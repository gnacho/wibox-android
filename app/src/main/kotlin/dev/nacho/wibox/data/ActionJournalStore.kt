package dev.nacho.wibox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.nacho.wibox.core.cloud.ActionAcknowledgementLevel
import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.ProviderId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ActionJournalStore {
    suspend fun load(): List<ActionJournalEntry>
    suspend fun save(entries: List<ActionJournalEntry>)
}

private val Context.actionDataStore: DataStore<Preferences> by preferencesDataStore(name = "wibox_actions")

class DataStoreActionJournalStore(
    private val dataStore: DataStore<Preferences>,
) : ActionJournalStore {
    constructor(context: Context) : this(context.actionDataStore)

    private val key = stringPreferencesKey("action_journal_json")
    private val json = Json { encodeDefaults = true }

    override suspend fun load(): List<ActionJournalEntry> {
        val encoded = dataStore.data.first()[key] ?: return emptyList()
        val stored = try {
            json.decodeFromString<List<StoredAction>>(encoded)
        } catch (error: Throwable) {
            throw ProviderError.Persistence("loadActionJournal")
        }
        return stored.map { it.toJournalEntry() }
    }

    override suspend fun save(entries: List<ActionJournalEntry>) {
        val stored = entries.map(StoredAction::fromJournalEntry)
        dataStore.edit { it[key] = json.encodeToString(stored) }
    }
}

class InMemoryActionJournalStore(
    initial: List<ActionJournalEntry> = emptyList(),
) : ActionJournalStore {
    private val mutex = Mutex()
    private var entries = initial

    override suspend fun load(): List<ActionJournalEntry> = mutex.withLock { entries }

    override suspend fun save(entries: List<ActionJournalEntry>) {
        mutex.withLock { this.entries = entries }
    }
}

@Serializable
private data class StoredAction(
    val commandId: String,
    val deviceId: String,
    val channel: Int,
    val type: String,
    val pending: Boolean,
    val level: String? = null,
    val errorOperation: String? = null,
) {
    fun toJournalEntry(): ActionJournalEntry {
        val command = ActionCommand(commandId, DeviceRef(ProviderId.DEMO, deviceId), channel, ActionType.valueOf(type))
        return ActionJournalEntry(
            command = command,
            outcome = level?.let { levelName ->
                when (ActionAcknowledgementLevel.valueOf(levelName)) {
                    ActionAcknowledgementLevel.LOCAL_ACCEPTED -> ActionOutcome.localAccepted(commandId)
                    ActionAcknowledgementLevel.PROVIDER_ACCEPTED -> ActionOutcome.providerAccepted(commandId)
                    ActionAcknowledgementLevel.DEVICE_ACKNOWLEDGED -> ActionOutcome.deviceAcknowledged(commandId)
                    ActionAcknowledgementLevel.PHYSICAL_OBSERVED -> ActionOutcome.unknown(commandId)
                    ActionAcknowledgementLevel.REJECTED -> ActionOutcome.rejected(commandId)
                    ActionAcknowledgementLevel.UNKNOWN_OUTCOME -> ActionOutcome.unknown(commandId)
                }
            },
            pending = pending,
            error = errorOperation?.let { ProviderError.Unknown(it) },
        )
    }

    companion object {
        fun fromJournalEntry(entry: ActionJournalEntry): StoredAction = StoredAction(
            commandId = entry.command.commandId,
            deviceId = entry.command.device.id,
            channel = entry.command.channel,
            type = entry.command.type.name,
            pending = entry.pending,
            level = entry.outcome?.level?.name,
            errorOperation = entry.error?.operation,
        )
    }
}
