package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import dev.nacho.wibox.core.cloud.devicecgi.DeviceFirmwareInfo
import dev.nacho.wibox.data.ActionCoordinator
import dev.nacho.wibox.data.FirmwareInspector
import dev.nacho.wibox.data.FirmwareResult
import dev.nacho.wibox.data.InMemoryActionJournalStore
import dev.nacho.wibox.data.WiboxRepository
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.InMemoryDemoStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FirmwareInspectTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeInspector(private val result: FirmwareResult) : FirmwareInspector {
        override suspend fun inspect(deviceId: String): FirmwareResult = result
    }

    private fun TestScope.repository(inspector: FirmwareInspector?): WiboxRepository {
        val store = InMemoryDemoStore()
        val provider = DemoWiboxProvider(persist = store::save)
        return WiboxRepository(
            provider = provider,
            operations = provider,
            actionCoordinator = ActionCoordinator(provider, InMemoryActionJournalStore()),
            restore = { provider.initializeDemo(store.load() ?: DemoFixtures.initial()) },
            firmwareInspector = inspector,
        )
    }

    @Test
    fun repositoryReturnsInspectorResult() = runTest {
        val info = DeviceFirmwareInfo(
            version = "2.4.1",
            releaseDate = "2026-05-01",
            model = "IDS7938",
            mac = "AA:BB",
            latestVersion = "2.5.0",
        )
        val repository = repository(FakeInspector(FirmwareResult.Available(info)))
        val result = repository.inspectFirmware("dev-1")
        assertTrue(result is FirmwareResult.Available)
        assertEquals("2.4.1", (result as FirmwareResult.Available).info.version)
    }

    @Test
    fun repositoryWithoutInspectorIsHonest() = runTest {
        val result = repository(null).inspectFirmware("dev-1")
        assertTrue(result is FirmwareResult.Unavailable)
    }

    @Test
    fun viewModelPublishesFirmwareState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = repository(FakeInspector(FirmwareResult.Unavailable("fuera de la red local")))
        val viewModel = DeviceDetailViewModel(repository, SavedStateHandle(), dispatcher)
        viewModel.load("dev-1")
        viewModel.checkFirmware()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(false, state.queryingFirmware)
        assertEquals("fuera de la red local", (state.firmware as FirmwareResult.Unavailable).reason)
    }
}
