package dev.nacho.wibox.data.demo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.nacho.wibox.core.cloud.ProviderError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

interface LocalDemoStore {
    suspend fun load(): DemoSnapshot?
    suspend fun save(snapshot: DemoSnapshot)
}

private val Context.demoDataStore: DataStore<Preferences> by preferencesDataStore(name = "wibox_demo")

class DataStoreDemoStore(
    private val dataStore: DataStore<Preferences>,
) : LocalDemoStore {
    constructor(context: Context) : this(context.demoDataStore)

    private val snapshotKey = stringPreferencesKey("snapshot_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun load(): DemoSnapshot? {
        val encoded = dataStore.data.first()[snapshotKey] ?: return null
        return try {
            json.decodeFromString<DemoSnapshot>(encoded)
        } catch (error: Throwable) {
            throw ProviderError.Persistence("loadDemoSnapshot")
        }
    }

    override suspend fun save(snapshot: DemoSnapshot) {
        val encoded = json.encodeToString(DemoSnapshot.serializer(), snapshot)
        dataStore.edit { preferences ->
            preferences[snapshotKey] = encoded
        }
    }
}

class InMemoryDemoStore(
    initial: DemoSnapshot? = null,
) : LocalDemoStore {
    private val mutex = Mutex()
    private var snapshot = initial

    override suspend fun load(): DemoSnapshot? = mutex.withLock { snapshot }

    override suspend fun save(snapshot: DemoSnapshot) {
        mutex.withLock { this.snapshot = snapshot }
    }
}
