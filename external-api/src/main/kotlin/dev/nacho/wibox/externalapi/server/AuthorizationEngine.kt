package dev.nacho.wibox.externalapi.server

import dev.nacho.wibox.externalapi.aidl.AuthorizationSnapshot
import dev.nacho.wibox.externalapi.aidl.WireActionType
import dev.nacho.wibox.externalapi.aidl.WireApiErrorCode
import dev.nacho.wibox.externalapi.aidl.WireAuthorizationState

/** Scope strings of the v1 contract. Authorization of a scope never implies runtime availability. */
object ApiScopes {
    const val PROVIDER_READ = "provider.read"
    const val CAPABILITIES_READ = "capabilities.read"
    const val DEVICES_READ = "devices.read"
    const val SHADOW_READ = "shadow.read"
    const val EVENTS_READ = "events.read"
    const val ACTIONS_UNLOCK = "actions.unlock"
    const val ACTIONS_F1 = "actions.f1"
    const val ACTIONS_CALL_GUARD = "actions.call_guard"
    const val ACTIONS_CALL_CONTROL = "actions.call_control"
    const val APP_UPGRADE_CHECK = "app.upgrade_check"

    val ALL = listOf(
        PROVIDER_READ,
        CAPABILITIES_READ,
        DEVICES_READ,
        SHADOW_READ,
        EVENTS_READ,
        ACTIONS_UNLOCK,
        ACTIONS_F1,
        ACTIONS_CALL_GUARD,
        ACTIONS_CALL_CONTROL,
        APP_UPGRADE_CHECK,
    )

    /** Maps a WireActionType to the scope that authorizes it, or null when unknown. */
    fun forAction(wireActionType: Int): String? = when (wireActionType) {
        WireActionType.UNLOCK -> ACTIONS_UNLOCK
        WireActionType.F1 -> ACTIONS_F1
        WireActionType.CALL_GUARD -> ACTIONS_CALL_GUARD
        WireActionType.CALL_ACCEPT,
        WireActionType.CALL_REJECT,
        WireActionType.START_CALL,
        WireActionType.STOP_CALL,
        -> ACTIONS_CALL_CONTROL
        else -> null
    }
}

data class AuthorizationDecision(
    val authorized: Boolean,
    val grant: GrantRecord?,
    val errorCode: Int?,
    val safeMessage: String?,
) {
    companion object {
        fun allowed(grant: GrantRecord): AuthorizationDecision =
            AuthorizationDecision(authorized = true, grant = grant, errorCode = null, safeMessage = null)

        fun denied(errorCode: Int, safeMessage: String): AuthorizationDecision =
            AuthorizationDecision(authorized = false, grant = null, errorCode = errorCode, safeMessage = safeMessage)
    }
}

data class AuthorizationRequestOutcome(
    val snapshot: AuthorizationSnapshot?,
    val errorCode: Int?,
    val safeMessage: String?,
    val retryAfterMs: Long? = null,
)

/**
 * Pure authorization logic over the allowlist store.
 *
 * Deny by default: a caller without an exact (uid, packageName, signerSetSha256)
 * entry in AUTHORIZED state is never authorized. The engine is called on every
 * Binder transaction so a revocation applies to subsequent calls immediately.
 */
class AuthorizationEngine(
    private val store: AuthorizationStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /** Pending authorization requests accepted per package before RATE_LIMITED. */
    val pendingPerPackageLimit = 3

    /** Pending authorization requests accepted globally before RATE_LIMITED. */
    val pendingGlobalLimit = 50

    fun authorize(identity: CallerIdentity): AuthorizationDecision = when (identity) {
        is CallerIdentity.NonApplicationUid ->
            AuthorizationDecision.denied(WireApiErrorCode.NOT_AUTHORIZED, "Wibox does not authorize this caller uid")
        is CallerIdentity.SharedUid ->
            AuthorizationDecision.denied(WireApiErrorCode.SHARED_UID_UNSUPPORTED, "Shared uid callers are not supported")
        is CallerIdentity.PackageMismatch ->
            AuthorizationDecision.denied(WireApiErrorCode.PACKAGE_UID_MISMATCH, "Package does not match the calling uid")
        is CallerIdentity.NoSigningInfo ->
            AuthorizationDecision.denied(WireApiErrorCode.NOT_AUTHORIZED, "Signing certificates could not be read")
        is CallerIdentity.Verified -> authorizeVerified(identity)
    }

    /**
     * Full access check for one call: identity, grant, scope and device.
     * A null [deviceId] means the operation is not bound to one device.
     */
    fun checkAccess(identity: CallerIdentity, scope: String?, deviceId: String?): AuthorizationDecision {
        val decision = authorize(identity)
        if (!decision.authorized) return decision
        val grant = requireNotNull(decision.grant)
        if (scope != null && grant.scopes.none { it == scope }) {
            return AuthorizationDecision.denied(WireApiErrorCode.NOT_AUTHORIZED, "Scope was not granted")
        }
        if (deviceId != null && grant.deviceIds.none { it == deviceId }) {
            return AuthorizationDecision.denied(WireApiErrorCode.NOT_AUTHORIZED, "Device was not granted")
        }
        return decision
    }

    /** Current authorization snapshot for a verified identity, without granting anything. */
    fun snapshot(identity: CallerIdentity.Verified): AuthorizationSnapshot {
        val grants = grantsFor(identity.packageName)
        val exact = exactTuple(grants, identity)
        val active = latestDecision(exact)
        val state = when {
            active != null -> active.state
            grants.any { it.uid == identity.uid } -> WireAuthorizationState.SIGNATURE_CHANGED
            grants.any { it.signerSetSha256 == identity.signerSetSha256 } -> WireAuthorizationState.UID_CHANGED
            else -> WireAuthorizationState.NOT_REQUESTED
        }
        val authorizedGrant = active?.takeIf { it.state == WireAuthorizationState.AUTHORIZED }
        return AuthorizationSnapshot(
            state = state,
            grantedScopes = authorizedGrant?.scopes ?: emptyList(),
            grantedDeviceIds = authorizedGrant?.deviceIds ?: emptyList(),
            grantGeneration = active?.grantGeneration ?: 0L,
        )
    }

    /**
     * Registers a pending authorization request. It never grants access and
     * never elevates scopes of an existing authorized grant. Unknown scopes
     * are rejected instead of silently stored.
     */
    fun requestAuthorization(
        identity: CallerIdentity.Verified,
        displayName: String,
        scopes: List<String>,
        deviceIds: List<String>,
    ): AuthorizationRequestOutcome {
        if (scopes.any { scope -> ApiScopes.ALL.none { it == scope } }) {
            return AuthorizationRequestOutcome(null, WireApiErrorCode.INVALID_INPUT, "Unknown scope requested")
        }
        val grants = grantsFor(identity.packageName)
        val exact = exactTuple(grants, identity)
        val existing = latestDecision(exact)
        if (existing != null && existing.state == WireAuthorizationState.AUTHORIZED) {
            return AuthorizationRequestOutcome(snapshot(identity), null, null)
        }
        val pendingGlobal = store.loadAll().count { it.state == WireAuthorizationState.PENDING }
        val pendingForPackage = grants.count { it.state == WireAuthorizationState.PENDING }
        if (pendingForPackage >= pendingPerPackageLimit) {
            return AuthorizationRequestOutcome(
                null,
                WireApiErrorCode.RATE_LIMITED,
                "Too many pending authorization requests for this app",
                retryAfterMs = 60_000L,
            )
        }
        if (pendingGlobal >= pendingGlobalLimit) {
            return AuthorizationRequestOutcome(
                null,
                WireApiErrorCode.RATE_LIMITED,
                "Too many pending authorization requests",
                retryAfterMs = 60_000L,
            )
        }
        val record = GrantRecord(
            uid = identity.uid,
            packageName = identity.packageName,
            signerSetSha256 = identity.signerSetSha256,
            state = WireAuthorizationState.PENDING,
            scopes = scopes,
            deviceIds = deviceIds,
            grantGeneration = existing?.grantGeneration ?: 0L,
            displayName = displayName,
            updatedAtEpochMs = nowMs(),
        )
        store.upsert(record)
        return AuthorizationRequestOutcome(snapshot(identity), null, null)
    }

    /** Wibox settings only. Promotes the pending request of the tuple to AUTHORIZED. */
    fun approve(uid: Int, packageName: String, signerSetSha256: String): Boolean {
        val current = store.loadAll().firstOrNull {
            it.uid == uid && it.packageName == packageName && it.signerSetSha256 == signerSetSha256
        } ?: return false
        store.upsert(
            current.copy(
                state = WireAuthorizationState.AUTHORIZED,
                grantGeneration = current.grantGeneration + 1,
                updatedAtEpochMs = nowMs(),
            ),
        )
        return true
    }

    /** Wibox settings only. Marks the tuple as explicitly denied by the user. */
    fun deny(uid: Int, packageName: String, signerSetSha256: String): Boolean {
        val current = store.loadAll().firstOrNull {
            it.uid == uid && it.packageName == packageName && it.signerSetSha256 == signerSetSha256
        } ?: return false
        if (current.state == WireAuthorizationState.DENIED) return false
        store.upsert(current.copy(state = WireAuthorizationState.DENIED, updatedAtEpochMs = nowMs()))
        return true
    }

    /**
     * Wibox settings only. Revocation increments the grant generation and
     * denies every subsequent call until the user approves again. Callback
     * removal is done by the service layer when it observes the new state.
     */
    fun revoke(uid: Int, packageName: String, signerSetSha256: String): Boolean {
        val current = store.loadAll().firstOrNull {
            it.uid == uid && it.packageName == packageName && it.signerSetSha256 == signerSetSha256
        } ?: return false
        if (current.state == WireAuthorizationState.REVOKED) return false
        store.upsert(
            current.copy(
                state = WireAuthorizationState.REVOKED,
                grantGeneration = current.grantGeneration + 1,
                updatedAtEpochMs = nowMs(),
            ),
        )
        return true
    }

    private fun authorizeVerified(identity: CallerIdentity.Verified): AuthorizationDecision {
        val grants = grantsFor(identity.packageName)
        val active = latestDecision(exactTuple(grants, identity))
        if (active != null) {
            return when (active.state) {
                WireAuthorizationState.AUTHORIZED -> AuthorizationDecision.allowed(active)
                WireAuthorizationState.PENDING ->
                    AuthorizationDecision.denied(WireApiErrorCode.AUTHORIZATION_PENDING, "Wibox authorization is pending user approval")
                WireAuthorizationState.DENIED ->
                    AuthorizationDecision.denied(WireApiErrorCode.AUTHORIZATION_DENIED, "Wibox access was denied by the user")
                WireAuthorizationState.REVOKED ->
                    AuthorizationDecision.denied(WireApiErrorCode.AUTHORIZATION_REVOKED, "Wibox access was revoked")
                else ->
                    AuthorizationDecision.denied(WireApiErrorCode.NOT_AUTHORIZED, "Wibox access has not been granted")
            }
        }
        if (grants.any { it.uid == identity.uid }) {
            return AuthorizationDecision.denied(WireApiErrorCode.SIGNATURE_CHANGED, "App signature changed and requires re-approval")
        }
        if (grants.any { it.signerSetSha256 == identity.signerSetSha256 }) {
            return AuthorizationDecision.denied(WireApiErrorCode.UID_CHANGED, "App uid changed and requires re-approval")
        }
        return AuthorizationDecision.denied(WireApiErrorCode.NOT_AUTHORIZED, "Wibox access has not been granted to this app")
    }

    private fun grantsFor(packageName: String): List<GrantRecord> =
        store.loadAll().filter { it.packageName == packageName }

    private fun exactTuple(grants: List<GrantRecord>, identity: CallerIdentity.Verified): List<GrantRecord> =
        grants.filter { it.uid == identity.uid && it.signerSetSha256 == identity.signerSetSha256 }

    /** Most recent user decision wins; ties break by the highest grant generation. */
    private fun latestDecision(records: List<GrantRecord>): GrantRecord? =
        records.maxWithOrNull(compareBy({ it.updatedAtEpochMs }, { it.grantGeneration }))
}
