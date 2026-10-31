package dev.nacho.wibox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class GalleryUiState(
    val media: LoadState<List<MediaUi>> = LoadState.Loading(),
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
    val currentMediaId: String? = null,
    val videoPositionSeconds: Float = 0f,
    val toolsVisible: Boolean = true,
)

class GalleryViewModel(
    private val repository: WiboxRepository,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        GalleryUiState(
            editing = savedStateHandle[EDITING_KEY] ?: false,
            selected = savedStateHandle.get<ArrayList<String>>(SELECTED_KEY)?.toSet().orEmpty(),
            currentMediaId = savedStateHandle[CURRENT_MEDIA_KEY],
            videoPositionSeconds = savedStateHandle[VIDEO_POSITION_KEY] ?: 0f,
            toolsVisible = savedStateHandle[TOOLS_VISIBLE_KEY] ?: true,
        ),
    )
    val state: StateFlow<GalleryUiState> = mutableState.asStateFlow()

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
                    media = LoadState.Error(error, mutableState.value.media.data),
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
        val selected = if (id in mutableState.value.selected) mutableState.value.selected - id else mutableState.value.selected + id
        savedStateHandle[SELECTED_KEY] = ArrayList(selected)
        mutableState.value = mutableState.value.copy(selected = selected)
    }

    fun selectAll() {
        val selected = mutableState.value.media.data.orEmpty().mapTo(mutableSetOf()) { it.id }
        savedStateHandle[SELECTED_KEY] = ArrayList(selected)
        mutableState.value = mutableState.value.copy(selected = selected)
    }

    fun deleteSelected() {
        val selected = mutableState.value.selected
        if (selected.isEmpty()) return
        viewModelScope.launch(dispatcher) {
            repository.deleteMedia(selected)
            savedStateHandle[SELECTED_KEY] = ArrayList<String>()
            savedStateHandle[EDITING_KEY] = false
            mutableState.value = mutableState.value.copy(editing = false, selected = emptySet())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(dispatcher) {
            repository.deleteMedia(setOf(id))
        }
    }

    fun setCurrentMedia(id: String) {
        savedStateHandle[CURRENT_MEDIA_KEY] = id
        mutableState.value = mutableState.value.copy(currentMediaId = id)
    }

    fun setVideoPosition(seconds: Float) {
        savedStateHandle[VIDEO_POSITION_KEY] = seconds
        mutableState.value = mutableState.value.copy(videoPositionSeconds = seconds)
    }

    fun setToolsVisible(visible: Boolean) {
        savedStateHandle[TOOLS_VISIBLE_KEY] = visible
        mutableState.value = mutableState.value.copy(toolsVisible = visible)
    }

    private fun renderSnapshot(snapshot: RepositorySnapshot) {
        val previous = mutableState.value.media.data
        val mapped = snapshot.media.map(UiMapper::media)
        val updated = mapped.maxOfOrNull { it.timestampMillis }
        mutableState.value = mutableState.value.copy(
            media = when (snapshot.preferences.scenario) {
                AppScenario.LOADING -> LoadState.Loading(previous, refreshing = previous != null, lastUpdated = updated)
                AppScenario.EMPTY -> LoadState.Content(emptyList(), lastUpdated = updated)
                AppScenario.ERROR -> LoadState.Error(
                    error = ProviderError.Network("renderMedia"),
                    data = previous ?: mapped,
                    lastUpdated = updated,
                )
                AppScenario.OFFLINE -> LoadState.Content(mapped, stale = true, lastUpdated = updated)
                else -> LoadState.Content(mapped, lastUpdated = updated)
            },
        )
    }

    companion object {
        private const val EDITING_KEY = "gallery_editing"
        private const val SELECTED_KEY = "gallery_selected"
        private const val CURRENT_MEDIA_KEY = "gallery_current_media"
        private const val VIDEO_POSITION_KEY = "gallery_video_position"
        private const val TOOLS_VISIBLE_KEY = "gallery_tools_visible"
    }
}
