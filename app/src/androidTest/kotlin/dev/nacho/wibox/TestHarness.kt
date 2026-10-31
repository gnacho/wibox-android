package dev.nacho.wibox

import dev.nacho.wibox.data.ActionCoordinator
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.InMemoryActionJournalStore
import dev.nacho.wibox.data.WiboxRepository
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoScenario
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.InMemoryDemoStore

fun buildRepository(scenario: AppScenario = AppScenario.NORMAL): WiboxRepository {
    val fixtures = DemoFixtures.initial()
    val store = InMemoryDemoStore(fixtures.copy(settings = fixtures.settings.copy(scenario = DemoScenario.valueOf(scenario.name))))
    val provider = DemoWiboxProvider(persist = store::save)
    return WiboxRepository(
        provider = provider,
        operations = provider,
        actionCoordinator = ActionCoordinator(provider, InMemoryActionJournalStore()),
        restore = { provider.initializeDemo(store.load() ?: DemoFixtures.initial()) },
    )
}
