package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.ActionCommand
import dev.nacho.wibox.core.cloud.ActionType
import dev.nacho.wibox.core.cloud.DeviceRef
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallStatus {
    RINGING,
    ACCEPT_PENDING,
    ACCEPTED,
    REJECT_PENDING,
    REJECTED,
    AUTO_REFUSED,
    ANSWERED_ELSEWHERE,
    FAILED,
}

data class CallUiState(
    val callId: String? = null,
    val device: DeviceUi? = null,
    val status: CallStatus = CallStatus.RINGING,
    val remainingSeconds: Int = 30,
    val action: ActionUi = ActionUi(),
)

class CallViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = mutableState.asStateFlow()
    private var timeoutJob: Job? = null

    fun start(callId: String) {
        if (mutableState.value.callId == callId) return
        timeoutJob?.cancel()
        val deviceId = callId.removePrefix("demo-call-")
        val device = repository.snapshots.value.devices.firstOrNull { it.device.id == deviceId }
        savedStateHandle[CALL_ID_KEY] = callId
        mutableState.value = CallUiState(
            callId = callId,
            device = device?.let(UiMapper::device),
            status = savedStateHandle[STATUS_KEY] ?: CallStatus.RINGING,
        )
        timeoutJob = viewModelScope.launch(dispatcher) {
            while (mutableState.value.remainingSeconds > 0) {
                delay(1_000)
                if (mutableState.value.status !in setOf(CallStatus.RINGING, CallStatus.ACCEPT_PENDING, CallStatus.REJECT_PENDING)) {
                    return@launch
                }
                mutableState.value = mutableState.value.copy(remainingSeconds = mutableState.value.remainingSeconds - 1)
            }
            if (mutableState.value.status == CallStatus.RINGING) {
                reject(ActionType.CALL_REJECT, automatic = true)
            }
        }
    }

    fun accept() = reject(ActionType.CALL_ACCEPT)

    fun refuse() = reject(ActionType.CALL_REJECT)

    private fun reject(type: ActionType, automatic: Boolean = false) {
        val callId = savedStateHandle.get<String>(CALL_ID_KEY) ?: return
        if (savedStateHandle.get<Boolean>(CONSUMED_KEY) == true) return
        savedStateHandle[CONSUMED_KEY] = true
        timeoutJob?.cancel()
        val accepting = type == ActionType.CALL_ACCEPT
        mutableState.value = mutableState.value.copy(
            status = if (accepting) CallStatus.ACCEPT_PENDING else CallStatus.REJECT_PENDING,
        )
        viewModelScope.launch(dispatcher) {
            try {
                val outcome = repository.execute(ActionCommand("$callId:${type.name}", DeviceRef(repository.provider.descriptor.id, callId.removePrefix("demo-call-")), 1, type))
                val newStatus = when {
                    accepting -> CallStatus.ACCEPTED
                    automatic -> CallStatus.AUTO_REFUSED
                    else -> CallStatus.REJECTED
                }
                savedStateHandle[STATUS_KEY] = newStatus
                mutableState.value = mutableState.value.copy(
                    status = newStatus,
                    action = ActionUi(outcome = outcome),
                )
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(status = CallStatus.FAILED, action = ActionUi(error = error))
            }
        }
    }

    fun markAnsweredElsewhere() {
        if (savedStateHandle.get<Boolean>(CONSUMED_KEY) == true) return
        savedStateHandle[CONSUMED_KEY] = true
        savedStateHandle[STATUS_KEY] = CallStatus.ANSWERED_ELSEWHERE
        timeoutJob?.cancel()
        mutableState.value = mutableState.value.copy(status = CallStatus.ANSWERED_ELSEWHERE)
    }

    override fun onCleared() {
        timeoutJob?.cancel()
    }

    companion object {
        private const val CALL_ID_KEY = "call_id"
        private const val STATUS_KEY = "call_status"
        private const val CONSUMED_KEY = "call_consumed"
    }
}
