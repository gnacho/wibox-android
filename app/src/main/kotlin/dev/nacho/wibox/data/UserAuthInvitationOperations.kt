package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.DevicePermissionCodec
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.UserAuthCloud
import dev.nacho.wibox.core.cloud.UserAuthException
import dev.nacho.wibox.core.cloud.UserAuthTimeoutException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CloudInvitationOperations {
    suspend fun list(deviceId: String): List<InvitationRecord>
    suspend fun create(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord
    suspend fun update(id: String, displayName: String, permissions: Set<DevicePermission>)
    suspend fun delete(id: String)
}

class UserAuthInvitationOperations(
    cloudFactory: () -> UserAuthCloud,
    private val credentials: () -> Pair<String, String>,
) : CloudInvitationOperations {
    private val cloud by lazy(cloudFactory)
    private val initialization = Mutex()
    private var loggedIn = false
    private var ownerId: String? = null
    private var invitePage: String? = null

    override suspend fun list(deviceId: String): List<InvitationRecord> = cloudCall("get-share-group-list") {
        ensureReady()
        cloud.listShareGroups(deviceId).map { group ->
            invitation(
                id = group.id,
                deviceId = deviceId,
                displayName = group.name,
                code = group.shareCode,
                expiresAtMillis = expirationMillis(group.expireTime, null),
                permissions = DevicePermissionCodec.decode(group.powers),
            )
        }
    }

    override suspend fun create(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord = cloudCall("generate-nologin-share-invitation") {
        ensureReady()
        val created = cloud.createShareGroup(deviceId, displayName, permissions)
        val page = created.invitePage.ifBlank { invitePage.orEmpty() }
        invitation(
            id = created.group.id,
            deviceId = deviceId,
            displayName = created.group.name.ifBlank { displayName },
            code = created.group.shareCode,
            expiresAtMillis = expirationMillis(created.group.expireTime, created.expireMinutes),
            permissions = permissions,
            invitePage = page.ifBlank { null },
        )
    }

    override suspend fun update(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) = cloudCall("update-share-group-config") {
        ensureReady()
        cloud.updateShareGroup(id, displayName, permissions)
    }

    override suspend fun delete(id: String) = cloudCall("delete-share-group") {
        ensureReady()
        cloud.deleteShareGroup(id)
    }

    private suspend fun ensureReady() = initialization.withLock {
        if (!loggedIn) {
            val (account, password) = credentials()
            ownerId = cloud.login(account, password).accountId
            loggedIn = true
        }
        if (invitePage.isNullOrBlank()) invitePage = cloud.getShareLink()
    }

    private fun invitation(
        id: String,
        deviceId: String,
        displayName: String,
        code: String,
        expiresAtMillis: Long,
        permissions: Set<DevicePermission>,
        invitePage: String? = this.invitePage,
    ) = InvitationRecord(
        id = id,
        deviceId = deviceId,
        displayName = displayName,
        code = code,
        createdAtMillis = System.currentTimeMillis(),
        expiresAtMillis = expiresAtMillis,
        permissions = permissions,
        invitePage = invitePage,
        ownerId = ownerId,
    )

    private suspend fun <T> cloudCall(operation: String, block: suspend () -> T): T = try {
        block()
    } catch (error: ProviderError) {
        throw error
    } catch (error: UserAuthTimeoutException) {
        throw ProviderError.Timeout(operation)
    } catch (error: UserAuthException) {
        if (error.result == 100101129) throw ProviderError.Authorization(operation)
        throw ProviderError.Unknown(operation, providerCode = error.result)
    } catch (error: Exception) {
        throw ProviderError.Network(operation)
    }
}

internal fun expirationMillis(raw: String, expireMinutes: Int?, nowMillis: Long = System.currentTimeMillis()): Long {
    raw.toLongOrNull()?.let { value ->
        if (value > 0) return if (value < 10_000_000_000L) value * 1000 else value
    }
    return expireMinutes?.let { nowMillis + it * 60_000L } ?: 0L
}
