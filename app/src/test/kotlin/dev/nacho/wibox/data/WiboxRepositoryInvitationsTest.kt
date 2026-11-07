package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.DevicePermission
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.InMemoryDemoStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WiboxRepositoryInvitationsTest {
    @Test
    fun cloudInvitationsRefreshCreateUpdateAndDelete() = runTest {
        val store = InMemoryDemoStore()
        val provider = DemoWiboxProvider(persist = store::save)
        val cloud = FakeCloudInvitationOperations()
        val repository = WiboxRepository(
            provider = provider,
            operations = provider,
            actionCoordinator = ActionCoordinator(provider, InMemoryActionJournalStore()),
            restore = { provider.initializeDemo(store.load() ?: DemoFixtures.initial()) },
            cloudInvitations = cloud,
        )
        try {
            repository.prepare()
            repository.refreshInvitations("device-1")
            assertEquals(listOf("g1"), repository.snapshot.invitations.map { it.id })

            val created = repository.createInvitation(
                "device-1",
                "Ana",
                setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK),
            )
            assertEquals("g-new", created.id)
            assertEquals("Ana", cloud.lastName)
            assertEquals(setOf(DevicePermission.PREVIEW, DevicePermission.UNLOCK), cloud.lastPermissions)

            repository.updateInvitation("g-new", "Ana editada", setOf(DevicePermission.PREVIEW))
            assertEquals("Ana editada", repository.snapshot.invitations.single { it.id == "g-new" }.displayName)

            repository.deleteInvitation("g-new")
            assertTrue(repository.snapshot.invitations.none { it.id == "g-new" })
            assertEquals("g-new", cloud.deletedId)
        } finally {
            repository.close()
        }
    }

    @Test
    fun installWithoutASessionKeepsTheLocalInvitations() = runTest {
        val store = InMemoryDemoStore()
        val provider = DemoWiboxProvider(persist = store::save)
        val cloud = FakeCloudInvitationOperations(available = false)
        val repository = WiboxRepository(
            provider = provider,
            operations = provider,
            actionCoordinator = ActionCoordinator(provider, InMemoryActionJournalStore()),
            restore = { provider.initializeDemo(store.load() ?: DemoFixtures.initial()) },
            cloudInvitations = cloud,
        )
        try {
            repository.prepare()
            repository.refreshInvitations("WIBOX7410001")
            assertTrue("sin sesion no se llama al canal cloud", cloud.calls == 0)

            val created = repository.createInvitation("WIBOX7410001", "Ana", setOf(DevicePermission.PREVIEW))
            assertTrue(created.id.startsWith("invitation-"))
            assertEquals("Ana", created.displayName)
            assertEquals(0, cloud.calls)
        } finally {
            repository.close()
        }
    }

    private class FakeCloudInvitationOperations(override val available: Boolean = true) : CloudInvitationOperations {
        var calls = 0
        var lastName = ""
        var lastPermissions = emptySet<DevicePermission>()
        var deletedId = ""

        override suspend fun list(deviceId: String): List<InvitationRecord> {
            calls++
            return listOf(record("g1", deviceId, "Remoto"))
        }

        override suspend fun create(
            deviceId: String,
            displayName: String,
            permissions: Set<DevicePermission>,
        ): InvitationRecord {
            lastName = displayName
            lastPermissions = permissions
            return record("g-new", deviceId, displayName).copy(permissions = permissions)
        }

        override suspend fun update(id: String, displayName: String, permissions: Set<DevicePermission>) {
            lastName = displayName
            lastPermissions = permissions
        }

        override suspend fun delete(id: String) {
            deletedId = id
        }

        private fun record(id: String, deviceId: String, name: String) = InvitationRecord(
            id = id,
            deviceId = deviceId,
            displayName = name,
            code = "CODE-$id",
            createdAtMillis = 1,
            expiresAtMillis = 2,
        )
    }
}
