package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.RepositorySnapshot
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicesUiState(
    val devices: LoadState<List<DeviceUi>> = LoadState.Loading(),
    val action: ActionUi = ActionUi(),
)

class DevicesViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repository.snapshots.collect(::renderSnapshot)
        }
        viewModelScope.launch(dispatcher) {
            repository.actionCoordinator.entries.collect { entries ->
                val commandId = savedStateHandle.get<String>(LAST_COMMAND_KEY) ?: return@collect
                val entry = entries[commandId] ?: return@collect
                mutableState.value = mutableState.value.copy(
                    action = ActionUi(
                        pendingCommandId = if (entry.pending) commandId else null,
                        outcome = entry.outcome,
                        error = entry.error,
                    ),
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(dispatcher) {
            val previous = mutableState.value.devices
            mutableState.value = mutableState.value.copy(
                devices = when (previous) {
                    is LoadState.Content -> previous.copy(refreshing = true)
                    is LoadState.Error -> previous.copy(refreshing = true)
                    is LoadState.Loading -> LoadState.Loading(previous.data, refreshing = previous.data != null)
                },
            )
            try {
                repository.refreshDevices()
                val refreshed = mutableState.value.devices
                if (refreshed is LoadState.Content) {
                    mutableState.value = mutableState.value.copy(devices = refreshed.copy(refreshing = false))
                }
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(
                    devices = LoadState.Error(error, previous.data, lastUpdated = previous.lastUpdated),
                )
            }
        }
    }

    fun performAction(deviceId: String, type: ActionType) {
        if (mutableState.value.action.pendingCommandId != null) return
        val sequence = (savedStateHandle.get<Int>(ACTION_SEQUENCE_KEY) ?: 0) + 1
        savedStateHandle[ACTION_SEQUENCE_KEY] = sequence
        val commandId = "$deviceId:${type.name}:$sequence"
        savedStateHandle[LAST_COMMAND_KEY] = commandId
        val command = ActionCommand(commandId, DeviceRef(repository.provider.descriptor.id, deviceId), 1, type)
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(
                action = ActionUi(pendingCommandId = commandId),
            )
            try {
                val outcome = repository.execute(command)
                mutableState.value = mutableState.value.copy(action = ActionUi(outcome = outcome))
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(action = ActionUi(error = error))
            }
        }
    }

    private fun renderSnapshot(snapshot: RepositorySnapshot) {
        val previous = mutableState.value.devices.data
        val mapped = snapshot.devices.map(UiMapper::device)
        val updated = snapshot.devices.mapNotNull { it.lastObservedAtMillis }.maxOrNull()
        mutableState.value = mutableState.value.copy(
            devices = when (snapshot.preferences.scenario) {
                AppScenario.LOADING -> LoadState.Loading(previous, refreshing = previous != null, lastUpdated = updated)
                AppScenario.EMPTY -> LoadState.Content(emptyList(), lastUpdated = updated)
                AppScenario.ERROR -> LoadState.Error(
                    error = ProviderError.Network("renderDevices"),
                    data = previous ?: mapped,
                    lastUpdated = updated,
                )
                AppScenario.OFFLINE -> LoadState.Content(mapped, stale = true, lastUpdated = updated)
                else -> LoadState.Content(mapped, lastUpdated = updated)
            },
        )
    }

    companion object {
        private const val ACTION_SEQUENCE_KEY = "device_action_sequence"
        private const val LAST_COMMAND_KEY = "device_last_command"
    }
}
