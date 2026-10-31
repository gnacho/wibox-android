package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.PreparationState
import dev.nacho.wibox.core.cloud.ProviderError
import dev.nacho.wibox.data.WiboxRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val providerStatus: ProviderStatusUi,
    val preparing: Boolean = true,
    val ready: Boolean = false,
    val error: ProviderError? = null,
    val selectedTab: MainTab = MainTab.DEVICES,
)

class SessionViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SessionUiState(
            providerStatus = UiMapper.providerStatus(repository.provider.descriptor),
            selectedTab = savedStateHandle.get<String>(SELECTED_TAB_KEY)?.let(MainTab::valueOf) ?: MainTab.DEVICES,
        ),
    )
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    init {
        prepare()
    }

    fun prepare() {
        viewModelScope.launch(dispatcher) {
            mutableState.value = mutableState.value.copy(preparing = true, ready = false, error = null)
            try {
                val preparation = repository.prepare()
                mutableState.value = mutableState.value.copy(
                    preparing = false,
                    ready = preparation == PreparationState.READY,
                    providerStatus = mutableState.value.providerStatus.copy(preparation = preparation),
                )
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(
                    preparing = false,
                    ready = false,
                    providerStatus = mutableState.value.providerStatus.copy(
                        preparation = PreparationState.FAILED,
                        error = error,
                    ),
                    error = error,
                )
            }
        }
    }

    fun selectTab(tab: MainTab) {
        savedStateHandle[SELECTED_TAB_KEY] = tab.name
        mutableState.value = mutableState.value.copy(selectedTab = tab)
    }

    companion object {
        private const val SELECTED_TAB_KEY = "selected_tab"
    }
}
