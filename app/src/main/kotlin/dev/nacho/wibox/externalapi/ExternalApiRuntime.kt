package dev.nacho.wibox.externalapi

import android.content.Context
import android.os.Binder
import dev.nacho.wibox.WiboxApplication
import dev.nacho.wibox.core.cloud.ActionOutcome as CoreActionOutcome
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.WiboxProvider
import dev.nacho.wibox.data.WiboxRepository
import dev.nacho.wibox.externalapi.server.AuthorizationEngine
import dev.nacho.wibox.externalapi.server.AuthorizationStore
import dev.nacho.wibox.externalapi.server.CallerIdentity
import dev.nacho.wibox.externalapi.server.CallerIdentityResolver
import dev.nacho.wibox.externalapi.server.SigningCertificateSource
import dev.nacho.wibox.externalapi.server.TokenBucket
import dev.nacho.wibox.externalapi.server.UidPackageSource
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Shared state of the external API: the persistent allowlist, the
 * authorization engine, the per-uid rate limits and the bounded journal of
 * one-shot operations.
 *
 * Identity must always be resolved inside the Binder transaction
 * ([resolveIdentity] calls [Binder.getCallingUid]); background coroutines
 * receive the identity captured by the caller, never re-derive it.
 */
internal class ExternalApiRuntime(context: Context) {
    private val appContext = context.applicationContext

    val store: AuthorizationStore = SharedPreferencesAuthorizationStore(appContext)
    val engine = AuthorizationEngine(store)
    val readLimiter = TokenBucket(capacity = READ_BURST_PER_UID, refillPerMinute = READS_PER_MINUTE_PER_UID)
    val actionLimiter = TokenBucket(capacity = ACTION_BURST_PER_UID, refillPerMinute = ACTIONS_PER_MINUTE_PER_UID)
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val uidPackages: UidPackageSource
    private val signingCertificates: SigningCertificateSource

    init {
        val (packages, certificates) = AndroidIdentitySources.create(appContext)
        uidPackages = packages
        signingCertificates = certificates
    }

    @Volatile
    var preparation: PreparationState = PreparationState.IDLE
        private set

    fun repository(): WiboxRepository = (appContext as WiboxApplication).container.repository

    fun provider(): WiboxProvider = repository().provider

    fun resolveIdentity(claimedPackageName: String?): CallerIdentity =
        CallerIdentityResolver.resolve(
            uid = Binder.getCallingUid(),
            claimedPackageName = claimedPackageName,
            uidPackages = uidPackages,
            signingCertificates = signingCertificates,
        )

    /**
     * Runs provider.prepare() at most once from the IO scope; Binder threads
     * only read the cached [preparation] value.
     */
    suspend fun ensurePrepared(): PreparationState {
        if (preparation == PreparationState.READY) return preparation
        val state = runCatching { repository().prepare() }
            .getOrElse { PreparationState.FAILED }
        preparation = state
        return state
    }

    fun identityKey(identity: CallerIdentity.Verified): String = "${identity.uid}/${identity.packageName}"

    // One-shot operation budgets: 20 per app, 100 global.

    private val pendingPerIdentity = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingGlobal = AtomicInteger(0)

    fun acquireOneShotSlot(identity: CallerIdentity.Verified): Boolean {
        val counter = pendingPerIdentity.computeIfAbsent(identityKey(identity)) { AtomicInteger(0) }
        var accepted = false
        while (true) {
            val current = counter.get()
            if (current >= PENDING_ONE_SHOT_PER_APP) break
            if (counter.compareAndSet(current, current + 1)) {
                accepted = true
                break
            }
        }
        if (!accepted) return false
        while (true) {
            val current = pendingGlobal.get()
            if (current >= PENDING_ONE_SHOT_GLOBAL) {
                counter.decrementAndGet()
                return false
            }
            if (pendingGlobal.compareAndSet(current, current + 1)) return true
        }
    }

    fun releaseOneShotSlot(identity: CallerIdentity.Verified) {
        pendingPerIdentity[identityKey(identity)]?.decrementAndGet()
        pendingGlobal.decrementAndGet()
    }

    // Journal of dispatched actions for deduplication, cancellation and outcome lookups.

    enum class JournalState { QUEUED, DISPATCHED, DONE, CANCELLED }

    class JournalEntry(
        val uid: Int,
        val packageName: String,
        val requestId: String,
        val payloadDigest: String,
        val wireActionType: Int,
        val deviceId: String,
        val completion: CompletableDeferred<CoreActionOutcome>,
        @Volatile var state: JournalState,
        @Volatile var outcome: CoreActionOutcome?,
    )

    val journal = ConcurrentHashMap<String, JournalEntry>()

    /** Stable digest of the action payload so a reused requestId with a different payload conflicts. */
    fun actionPayloadDigest(actionType: Int, deviceId: String, parameters: Map<String, String>): String {
        val canonical = parameters.toSortedMap().entries.joinToString(separator = "\u0000") { (key, value) ->
            "$key=$value"
        }
        val source = "$actionType\u0000$deviceId\u0000$canonical"
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
    }

    /** Bounds the journal by dropping finished entries first, then the oldest ones. */
    fun compactJournal() {
        if (journal.size <= JOURNAL_MAX_ENTRIES) return
        val entries = journal.entries.sortedBy { it.key }
        entries.forEach { entry ->
            if (journal.size <= JOURNAL_MAX_ENTRIES) return
            if (entry.value.state == JournalState.DONE || entry.value.state == JournalState.CANCELLED) {
                journal.remove(entry.key)
            }
        }
        entries.take(maxOf(0, journal.size - JOURNAL_MAX_ENTRIES / 2)).forEach { journal.remove(it.key) }
    }

    private companion object {
        const val READS_PER_MINUTE_PER_UID = 30
        const val READ_BURST_PER_UID = 10
        const val ACTIONS_PER_MINUTE_PER_UID = 6
        const val ACTION_BURST_PER_UID = 2
        const val PENDING_ONE_SHOT_PER_APP = 20
        const val PENDING_ONE_SHOT_GLOBAL = 100
        const val JOURNAL_MAX_ENTRIES = 256
    }
}
