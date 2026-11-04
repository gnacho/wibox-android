package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.BindRequest
import dev.nacho.wibox.data.CodeParseError
import dev.nacho.wibox.data.CodeParseException
import dev.nacho.wibox.data.DeviceCodeParser
import dev.nacho.wibox.data.DiscoveredRecord
import dev.nacho.wibox.data.ParsedCode
import dev.nacho.wibox.data.ParsedDeviceQr
import dev.nacho.wibox.data.ParsedInvitation
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProvisionStage {
    IDLE,
    CONNECTING,
    SENDING,
    WAITING,
    BINDING,
    READY,
    FAILED,
    CANCELLED,
    PERMISSION_REQUIRED,
    OUTCOME_UNKNOWN,
}

enum class PairingError {
    EMPTY_FIELDS,
    DUPLICATE,
    INVITATION_USED,
    INVALID,
    NETWORK,
    UNSUPPORTED,
    OUTCOME_UNKNOWN,
}

enum class PermissionKind {
    CAMERA,
    LOCATION,
    MICROPHONE,
    STORAGE,
}

data class PermissionRationaleState(
    val kind: PermissionKind,
    val denied: Boolean = false,
)

data class PairingUiState(
    val codeInput: String = "",
    val codeError: CodeParseError? = null,
    val name: String = "",
    val uid: String = "",
    val authCode: String = "",
    val model: String = "7410",
    val apName: String = "WIBOX",
    val qrLocked: Boolean = false,
    val invitation: ParsedInvitation? = null,
    val onlineDevices: LoadState<List<DiscoveredRecord>> = LoadState.Content(emptyList()),
    val binding: Boolean = false,
    val bindError: PairingError? = null,
    val boundDeviceId: String? = null,
    val provisionStage: ProvisionStage = ProvisionStage.IDLE,
    val selectedSsid: String = "Wibox 2.4 GHz",
    val wifiPassword: String = "",
    val showWifiPassword: Boolean = false,
    val saveWifiPassword: Boolean = false,
    val permission: PermissionRationaleState? = null,
)

class PairingViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        PairingUiState(
            name = savedStateHandle[NAME_KEY] ?: "",
            uid = savedStateHandle[UID_KEY] ?: "",
            model = savedStateHandle[MODEL_KEY] ?: "7410",
            apName = savedStateHandle[AP_NAME_KEY] ?: "WIBOX",
            qrLocked = savedStateHandle[QR_LOCKED_KEY] ?: false,
            selectedSsid = savedStateHandle[SSID_KEY] ?: "Wibox 2.4 GHz",
            permission = if (repository.snapshot.preferences.scenario == AppScenario.PERMISSION) {
                PermissionRationaleState(PermissionKind.CAMERA)
            } else {
                null
            },
        ),
    )
    init {
    savedStateHandle.get<String>(INVITE_CODE_KEY)?.let { savedInvite ->
        val restoredUid = mutableState.value.uid
        if (restoredUid.isNotBlank()) {
            mutableState.value = mutableState.value.copy(
                invitation = ParsedInvitation(
                    inviteCode = savedInvite,
                    ownerId = "",
                    oemId = "G0021",
                    appVersion = "",
                    deviceId = restoredUid,
                    from = "QrInvite",
                    appId = 2021,
                ),
                authCode = savedInvite,
                qrLocked = true,
            )
        }
        }
    }
    val state: StateFlow<PairingUiState> = mutableState.asStateFlow()
    private var provisionJob: Job? = null
    private var discoveryJob: Job? = null

    fun beginNewFlow() {
        provisionJob?.cancel()
        discoveryJob?.cancel()
        listOf(NAME_KEY, UID_KEY, MODEL_KEY, AP_NAME_KEY, QR_LOCKED_KEY, SSID_KEY, INVITE_CODE_KEY).forEach { key ->
            savedStateHandle.remove<Any>(key)
        }
        mutableState.value = PairingUiState(
            permission = if (repository.snapshot.preferences.scenario == AppScenario.PERMISSION) {
                PermissionRationaleState(PermissionKind.CAMERA)
            } else {
                null
            },
        )
    }

    fun updateCodeInput(value: String) {
        mutableState.value = mutableState.value.copy(codeInput = value, codeError = null)
    }

    fun validateCode(): ParsedCode? = try {
        val parsed = DeviceCodeParser.parse(mutableState.value.codeInput)
        when (parsed) {
            is ParsedCode.Device -> applyDeviceQr(parsed.value)
            is ParsedCode.Invitation -> {
                savedStateHandle[INVITE_CODE_KEY] = parsed.value.inviteCode
                mutableState.value = mutableState.value.copy(
                    invitation = parsed.value,
                    uid = parsed.value.deviceId,
                    authCode = parsed.value.inviteCode,
                    name = "Shared home",
                    qrLocked = true,
                    codeError = null,
                )
            }
        }
        parsed
    } catch (error: CodeParseException) {
        mutableState.value = mutableState.value.copy(codeError = error.reason)
        null
    }

    private fun applyDeviceQr(qr: ParsedDeviceQr) {
        savedStateHandle[NAME_KEY] = "Home ${qr.uid.takeLast(4)}"
        savedStateHandle[UID_KEY] = qr.uid
        savedStateHandle[MODEL_KEY] = qr.model
        savedStateHandle[AP_NAME_KEY] = qr.apName
        savedStateHandle[QR_LOCKED_KEY] = true
        mutableState.value = mutableState.value.copy(
            name = "Home ${qr.uid.takeLast(4)}",
            uid = qr.uid,
            authCode = qr.authCode,
            model = qr.model,
            apName = qr.apName,
            invitation = null,
            qrLocked = true,
            codeError = null,
        )
    }

    fun startManual() {
        mutableState.value = mutableState.value.copy(
            name = "",
            uid = "",
            authCode = "",
            invitation = null,
            qrLocked = false,
            bindError = null,
        )
    }

    fun selectOnlineDevice(device: DiscoveredRecord) {
        savedStateHandle[UID_KEY] = device.id
        savedStateHandle[NAME_KEY] = "Home ${device.id.takeLast(4)}"
        savedStateHandle[MODEL_KEY] = device.declaredModel
        savedStateHandle[QR_LOCKED_KEY] = true
        mutableState.value = mutableState.value.copy(
            uid = device.id,
            name = "Home ${device.id.takeLast(4)}",
            model = device.declaredModel,
            invitation = null,
            qrLocked = true,
            bindError = null,
        )
    }

    fun scanOnlineDevices() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(onlineDevices = LoadState.Loading())
            try {
                val devices = repository.discoverCandidates()
                mutableState.value = mutableState.value.copy(onlineDevices = LoadState.Content(devices))
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(onlineDevices = LoadState.Error(error))
            }
        }
    }

    fun updateName(value: String) {
        savedStateHandle[NAME_KEY] = value
        mutableState.value = mutableState.value.copy(name = value, bindError = null)
    }

    fun updateUid(value: String) {
        if (mutableState.value.qrLocked) return
        savedStateHandle[UID_KEY] = value
        mutableState.value = mutableState.value.copy(uid = value, bindError = null)
    }

    fun updateAuthCode(value: String) {
        if (mutableState.value.invitation != null) return
        mutableState.value = mutableState.value.copy(authCode = value, bindError = null)
    }

    fun bindDevice() {
        if (mutableState.value.binding) return
        val current = mutableState.value
        if (current.name.isBlank() || current.uid.isBlank() || current.authCode.isBlank()) {
            mutableState.value = current.copy(bindError = PairingError.EMPTY_FIELDS)
            return
        }
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(binding = true, bindError = null, provisionStage = ProvisionStage.BINDING)
            try {
                val device = kotlinx.coroutines.withTimeout(120_000) {
                    if (current.invitation == null) {
                        Log.d("WIBOX-ACCEPT", "BIND: invitation NULL -> DEMO uid=" + current.uid)
                        repository.bind(BindRequest(current.uid, current.name, current.model))
                    } else {
                        Log.d("WIBOX-ACCEPT", "BIND: invitation PRESENTE -> REAL uid=" + current.uid + " code=" + current.invitation.inviteCode)
                        repository.acceptInvitation(current.invitation, current.name)
                    }
                }
                mutableState.value = mutableState.value.copy(
                    binding = false,
                    boundDeviceId = device.device.id,
                    provisionStage = ProvisionStage.READY,
                    authCode = "",
                    wifiPassword = "",
                )
            } catch (safety: kotlinx.coroutines.TimeoutCancellationException) {
                Log.d("WIBOX-ACCEPT", "BIND: safety timeout -> OUTCOME_UNKNOWN")
                mutableState.value = mutableState.value.copy(
                    binding = false,
                    bindError = PairingError.OUTCOME_UNKNOWN,
                    provisionStage = ProvisionStage.OUTCOME_UNKNOWN,
                )
            } catch (error: ProviderError.InvitationUsed) {
                Log.d("WIBOX-ACCEPT", "BIND: invitation already used (100151007)")
                mutableState.value = mutableState.value.copy(binding = false, bindError = PairingError.INVITATION_USED)
            } catch (error: ProviderError.Conflict) {
                mutableState.value = mutableState.value.copy(binding = false, bindError = PairingError.DUPLICATE)
            } catch (error: ProviderError.Timeout) {
                mutableState.value = mutableState.value.copy(
                    binding = false,
                    bindError = PairingError.OUTCOME_UNKNOWN,
                    provisionStage = ProvisionStage.OUTCOME_UNKNOWN,
                )
            } catch (error: ProviderError.Unsupported) {
                mutableState.value = mutableState.value.copy(binding = false, bindError = PairingError.UNSUPPORTED)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(binding = false, bindError = PairingError.NETWORK)
            }
        }
    }

    fun consumeBoundDevice() {
        mutableState.value = mutableState.value.copy(boundDeviceId = null)
    }

    fun updateSelectedSsid(value: String) {
        savedStateHandle[SSID_KEY] = value
        mutableState.value = mutableState.value.copy(selectedSsid = value)
    }

    fun updateWifiPassword(value: String) {
        mutableState.value = mutableState.value.copy(wifiPassword = value)
    }

    fun toggleShowWifiPassword() {
        mutableState.value = mutableState.value.copy(showWifiPassword = !mutableState.value.showWifiPassword)
    }

    fun toggleSaveWifiPassword() {
        mutableState.value = mutableState.value.copy(saveWifiPassword = !mutableState.value.saveWifiPassword)
    }

    fun startProvisioning() {
        provisionJob?.cancel()
        provisionJob = viewModelScope.launch(dispatcher) {
            if (repository.snapshot.preferences.scenario == AppScenario.PERMISSION) {
                mutableState.value = mutableState.value.copy(
                    provisionStage = ProvisionStage.PERMISSION_REQUIRED,
                    permission = PermissionRationaleState(PermissionKind.CAMERA),
                )
                return@launch
            }
            mutableState.value = mutableState.value.copy(provisionStage = ProvisionStage.CONNECTING)
            delay(350)
            mutableState.value = mutableState.value.copy(provisionStage = ProvisionStage.SENDING)
            delay(350)
            mutableState.value = mutableState.value.copy(provisionStage = ProvisionStage.WAITING)
            delay(350)
            mutableState.value = mutableState.value.copy(provisionStage = ProvisionStage.READY)
        }
    }

    fun failProvisioning() {
        provisionJob?.cancel()
        mutableState.value = mutableState.value.copy(provisionStage = ProvisionStage.FAILED)
    }

    fun onPermissionResult(granted: Boolean) {
        val kind = mutableState.value.permission?.kind ?: PermissionKind.CAMERA
        mutableState.value = mutableState.value.copy(
            permission = if (granted) null else PermissionRationaleState(kind, denied = true),
            provisionStage = if (granted) ProvisionStage.IDLE else ProvisionStage.PERMISSION_REQUIRED,
        )
        if (granted) startProvisioning()
    }

    fun cancel() {
        provisionJob?.cancel()
        discoveryJob?.cancel()
        mutableState.value = mutableState.value.copy(
            provisionStage = ProvisionStage.CANCELLED,
            authCode = "",
            wifiPassword = "",
            codeInput = "",
            boundDeviceId = null,
        )
    }

    override fun onCleared() {
        provisionJob?.cancel()
        discoveryJob?.cancel()
    }

    companion object {

        private const val INVITE_CODE_KEY = "invite_code"
        private const val NAME_KEY = "pairing_name"
        private const val UID_KEY = "pairing_uid"
        private const val MODEL_KEY = "pairing_model"
        private const val AP_NAME_KEY = "pairing_ap_name"
        private const val QR_LOCKED_KEY = "pairing_qr_locked"
        private const val SSID_KEY = "pairing_ssid"
    }
}
