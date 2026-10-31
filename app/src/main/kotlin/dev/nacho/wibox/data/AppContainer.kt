package dev.nacho.wibox.data

import android.content.Context
import dev.nacho.wibox.data.demo.DataStoreDemoStore
import dev.nacho.wibox.data.demo.DemoFixtures
import dev.nacho.wibox.data.demo.DemoWiboxProvider
import dev.nacho.wibox.data.demo.LocalDemoStore

class AppContainer(
    private val demoStore: LocalDemoStore,
    actionStore: ActionJournalStore,
) {
    private val provider = DemoWiboxProvider(persist = demoStore::save)
    val repository = WiboxRepository(
        provider = provider,
        operations = provider,
        actionCoordinator = ActionCoordinator(provider, actionStore),
        restore = { provider.initializeDemo(demoStore.load() ?: DemoFixtures.initial()) },
    )

    suspend fun initialize() = repository.prepare()

    fun close() = repository.close()

    companion object {
        fun local(context: Context): AppContainer = AppContainer(
            demoStore = DataStoreDemoStore(context.applicationContext),
            actionStore = DataStoreActionJournalStore(context.applicationContext),
        )
    }
}
