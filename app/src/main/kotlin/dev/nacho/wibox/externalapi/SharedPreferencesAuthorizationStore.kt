package dev.nacho.wibox.externalapi

import android.content.Context
import dev.nacho.wibox.externalapi.server.AuthorizationStore
import dev.nacho.wibox.externalapi.server.GrantRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persistent revocable allowlist backed by SharedPreferences.
 *
 * Stored fields never include secrets: uid, package name, signer-set digest,
 * scope strings, device ids, generation, display name and state only. Writes
 * are atomic (commit) and the store carries a schema version so older files
 * are discarded instead of misread.
 */
internal class SharedPreferencesAuthorizationStore(context: Context) : AuthorizationStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        STORE_NAME,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (prefs.getInt(SCHEMA_VERSION_KEY, 0) != SCHEMA_VERSION) {
            prefs.edit().clear().putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION).commit()
        }
    }

    @Synchronized
    override fun loadAll(): List<GrantRecord> {
        val raw = prefs.getString(GRANTS_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredGrant>>(raw).map { it.toRecord() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    override fun upsert(record: GrantRecord) {
        val records = loadAll().filterNot { it.identityTuple() == record.identityTuple() } + record
        prefs.edit()
            .putString(GRANTS_KEY, json.encodeToString(ListSerializer(StoredGrant.serializer()), records.map { it.toStored() }))
            .commit()
    }

    @Synchronized
    override fun delete(uid: Int, packageName: String, signerSetSha256: String) {
        val records = loadAll().filterNot {
            it.uid == uid && it.packageName == packageName && it.signerSetSha256 == signerSetSha256
        }
        prefs.edit()
            .putString(GRANTS_KEY, json.encodeToString(ListSerializer(StoredGrant.serializer()), records.map { it.toStored() }))
            .commit()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().remove(GRANTS_KEY).commit()
    }

    private fun GrantRecord.identityTuple(): Triple<Int, String, String> =
        Triple(uid, packageName, signerSetSha256)

    @Serializable
    private data class StoredGrant(
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

    private fun GrantRecord.toStored() = StoredGrant(
        uid = uid,
        packageName = packageName,
        signerSetSha256 = signerSetSha256,
        state = state,
        scopes = scopes,
        deviceIds = deviceIds,
        grantGeneration = grantGeneration,
        displayName = displayName,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun StoredGrant.toRecord() = GrantRecord(
        uid = uid,
        packageName = packageName,
        signerSetSha256 = signerSetSha256,
        state = state,
        scopes = scopes,
        deviceIds = deviceIds,
        grantGeneration = grantGeneration,
        displayName = displayName,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private companion object {
        const val STORE_NAME = "wibox_external_api_allowlist"
        const val SCHEMA_VERSION_KEY = "schema_version"
        const val SCHEMA_VERSION = 1
        const val GRANTS_KEY = "grants_v1"
    }
}
