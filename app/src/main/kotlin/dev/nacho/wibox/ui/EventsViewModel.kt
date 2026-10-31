package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nacho.wibox.core.cloud.EventGapReason
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

data class EventsUiState(
    val events: LoadState<List<EventUi>> = LoadState.Loading(),
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
    val visibleCount: Int = 15,
    val gap: EventGapReason? = null,
)

class EventsViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        EventsUiState(
            editing = savedStateHandle[EDITING_KEY] ?: false,
            selected = savedStateHandle.get<ArrayList<String>>(SELECTED_KEY)?.toSet().orEmpty(),
            visibleCount = savedStateHandle[VISIBLE_COUNT_KEY] ?: 15,
        ),
    )
    val state: StateFlow<EventsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            repository.snapshots.collect(::renderSnapshot)
        }
    }

    fun refresh() {
        viewModelScope.launch(dispatcher) {
            try {
                repository.refreshDevices()
            } catch (error: ProviderError) {
                mutableState.value = mutableState.value.copy(
                    events = LoadState.Error(error, mutableState.value.events.data),
                )
            }
        }
    }

    fun toggleEditing() {
        val editing = !mutableState.value.editing
        savedStateHandle[EDITING_KEY] = editing
        savedStateHandle[SELECTED_KEY] = ArrayList<String>()
        mutableState.value = mutableState.value.copy(editing = editing, selected = emptySet())
    }

    fun toggleSelection(id: String) {
        val selected = if (id in mutableState.value.selected) {
            mutableState.value.selected - id
        } else {
            mutableState.value.selected + id
        }
        savedStateHandle[SELECTED_KEY] = ArrayList(selected)
        mutableState.value = mutableState.value.copy(selected = selected)
    }

    fun selectAll() {
        val selected = mutableState.value.events.data.orEmpty().mapTo(mutableSetOf()) { it.id }
        savedStateHandle[SELECTED_KEY] = ArrayList(selected)
        mutableState.value = mutableState.value.copy(selected = selected)
    }

    fun deleteSelected() {
        val selected = mutableState.value.selected
        if (selected.isEmpty()) return
        viewModelScope.launch(dispatcher) {
            repository.deleteEvents(selected)
            savedStateHandle[SELECTED_KEY] = ArrayList<String>()
            savedStateHandle[EDITING_KEY] = false
            mutableState.value = mutableState.value.copy(editing = false, selected = emptySet())
        }
    }

    fun loadMore() {
        val count = mutableState.value.visibleCount + 15
        savedStateHandle[VISIBLE_COUNT_KEY] = count
        mutableState.value = mutableState.value.copy(visibleCount = count)
    }

    fun markGap(reason: EventGapReason) {
        mutableState.value = mutableState.value.copy(gap = reason)
    }

    fun clearGap() {
        mutableState.value = mutableState.value.copy(gap = null)
    }

    private fun renderSnapshot(snapshot: RepositorySnapshot) {
        val previous = mutableState.value.events.data
        val mapped = snapshot.events.map(UiMapper::event)
        val updated = mapped.maxOfOrNull { it.timestampMillis }
        mutableState.value = mutableState.value.copy(
            events = when (snapshot.preferences.scenario) {
                AppScenario.LOADING -> LoadState.Loading(previous, refreshing = previous != null, lastUpdated = updated)
                AppScenario.EMPTY -> LoadState.Content(emptyList(), lastUpdated = updated)
                AppScenario.ERROR -> LoadState.Error(
                    error = ProviderError.Network("renderEvents"),
                    data = previous ?: mapped,
                    lastUpdated = updated,
                )
                AppScenario.OFFLINE -> LoadState.Content(mapped, stale = true, lastUpdated = updated)
                else -> LoadState.Content(mapped, lastUpdated = updated)
            },
        )
    }

    companion object {
        private const val EDITING_KEY = "events_editing"
        private const val SELECTED_KEY = "events_selected"
        private const val VISIBLE_COUNT_KEY = "events_visible_count"
    }
}
