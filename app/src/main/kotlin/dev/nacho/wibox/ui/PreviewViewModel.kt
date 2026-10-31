package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.MediaKind
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PreviewStatus {
    IDLE,
    CONNECTING,
    PLAYING_SIMULATED,
    STOPPED,
    REFRESH,
    PASSWORD_ERROR,
    OFFLINE,
    UNSUPPORTED,
    NO_PERMISSION,
    TIMEOUT,
    ERROR,
}

data class PreviewUiState(
    val sessionId: String? = null,
    val device: DeviceUi? = null,
    val status: PreviewStatus = PreviewStatus.IDLE,
    val fourPanels: Boolean = false,
    val listening: Boolean = false,
    val talking: Boolean = false,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val action: ActionUi = ActionUi(),
    val mediaSaved: Boolean = false,
)

class PreviewViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var recordingJob: Job? = null

    fun load(deviceId: String) {
        loadJob?.cancel()
        recordingJob?.cancel()
        savedStateHandle[SESSION_ID_KEY] = "preview-$deviceId"
        mutableState.value = PreviewUiState(
            sessionId = "preview-$deviceId",
            fourPanels = savedStateHandle[FOUR_PANELS_KEY] ?: false,
        )
        loadJob = viewModelScope.launch(dispatcher) {
            val device = repository.snapshot.devices.firstOrNull { it.device.id == deviceId }
            if (device == null) {
                mutableState.value = mutableState.value.copy(status = PreviewStatus.ERROR)
                return@launch
            }
            val status = when (repository.snapshot.preferences.scenario) {
                AppScenario.UNSUPPORTED -> PreviewStatus.UNSUPPORTED
                AppScenario.PERMISSION -> PreviewStatus.NO_PERMISSION
                AppScenario.OFFLINE -> PreviewStatus.OFFLINE
                else -> if (device.presence == dev.nacho.wibox.core.cloud.DevicePresence.OFFLINE) {
                    PreviewStatus.OFFLINE
                } else {
                    PreviewStatus.CONNECTING
                }
            }
            mutableState.value = mutableState.value.copy(device = UiMapper.device(device), status = status)
            if (status == PreviewStatus.CONNECTING) {
                delay(400)
                mutableState.value = mutableState.value.copy(status = PreviewStatus.PLAYING_SIMULATED)
            }
        }
    }

    fun togglePlayback() {
        val deviceId = savedStateHandle.get<String>(SESSION_ID_KEY)?.removePrefix("preview-") ?: return
        when (mutableState.value.status) {
            PreviewStatus.PLAYING_SIMULATED -> mutableState.value = mutableState.value.copy(status = PreviewStatus.STOPPED)
            PreviewStatus.STOPPED, PreviewStatus.REFRESH -> load(deviceId)
            else -> Unit
        }
    }

    fun togglePanels() {
        val value = !mutableState.value.fourPanels
        savedStateHandle[FOUR_PANELS_KEY] = value
        mutableState.value = mutableState.value.copy(fourPanels = value)
    }

    fun toggleListening() {
        mutableState.value = mutableState.value.copy(listening = !mutableState.value.listening)
    }

    fun toggleTalking() {
        mutableState.value = mutableState.value.copy(talking = !mutableState.value.talking)
    }

    fun toggleRecording() {
        if (mutableState.value.recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val device = mutableState.value.device ?: return
        if (mutableState.value.status != PreviewStatus.PLAYING_SIMULATED) return
        mutableState.value = mutableState.value.copy(recording = true, recordingSeconds = 0, mediaSaved = false)
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                delay(1_000)
                mutableState.value = mutableState.value.copy(recordingSeconds = mutableState.value.recordingSeconds + 1)
            }
        }
    }

    private fun stopRecording() {
        val device = mutableState.value.device ?: return
        recordingJob?.cancel()
        mutableState.value = mutableState.value.copy(recording = false)
        viewModelScope.launch(dispatcher) {
            repository.captureMedia(device.id, MediaKind.VIDEO)
            mutableState.value = mutableState.value.copy(mediaSaved = true)
        }
    }

    fun snapshot() {
        val device = mutableState.value.device ?: return
        if (mutableState.value.status != PreviewStatus.PLAYING_SIMULATED) return
        viewModelScope.launch(dispatcher) {
            repository.captureMedia(device.id, MediaKind.PHOTO)
            mutableState.value = mutableState.value.copy(mediaSaved = true)
        }
    }

    fun performAction(type: ActionType) {
        val device = mutableState.value.device ?: return
        if (mutableState.value.action.pendingCommandId != null) return
        val sequence = (savedStateHandle.get<Int>(ACTION_SEQUENCE_KEY) ?: 0) + 1
        savedStateHandle[ACTION_SEQUENCE_KEY] = sequence
        val commandId = "${device.id}:${type.name}:$sequence"
        savedStateHandle[LAST_COMMAND_KEY] = commandId
        val command = ActionCommand(commandId, DeviceRef(repository.provider.descriptor.id, device.id), 1, type)
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(action = ActionUi(pendingCommandId = commandId), mediaSaved = false)
            try {
                val outcome = repository.execute(command)
                mutableState.value = mutableState.value.copy(action = ActionUi(outcome = outcome))
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(action = ActionUi(error = error))
            }
        }
    }

    fun markTimeout() {
        mutableState.value = mutableState.value.copy(status = PreviewStatus.TIMEOUT)
    }

    fun markPasswordError() {
        mutableState.value = mutableState.value.copy(status = PreviewStatus.PASSWORD_ERROR)
    }

    override fun onCleared() {
        loadJob?.cancel()
        recordingJob?.cancel()
    }

    companion object {
        private const val SESSION_ID_KEY = "preview_session_id"
        private const val FOUR_PANELS_KEY = "preview_four_panels"
        private const val ACTION_SEQUENCE_KEY = "preview_action_sequence"
        private const val LAST_COMMAND_KEY = "preview_last_command"
    }
}
