package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.DevicePresence
import dev.nacho.wibox.core.cloud.DeviceSnapshot
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.AppPreferences
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceDetailUiState(
    val device: DeviceUi? = null,
    val preferences: AppPreferences = AppPreferences(),
    val shadow: LoadState<ShadowUi> = LoadState.Loading(),
    val deviceInfoSaved: Boolean = false,
    val deletedDeviceId: String? = null,
    val operationError: ProviderError? = null,
    val queryingFirmware: Boolean = false,
    val timeSyncSucceeded: Boolean? = null,
)

class DeviceDetailViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeviceDetailUiState())
    val state: StateFlow<DeviceDetailUiState> = mutableState.asStateFlow()
    private var loadedDeviceId: String? = null

    fun load(deviceId: String) {
        if (loadedDeviceId == deviceId) return
        loadedDeviceId = deviceId
        savedStateHandle[DEVICE_ID_KEY] = deviceId
        viewModelScope.launch(dispatcher) {
            repository.snapshots.collect { snapshot ->
                val device = snapshot.devices.firstOrNull { it.device.id == deviceId }
                mutableState.value = mutableState.value.copy(
                    device = device?.let(UiMapper::device),
                    preferences = snapshot.preferences,
                    operationError = if (device == null) ProviderError.NotFound("deviceDetail") else null,
                )
            }
        }
        loadShadow(deviceId)
    }

    private fun loadShadow(deviceId: String) {
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(shadow = LoadState.Loading(mutableState.value.shadow.data))
            try {
                val shadow = repository.provider.devices.getShadow(deviceId)
                mutableState.value = mutableState.value.copy(
                    shadow = LoadState.Content(
                        ShadowUi(
                            returnedFields = shadow.returnedFields,
                            path = shadow.path,
                            timestampMillis = shadow.timestampMillis,
                            fresh = shadow.fresh,
                        ),
                    ),
                )
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(
                    shadow = LoadState.Error(error, mutableState.value.shadow.data),
                )
            }
        }
    }

    fun updateDevice(transform: (DeviceSnapshot) -> DeviceSnapshot) {
        val deviceId = savedStateHandle.get<String>(DEVICE_ID_KEY) ?: return
        viewModelScope.launch(dispatcher) {
            try {
                repository.updateDevice(deviceId, transform)
                mutableState.value = mutableState.value.copy(operationError = null)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(operationError = error)
            }
        }
    }

    fun setQuickUnlock(enabled: Boolean) {
        viewModelScope.launch(dispatcher) {
            repository.updatePreferences { it.copy(quickUnlock = enabled) }
        }
    }

    fun setBiometricUnlock(enabled: Boolean) {
        viewModelScope.launch(dispatcher) {
            repository.updatePreferences { it.copy(biometricUnlock = enabled) }
        }
    }

    fun syncTime() {
        val deviceId = savedStateHandle.get<String>(DEVICE_ID_KEY) ?: return
        viewModelScope.launch(dispatcher) {
            val online = mutableState.value.device?.presence == DevicePresence.ONLINE
            if (!online) {
                mutableState.value = mutableState.value.copy(timeSyncSucceeded = false)
                return@launch
            }
            try {
                val stamp = System.currentTimeMillis().toString()
                repository.updateDevice(deviceId) { it.copy(metadata = it.metadata + ("timeSyncAt" to stamp)) }
                mutableState.value = mutableState.value.copy(timeSyncSucceeded = true, operationError = null)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(timeSyncSucceeded = false, operationError = error)
            }
        }
    }

    fun consumeTimeSyncResult() {
        mutableState.value = mutableState.value.copy(timeSyncSucceeded = null)
    }

    fun renameDevice(name: String) {
        val deviceId = savedStateHandle.get<String>(DEVICE_ID_KEY) ?: return
        viewModelScope.launch(dispatcher) {
            try {
                repository.renameDevice(deviceId, name)
                mutableState.value = mutableState.value.copy(deviceInfoSaved = true, operationError = null)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(operationError = error)
            }
        }
    }

    fun consumeDeviceInfoSaved() {
        mutableState.value = mutableState.value.copy(deviceInfoSaved = false)
    }

    fun deleteDevice() {
        val deviceId = savedStateHandle.get<String>(DEVICE_ID_KEY) ?: return
        viewModelScope.launch(dispatcher) {
            try {
                repository.deleteDevice(deviceId)
                mutableState.value = mutableState.value.copy(deletedDeviceId = deviceId, operationError = null)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(operationError = error)
            }
        }
    }

    fun consumeDeletedDevice() {
        mutableState.value = mutableState.value.copy(deletedDeviceId = null)
    }

    fun checkFirmware() {
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(queryingFirmware = true)
            delay(300)
            mutableState.value = mutableState.value.copy(queryingFirmware = false)
        }
    }

    companion object {
        private const val DEVICE_ID_KEY = "device_detail_id"
    }
}
