package dev.nacho.wibox.core.cloud.internal

import dev.nacho.wibox.core.cloud.CloudClock
import dev.nacho.wibox.core.cloud.CloudCredentials
import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.CloudSession
import dev.nacho.wibox.core.cloud.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SessionManager(
    private val store: TokenStore,
    private val clock: CloudClock,
    private val scope: CoroutineScope,
    private val passwordRequest: suspend (CloudCredentials, String) -> CloudSession,
    private val refreshRequest: suspend (CloudSession) -> CloudSession,
) {
    private enum class OperationKind { PASSWORD, REFRESH }
    private data class InFlight(val kind: OperationKind, val deferred: Deferred<CloudSession>)

    private val mutex = Mutex()
    private var session: CloudSession? = null
    private var restored = false
    private var inFlight: InFlight? = null

    suspend fun restore(): CloudSession? = mutex.withLock {
        restoreLocked()
        session
    }

    suspend fun authenticate(credentials: CloudCredentials): CloudSession {
        while (true) {
            val operation = mutex.withLock {
                restoreLocked()
                val active = inFlight
                if (active != null) {
                    active.deferred to false
                } else {
                    val tokenHeader = session?.accessToken.orEmpty()
                    startLocked(OperationKind.PASSWORD) {
                        complete(passwordRequest(credentials, tokenHeader))
                    } to true
                }
            }
            val result = operation.first.await()
            if (operation.second) return result
        }
    }

    suspend fun refresh(): CloudSession {
        while (true) {
            val operation = mutex.withLock {
                restoreLocked()
                val active = inFlight
                if (active != null) {
                    Triple(active.deferred, active.kind, false)
                } else {
                    val source = session?.takeIf { usable(it.refreshTokenExpiresAtMillis) }
                        ?: throw CloudError.MissingToken()
                    Triple(
                        startLocked(OperationKind.REFRESH) { complete(refreshRequest(source)) },
                        OperationKind.REFRESH,
                        true,
                    )
                }
            }
            val result = operation.first.await()
            if (operation.third || operation.second == OperationKind.REFRESH) return result
        }
    }

    suspend fun validSession(): CloudSession {
        while (true) {
            val waiting = mutex.withLock {
                restoreLocked()
                val active = inFlight
                if (active != null) {
                    active.deferred
                } else {
                    val current = session
                    if (current != null && usable(current.accessTokenExpiresAtMillis)) {
                        return current
                    }
                    val source = current?.takeIf { usable(it.refreshTokenExpiresAtMillis) }
                        ?: throw CloudError.MissingToken()
                    startLocked(OperationKind.REFRESH) { complete(refreshRequest(source)) }
                }
            }
            waiting.await()
        }
    }

    private fun usable(expiresAtMillis: Long): Boolean =
        expiresAtMillis - clock.nowMillis() > REFRESH_MARGIN_MILLIS

    private suspend fun restoreLocked() {
        if (restored) return
        val loaded = try {
            store.load()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw CloudError.Persistence("load")
        }
        session = loaded?.takeIf {
            it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() &&
                it.accessTokenExpiresAtMillis > 0 && it.refreshTokenExpiresAtMillis > 0
        }
        restored = true
    }

    private suspend fun complete(newSession: CloudSession): CloudSession {
        mutex.withLock {
            try {
                store.save(newSession)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                throw CloudError.Persistence("save")
            }
            session = newSession
        }
        return newSession
    }

    private fun startLocked(
        kind: OperationKind,
        block: suspend () -> CloudSession,
    ): Deferred<CloudSession> {
        lateinit var deferred: Deferred<CloudSession>
        deferred = scope.async(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                mutex.withLock {
                    if (inFlight?.deferred === deferred) inFlight = null
                }
            }
        }
        inFlight = InFlight(kind, deferred)
        deferred.start()
        return deferred
    }

    companion object {
        const val REFRESH_MARGIN_MILLIS = 5 * 60 * 1_000L
    }
}
