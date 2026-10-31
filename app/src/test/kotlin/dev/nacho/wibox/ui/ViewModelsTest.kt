package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import dev.nacho.wibox.data.ActionCoordinator
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.InMemoryActionJournalStore
import dev.nacho.wibox.data.WiboxRepository
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.InMemoryDemoStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.repository(): WiboxRepository {
        val store = InMemoryDemoStore()
        val provider = DemoWiboxProvider(persist = store::save)
        return WiboxRepository(
            provider = provider,
            operations = provider,
            actionCoordinator = ActionCoordinator(provider, InMemoryActionJournalStore()),
            restore = { provider.initializeDemo(store.load() ?: DemoFixtures.initial()) },
        )
    }

    private suspend fun <T : Any> awaitFirst(timeoutMillis: Long = 5_000, condition: () -> T?): T =
        withTimeout(timeoutMillis) {
            var value = condition()
            while (value == null) {
                kotlinx.coroutines.delay(10)
                value = condition()
            }
            value
        }

    @Test
    fun devicesShowDemoContentAfterPrepare() = runTest {
        val repository = repository()
        try {
            val viewModel = DevicesViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            val content = awaitFirst {
                val state = viewModel.state.value.devices
                (state as? LoadState.Content)?.value
            }
            assertEquals(setOf("DEMO7410001", "DEMO9440002"), content.map { it.id }.toSet())
        } finally {
            repository.close()
        }
    }

    @Test
    fun devicesScenarioEmptyLoadsEmptyList() = runTest {
        val repository = repository()
        try {
            val viewModel = DevicesViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            repository.setScenario(AppScenario.EMPTY)
            val content = awaitFirst {
                val state = viewModel.state.value.devices
                (state as? LoadState.Content)?.value
            }
            assertTrue(content.isEmpty())
        } finally {
            repository.close()
        }
    }

    @Test
    fun eventsEditingSelectionAndDeleteSelected() = runTest {
        val repository = repository()
        try {
            val viewModel = EventsViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            awaitFirst {
                val state = viewModel.state.value.events
                (state as? LoadState.Content)?.value
            }
            viewModel.toggleEditing()
            assertTrue(viewModel.state.value.editing)
            viewModel.toggleSelection("event-001")
            assertEquals(setOf("event-001"), viewModel.state.value.selected)
            viewModel.selectAll()
            assertEquals(6, viewModel.state.value.selected.size)
            viewModel.deleteSelected()
            val remaining = awaitFirst {
                val state = viewModel.state.value.events
                if (viewModel.state.value.editing) null else (state as? LoadState.Content)?.value
            }
            assertEquals(0, remaining.size)
            assertFalse(viewModel.state.value.editing)
        } finally {
            repository.close()
        }
    }

    @Test
    fun eventsLoadMoreIncreasesVisibleCount() = runTest {
        val repository = repository()
        try {
            val viewModel = EventsViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            assertEquals(15, viewModel.state.value.visibleCount)
            viewModel.loadMore()
            assertEquals(30, viewModel.state.value.visibleCount)
        } finally {
            repository.close()
        }
    }

    @Test
    fun gallerySelectionAndDeleteSelected() = runTest {
        val repository = repository()
        try {
            val viewModel = GalleryViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            awaitFirst {
                val state = viewModel.state.value.media
                (state as? LoadState.Content)?.value
            }
            viewModel.toggleEditing()
            assertTrue(viewModel.state.value.editing)
            viewModel.toggleSelection("media-001")
            assertEquals(setOf("media-001"), viewModel.state.value.selected)
            viewModel.deleteSelected()
            val remaining = awaitFirst {
                val state = viewModel.state.value.media
                if (viewModel.state.value.editing) null else (state as? LoadState.Content)?.value
            }
            assertEquals(5, remaining.size)
            assertFalse(remaining.any { it.id == "media-001" })
        } finally {
            repository.close()
        }
    }

    @Test
    fun settingsPreferencesPersist() = runTest {
        val repository = repository()
        try {
            val viewModel = SettingsViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            viewModel.setQuickUnlock(true)
            assertTrue(awaitFirst { viewModel.state.value.preferences.quickUnlock.takeIf { it } })
            viewModel.setScenario(AppScenario.OFFLINE)
            assertEquals(AppScenario.OFFLINE, awaitFirst {
                viewModel.state.value.preferences.scenario.takeIf { it == AppScenario.OFFLINE }
            })
        } finally {
            repository.close()
        }
    }

    @Test
    fun settingsResetDemoRestoresDefaultsAndNotices() = runTest {
        val repository = repository()
        try {
            val viewModel = SettingsViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            repository.setScenario(AppScenario.ERROR)
            awaitFirst {
                viewModel.state.value.preferences.scenario.takeIf { it == AppScenario.ERROR }
            }
            viewModel.resetDemo()
            assertEquals(AppScenario.NORMAL, awaitFirst {
                viewModel.state.value.preferences.scenario.takeIf { it == AppScenario.NORMAL }
            })
            assertEquals(UserNotice.DEMO_RESET, viewModel.state.value.notice)
            viewModel.consumeNotice()
            assertNull(viewModel.state.value.notice)
        } finally {
            repository.close()
        }
    }

    @Test
    fun sharingCreatesConsumesAndDeletesInvitation() = runTest {
        val repository = repository()
        try {
            val viewModel = SharingViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            viewModel.createInvitation("DEMO7410001")
            val invitationId = awaitFirst { viewModel.state.value.createdInvitationId }
            assertNotNull(invitationId)
            val created = awaitFirst {
                val state = viewModel.state.value.invitations
                (state as? LoadState.Content)?.value?.firstOrNull { it.id == invitationId }
            }
            assertEquals("DEMO7410001", created.deviceId)
            viewModel.consumeCreatedInvitation()
            assertNull(viewModel.state.value.createdInvitationId)
            viewModel.deleteInvitation(invitationId)
            val remaining = awaitFirst {
                val state = viewModel.state.value.invitations
                (state as? LoadState.Content)?.value?.none { it.id == invitationId }?.takeIf { it }
            }
            assertTrue(remaining)
        } finally {
            repository.close()
        }
    }

    @Test
    fun deviceDetailLoadsAndRenames() = runTest {
        val repository = repository()
        try {
            val viewModel = DeviceDetailViewModel(repository, SavedStateHandle(), StandardTestDispatcher(testScheduler))
            repository.prepare()
            viewModel.load("DEMO7410001")
            val device = awaitFirst { viewModel.state.value.device }
            assertEquals("Front door", device.name)
            viewModel.renameDevice("Renamed")
            assertTrue(awaitFirst { viewModel.state.value.deviceInfoSaved.takeIf { it } })
            assertEquals("Renamed", awaitFirst {
                repository.snapshot.devices.firstOrNull { it.device.id == "DEMO7410001" }?.displayName
            })
            viewModel.consumeDeviceInfoSaved()
            assertFalse(viewModel.state.value.deviceInfoSaved)
        } finally {
            repository.close()
        }
    }
}
