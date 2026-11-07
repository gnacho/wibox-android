package dev.nacho.wibox.core.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Subset of the userauth duplex channel needed to manage the people a device is
 * shared with. [UserAuthCloud] is the production implementation; the tests inject a
 * double, so the mapping is proven without the HTTP wire.
 */
interface UserAuthGateway {
    suspend fun login(account: String, password: String): UserAuthLogin
    suspend fun getShareLink(): String
    suspend fun listShareGroups(deviceId: String): List<ShareGroup>
    suspend fun createShareGroup(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): ShareGroupCreation
    suspend fun updateShareGroup(id: String, displayName: String, permissions: Set<DevicePermission>)
    suspend fun deleteShareGroup(id: String)
}

/**
 * [CloudUserAdministration] over the userauth share groups, the only user model the
 * provider has: `get-share-group-list` to read them, `generate-nologin-share-invitation`
 * to create one, `update-share-group-config` to edit it and `delete-share-group` to
 * revoke it.
 *
 * Every command needs an authenticated session, so the first call logs in with the
 * credentials supplied by [credentials]. The owner id of that account and the invite
 * page of the gateway complete the invitation record.
 */
class UserAuthUserAdministration(
    private val gateway: UserAuthGateway,
    private val credentials: () -> Pair<String, String>?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CloudUserAdministration {

    private val initialization = Mutex()

    @Volatile
    private var loggedIn = false

    @Volatile
    private var ownerId: String? = null

    @Volatile
    private var invitePage: String? = null

    override suspend fun listUsers(deviceId: String): List<CloudUserInvitation> =
        guarded(OPERATION_LIST) {
            if (deviceId.isBlank()) throw CloudError.InvalidInput(OPERATION_LIST)
            ensureReady()
            gateway.listShareGroups(deviceId).mapNotNull { group ->
                if (group.id.isBlank()) {
                    null
                } else {
                    record(
                        id = group.id,
                        deviceId = deviceId,
                        displayName = group.name,
                        shareCode = group.shareCode,
                        expireTime = group.expireTime,
                        expireMinutes = null,
                        permissions = DevicePermissionCodec.decode(group.powers),
                    )
                }
            }
        }

    override suspend fun createInvitation(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): CloudUserInvitation = guarded(OPERATION_CREATE) {
        if (deviceId.isBlank()) throw CloudError.InvalidInput(OPERATION_CREATE)
        ensureReady()
        val created = gateway.createShareGroup(deviceId, displayName, permissions)
        val id = created.group.id
        val code = created.group.shareCode
        // A success envelope with no group and no code is not a usable invitation.
        if (id.isBlank() && code.isBlank()) throw CloudError.Parse(OPERATION_CREATE)
        record(
            id = id,
            deviceId = deviceId,
            displayName = created.group.name.ifBlank { displayName },
            shareCode = code,
            expireTime = created.group.expireTime,
            expireMinutes = created.expireMinutes,
            permissions = permissions,
            invitePage = created.invitePage.ifBlank { invitePage },
        )
    }

    override suspend fun updateInvitation(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) = guarded(OPERATION_UPDATE) {
        if (id.isBlank()) throw CloudError.InvalidInput(OPERATION_UPDATE)
        ensureReady()
        gateway.updateShareGroup(id, displayName, permissions)
    }

    override suspend fun revokeInvitation(id: String) = guarded(OPERATION_REVOKE) {
        if (id.isBlank()) throw CloudError.InvalidInput(OPERATION_REVOKE)
        ensureReady()
        gateway.deleteShareGroup(id)
    }

    private suspend fun ensureReady() = initialization.withLock {
        if (!loggedIn) {
            val account = credentials()?.takeIf { it.first.isNotBlank() && it.second.isNotBlank() }
                ?: throw CloudError.Authentication(OPERATION_LOGIN)
            ownerId = try {
                gateway.login(account.first, account.second).accountId
            } catch (error: UserAuthException) {
                throw CloudError.Authentication(OPERATION_LOGIN)
            }
            loggedIn = true
        }
        if (invitePage.isNullOrBlank()) {
            invitePage = try {
                gateway.getShareLink()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The invite page is optional decoration: without it the code still works.
                null
            }
        }
    }

    private fun record(
        id: String,
        deviceId: String,
        displayName: String,
        shareCode: String,
        expireTime: String,
        expireMinutes: Int?,
        permissions: Set<DevicePermission>,
        invitePage: String? = this.invitePage,
    ): CloudUserInvitation = CloudUserInvitation(
        id = id,
        deviceId = deviceId,
        displayName = displayName,
        shareCode = shareCode,
        expiresAtMillis = invitationExpiryMillis(expireTime, expireMinutes, nowMillis()),
        permissions = permissions,
        invitePage = invitePage,
        ownerId = ownerId,
    )

    private suspend fun <T> guarded(operation: String, block: suspend () -> T): T = try {
        block()
    } catch (error: CloudError) {
        throw error
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: UserAuthTimeoutException) {
        throw CloudError.Timeout(operation)
    } catch (error: UserAuthException) {
        throw when (error.result) {
            // EmErrNoPowerGetShareGroup / EmErrNoPowerUpdateShareDeviceMemoAndPowers:
            // only the owner account manages the people a device is shared with.
            NO_POWER_LIST, NO_POWER_UPDATE -> CloudError.Authorization(operation)
            else -> CloudError.Provider(operation, error.result)
        }
    } catch (error: Exception) {
        throw CloudError.Network(operation)
    }

    companion object {
        private const val OPERATION_LIST = "listUsers"
        private const val OPERATION_CREATE = "createUserInvitation"
        private const val OPERATION_UPDATE = "updateUserInvitation"
        private const val OPERATION_REVOKE = "revokeUserInvitation"
        private const val OPERATION_LOGIN = "login"

        const val NO_POWER_LIST = 100101129
        const val NO_POWER_UPDATE = 100101126
    }
}
