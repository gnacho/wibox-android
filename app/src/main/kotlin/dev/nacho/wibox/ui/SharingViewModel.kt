package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.DevicePermission
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

data class SharingUiState(
    val invitations: LoadState<List<InvitationUi>> = LoadState.Loading(),
    val createdInvitationId: String? = null,
    val operationError: ProviderError? = null,
)

class SharingViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SharingUiState())
    val state: StateFlow<SharingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repository.snapshots.collect(::renderSnapshot)
        }
    }

    fun createInvitation(
        deviceId: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                val invitation = repository.createInvitation(deviceId, displayName, permissions)
                mutableState.value = mutableState.value.copy(
                    createdInvitationId = invitation.id,
                    operationError = null,
                )
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(operationError = error)
            }
        }
    }

    fun updateInvitation(
        id: String,
        displayName: String,
        permissions: Set<DevicePermission>,
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                repository.updateInvitation(id, displayName, permissions)
                mutableState.value = mutableState.value.copy(operationError = null)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(operationError = error)
            }
        }
    }

    fun consumeCreatedInvitation() {
        mutableState.value = mutableState.value.copy(createdInvitationId = null)
    }

    fun deleteInvitation(id: String) {
        viewModelScope.launch(dispatcher) {
            try {
                repository.deleteInvitation(id)
                mutableState.value = mutableState.value.copy(operationError = null)
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(operationError = error)
            }
        }
    }

    private fun renderSnapshot(snapshot: RepositorySnapshot) {
        val previous = mutableState.value.invitations.data
        val mapped = snapshot.invitations.map(UiMapper::invitation)
        val updated = mapped.maxOfOrNull { it.createdAtMillis }
        mutableState.value = mutableState.value.copy(
            invitations = when (snapshot.preferences.scenario) {
                AppScenario.LOADING -> LoadState.Loading(previous, refreshing = previous != null, lastUpdated = updated)
                AppScenario.EMPTY -> LoadState.Content(emptyList(), lastUpdated = updated)
                AppScenario.ERROR -> LoadState.Error(
                    error = ProviderError.Network("renderInvitations"),
                    data = previous ?: mapped,
                    lastUpdated = updated,
                )
                AppScenario.OFFLINE -> LoadState.Content(mapped, stale = true, lastUpdated = updated)
                else -> LoadState.Content(mapped, lastUpdated = updated)
            },
        )
    }
}
