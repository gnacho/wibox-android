package dev.nacho.wibox.core.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The user channel of the provider is the share group list of the userauth gateway:
 * `get-share-group-list`, `generate-nologin-share-invitation`,
 * `update-share-group-config` and `delete-share-group`. Every case runs against a
 * double of the gateway, so the mapping is proven without credentials or a device.
 */
class UserAuthUserAdministrationTest {

    @Test
    fun listUsersMapsTheShareGroupsOfTheAccount() = runTest {
        val gateway = FakeGateway(
            groups = listOf(
                ShareGroup(
                    id = "g1",
                    name = "Ana",
                    powers = "1,1001",
                    shareCode = "ABC12345",
                    expireTime = "1700000000",
                ),
                ShareGroup(id = "", name = "sin id", powers = "1", shareCode = "", expireTime = ""),
            ),
        )
        val administration = administration(gateway)

        val users = administration.listUsers("23123csfewyk")

        assertEquals(1, users.size)
        val user = users.single()
        assertEquals("g1", user.id)
        assertEquals("23123csfewyk", user.deviceId)
        assertEquals("Ana", user.displayName)
        assertEquals("ABC12345", user.shareCode)
        assertEquals(1_700_000_000_000L, user.expiresAtMillis)
        assertEquals(setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK), user.permissions)
        assertEquals("2041769", user.ownerId)
        assertEquals("https://share.example/invite", user.invitePage)
        assertEquals(listOf("23123csfewyk"), gateway.listedDevices)
        assertEquals(1, gateway.logins)
    }

    @Test
    fun createInvitationKeepsTheCodeThePageAndThePermissions() = runTest {
        val gateway = FakeGateway(
            creation = ShareGroupCreation(
                group = ShareGroup(
                    id = "g-new",
                    name = "Ana",
                    powers = "1,1001",
                    shareCode = "NEW12345",
                    expireTime = "",
                ),
                invitePage = "https://share.example/invite?InviteCode=NEW12345",
                expireMinutes = 5,
            ),
        )
        val administration = administration(gateway, now = 60_000L)

        val created = administration.createInvitation(
            deviceId = "23123csfewyk",
            displayName = "Ana",
            permissions = setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK),
        )

        assertEquals("g-new", created.id)
        assertEquals("NEW12345", created.shareCode)
        assertEquals("https://share.example/invite?InviteCode=NEW12345", created.invitePage)
        assertEquals(360_000L, created.expiresAtMillis)
        assertEquals(setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK), created.permissions)
        assertEquals(Triple("23123csfewyk", "Ana", "1,1001"), gateway.createdWith)
        assertEquals(1, gateway.logins)
        // Secrets stay out of the logs.
        assertTrue(created.toString().indexOf("NEW12345") < 0)
    }

    @Test
    fun aGuestAccountIsReportedAsNotAuthorized() = runTest {
        val listing = FakeGateway(commandFailure = UserAuthException("get-share-group-list", 100101129))
        assertFailsWith<CloudError.Authorization> { administration(listing).listUsers("23123csfewyk") }

        val updating = FakeGateway(commandFailure = UserAuthException("update-share-group-config", 100101126))
        assertFailsWith<CloudError.Authorization> {
            administration(updating).updateInvitation("g1", "Ana", setOf(DevicePermission.PREVIEW))
        }
    }

    @Test
    fun anEmptyCreateResponseIsAParseError() = runTest {
        val gateway = FakeGateway(
            creation = ShareGroupCreation(
                group = ShareGroup(id = "", name = "", powers = "", shareCode = "", expireTime = ""),
                invitePage = "",
                expireMinutes = null,
            ),
        )

        assertFailsWith<CloudError.Parse> {
            administration(gateway).createInvitation("23123csfewyk", "Ana", setOf(DevicePermission.PREVIEW))
        }
    }

    @Test
    fun withoutCredentialsNothingIsSent() = runTest {
        val gateway = FakeGateway()

        assertFailsWith<CloudError.Authentication> {
            UserAuthUserAdministration(gateway, credentials = { null }).listUsers("23123csfewyk")
        }
        assertEquals(0, gateway.logins)
        assertEquals(emptyList(), gateway.listedDevices)
    }

    @Test
    fun aRejectedLoginIsAnAuthenticationError() = runTest {
        val gateway = FakeGateway(loginFailure = UserAuthException("login", 100100003))

        assertFailsWith<CloudError.Authentication> {
            administration(gateway).listUsers("23123csfewyk")
        }
    }

    @Test
    fun aTimeoutWithoutDownResponseIsATimeout() = runTest {
        val gateway = FakeGateway(commandFailure = UserAuthTimeoutException("get-share-group-list"))

        assertFailsWith<CloudError.Timeout> { administration(gateway).listUsers("23123csfewyk") }
    }

    @Test
    fun revokingUsesTheGroupIdentifier() = runTest {
        val gateway = FakeGateway()

        administration(gateway).revokeInvitation("g1")

        assertEquals("g1", gateway.deletedId)
    }

    @Test
    fun theInvitePageFailureDoesNotBlockTheList() = runTest {
        val gateway = FakeGateway(
            groups = listOf(ShareGroup("g1", "Ana", "1", "ABC", "")),
            shareLinkFailure = UserAuthException("get-share-link", 100101129),
        )

        val users = administration(gateway).listUsers("23123csfewyk")

        assertEquals(1, users.size)
        assertNull(users.single().invitePage)
    }

    @Test
    fun invitationExpiryAcceptsEpochAndRelativeMinutes() {
        assertEquals(1_700_000_000_000L, invitationExpiryMillis("1700000000", null))
        assertEquals(1_700_000_000_123L, invitationExpiryMillis("1700000000123", null))
        assertEquals(360_000L, invitationExpiryMillis("", 5, nowMillis = 60_000L))
        assertEquals(0L, invitationExpiryMillis("", null))
    }

    private fun administration(gateway: UserAuthGateway, now: Long = 1L): UserAuthUserAdministration =
        UserAuthUserAdministration(
            gateway = gateway,
            credentials = { "OX00000000001f28dd003-2021-87dad215f30f1f7e" to "fixture-password" },
            nowMillis = { now },
        )

    private class FakeGateway(
        private val groups: List<ShareGroup> = emptyList(),
        private val creation: ShareGroupCreation? = null,
        private val commandFailure: Exception? = null,
        private val loginFailure: Exception? = null,
        private val shareLinkFailure: Exception? = null,
    ) : UserAuthGateway {
        var logins = 0
        var deletedId: String? = null
        var createdWith: Triple<String, String, String>? = null
        val listedDevices = mutableListOf<String>()

        override suspend fun login(account: String, password: String): UserAuthLogin {
            logins++
            loginFailure?.let { throw it }
            return UserAuthLogin(accountId = "2041769", session = "session-token")
        }

        override suspend fun getShareLink(): String {
            shareLinkFailure?.let { throw it }
            return "https://share.example/invite"
        }

        override suspend fun listShareGroups(deviceId: String): List<ShareGroup> {
            commandFailure?.let { throw it }
            listedDevices += deviceId
            return groups
        }

        override suspend fun createShareGroup(
            deviceId: String,
            displayName: String,
            permissions: Set<DevicePermission>,
        ): ShareGroupCreation {
            commandFailure?.let { throw it }
            createdWith = Triple(deviceId, displayName, DevicePermissionCodec.encode(permissions))
            return creation ?: error("createShareGroup without a fixture")
        }

        override suspend fun updateShareGroup(
            id: String,
            displayName: String,
            permissions: Set<DevicePermission>,
        ) {
            commandFailure?.let { throw it }
        }

        override suspend fun deleteShareGroup(id: String) {
            commandFailure?.let { throw it }
            deletedId = id
        }
    }
}
