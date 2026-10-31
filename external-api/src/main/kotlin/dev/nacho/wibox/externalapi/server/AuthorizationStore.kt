package dev.nacho.wibox.externalapi.server

import java.util.concurrent.ConcurrentHashMap

/**
 * Authorization state of one (uid, packageName, signerSetSha256) tuple.
 *
 * The record never contains secrets: no tokens, cookies, credentials,
 * client ids or media keys. Scopes and device ids are plain strings, and
 * [state] uses the WireAuthorizationState constants from the AIDL contract.
 *
 * While the state is PENDING the scope and device lists hold the requested
 * values; they only become granted once the state is AUTHORIZED, which is
 * decided exclusively by Wibox settings.
 */
data class GrantRecord(
    val uid: Int,
    val packageName: String,
    val signerSetSha256: String,
    val state: Int,
    val scopes: List<String>,
    val deviceIds: List<String>,
    val grantGeneration: Long,
    val displayName: String,
    val updatedAtEpochMs: Long,
)

/** Persistent, revocable allowlist storage keyed by (uid, packageName, signerSetSha256). */
interface AuthorizationStore {
    fun loadAll(): List<GrantRecord>

    /** Inserts or replaces the record with the same identity tuple. */
    fun upsert(record: GrantRecord)

    fun delete(uid: Int, packageName: String, signerSetSha256: String)

    fun clear()
}

/** Thread-safe in-memory implementation used by unit tests. */
class InMemoryAuthorizationStore : AuthorizationStore {
    private val records = ConcurrentHashMap<String, GrantRecord>()

    private fun key(uid: Int, packageName: String, signerSetSha256: String): String =
        "$uid|$packageName|$signerSetSha256"

    override fun loadAll(): List<GrantRecord> = records.values.toList()

    override fun upsert(record: GrantRecord) {
        records[key(record.uid, record.packageName, record.signerSetSha256)] = record
    }

    override fun delete(uid: Int, packageName: String, signerSetSha256: String) {
        records.remove(key(uid, packageName, signerSetSha256))
    }

    override fun clear() {
        records.clear()
    }
}
