package dev.nacho.wibox.data

import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionOutcome
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.core.cloud.WiboxProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ActionCoordinator(
    private val provider: WiboxProvider,
    private val store: ActionJournalStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private val deferreds = mutableMapOf<String, Deferred<ActionOutcome>>()
    private val journal = MutableStateFlow<Map<String, ActionJournalEntry>>(emptyMap())
    val entries: StateFlow<Map<String, ActionJournalEntry>> = journal.asStateFlow()
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        mutex.withLock {
            if (initialized) return@withLock
            journal.value = store.load().associateBy { it.command.commandId }
            initialized = true
        }
    }

    suspend fun execute(command: ActionCommand): ActionOutcome {
        initialize()
        val existing = mutex.withLock {
            val current = journal.value[command.commandId]
            if (current != null && current.command.signature() != command.signature()) {
                throw ProviderError.Conflict("actionCommandId")
            }
            when {
                deferreds.containsKey(command.commandId) -> CommandExecution.ExistingDeferred(deferreds.getValue(command.commandId))
                current?.error != null -> throw current.error
                current?.outcome != null -> CommandExecution.Completed(current.outcome)
                current?.pending == true -> CommandExecution.Completed(ActionOutcome.unknown(command.commandId))
                else -> null
            }
        }
        @Suppress("UNCHECKED_CAST")
        return when (existing) {
            is CommandExecution.Completed -> existing.outcome
            is CommandExecution.ExistingDeferred -> existing.deferred.await()
            null -> {
                val deferred = mutex.withLock {
                    journal.updateEntry(
                        ActionJournalEntry(command = command, outcome = null, pending = true),
                    )
                    scope.async { provider.actions.execute(command) }.also { deferreds[command.commandId] = it }
                }
                try {
                    val outcome = deferred.await()
                    complete(command.commandId, ActionJournalEntry(command, outcome, pending = false))
                    outcome
                } catch (error: CancellationException) {
                    complete(command.commandId, ActionJournalEntry(command, ActionOutcome.unknown(command.commandId), pending = false))
                    throw error
                } catch (error: Throwable) {
                    val providerError = error as? ProviderError ?: ProviderError.Unknown("performAction")
                    complete(
                        command.commandId,
                        ActionJournalEntry(command, outcome = null, pending = false, error = providerError),
                    )
                    throw error
                } finally {
                    mutex.withLock { deferreds.remove(command.commandId) }
                }
            }
        }
    }

    private suspend fun complete(commandId: String, entry: ActionJournalEntry) {
        mutex.withLock {
            journal.updateEntry(entry)
        }
    }

    private suspend fun MutableStateFlow<Map<String, ActionJournalEntry>>.updateEntry(entry: ActionJournalEntry) {
        value = value + (entry.command.commandId to entry)
        store.save(value.values.toList())
    }

    fun close() {
        scope.cancel()
    }
}

private sealed interface CommandExecution {
    data class Completed(val outcome: ActionOutcome) : CommandExecution
    data class ExistingDeferred(val deferred: Deferred<ActionOutcome>) : CommandExecution
}

private fun ActionCommand.signature(): String = "${device.id}|$channel|$type"
