package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.CloudError
import dev.nacho.wibox.core.cloud.CloudUserAdministration
import dev.nacho.wibox.core.cloud.CloudUserInvitation
import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.core.cloud.ProviderError
import kotlinx.coroutines.CancellationException

/**
 * People a device is shared with, as the app needs them.
 *
 * [available] is false on an install without an account: the repository then keeps the
 * local catalogue instead of pretending it has a cloud session. The rest of the
 * operations are the real userauth share-group channel, which only the owner account
 * can use.
 */
interface CloudInvitationOperations {
    val available: Boolean
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
    private val administration: CloudUserAdministration,
    private val hasSession: () -> Boolean,
) : CloudInvitationOperations {

    override val available: Boolean get() = hasSession()

    override suspend fun list(deviceId: String): List<InvitationRecord> =
        cloudCall("listUsers") { administration.listUsers(deviceId) }.map(::toRecord)

    override suspend fun create(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ): InvitationRecord = toRecord(
        cloudCall("createUserInvitation") {
            administration.createInvitation(deviceId, displayName, permissions)
        },
    )

    override suspend fun update(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) = cloudCall("updateUserInvitation") {
        administration.updateInvitation(id, displayName, permissions)
    }

    override suspend fun delete(id: String) = cloudCall("revokeUserInvitation") {
        administration.revokeInvitation(id)
    }

    private fun toRecord(invitation: CloudUserInvitation) = InvitationRecord(
        id = invitation.id,
        deviceId = invitation.deviceId,
        displayName = invitation.displayName,
        code = invitation.shareCode,
        createdAtMillis = System.currentTimeMillis(),
        expiresAtMillis = invitation.expiresAtMillis,
        permissions = invitation.permissions,
        invitePage = invitation.invitePage,
        ownerId = invitation.ownerId,
    )

    private suspend fun <T> cloudCall(operation: String, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ProviderError) {
        throw error
    } catch (error: CloudError) {
        throw error.toProviderError(operation)
    } catch (error: Exception) {
        throw ProviderError.Network(operation)
    }
}

private fun CloudError.toProviderError(operation: String): ProviderError = when (this) {
    is CloudError.Authentication, is CloudError.MissingToken -> ProviderError.Authentication(operation)
    is CloudError.Authorization -> ProviderError.Authorization(operation)
    is CloudError.Http -> ProviderError.Http(operation, statusCode = statusCode)
    is CloudError.Provider -> ProviderError.Unknown(operation, providerCode = providerCode)
    is CloudError.Network -> ProviderError.Network(operation)
    is CloudError.Timeout -> ProviderError.Timeout(operation)
    is CloudError.Parse -> ProviderError.Parse(operation)
    is CloudError.Persistence -> ProviderError.Persistence(operation)
    is CloudError.InvalidInput -> ProviderError.InvalidInput(operation)
    is CloudError.Unsupported -> ProviderError.Unsupported(operation, reason = reason)
    is CloudError.Closed -> ProviderError.Closed(operation)
}
