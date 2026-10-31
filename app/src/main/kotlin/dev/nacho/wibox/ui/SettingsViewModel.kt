package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.AppPreferences
import dev.nacho.wibox.data.AppScenario
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val notice: UserNotice? = null,
    val noticeId: Long = 0,
    val upgradeCheckError: ProviderError? = null,
)

class SettingsViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repository.snapshots.collect { snapshot ->
                mutableState.value = mutableState.value.copy(preferences = snapshot.preferences)
            }
        }
    }

    fun setSound(enabled: Boolean) = updatePreferences { it.copy(sound = enabled) }
    fun setVibration(enabled: Boolean) = updatePreferences { it.copy(vibration = enabled) }
    fun setQuickUnlock(enabled: Boolean) = updatePreferences { it.copy(quickUnlock = enabled) }
    fun setBiometricUnlock(enabled: Boolean) = updatePreferences { it.copy(biometricUnlock = enabled) }
    fun acceptTerms() = updatePreferences { it.copy(termsAccepted = true) }
    fun dismissAndroidHint() = updatePreferences { it.copy(androidHintDismissed = true) }

    fun setScenario(scenario: AppScenario) {
        viewModelScope.launch(dispatcher) {
            repository.setScenario(scenario)
        }
    }

    fun simulateIncomingCall(deviceId: String) {
        viewModelScope.launch(dispatcher) {
            repository.emitIncomingCall(deviceId)
        }
    }

    fun checkAppUpgrade() {
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(upgradeCheckError = null)
            try {
                repository.checkAppUpgrade()
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(upgradeCheckError = error)
            }
        }
    }

    fun resetDemo() {
        viewModelScope.launch(dispatcher) {
            repository.resetDemo()
            showNotice(UserNotice.DEMO_RESET)
        }
    }

    fun showNotice(notice: UserNotice) {
        mutableState.value = mutableState.value.copy(
            notice = notice,
            noticeId = mutableState.value.noticeId + 1,
        )
    }

    fun consumeNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    private fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch(dispatcher) {
            repository.updatePreferences(transform)
        }
    }
}
