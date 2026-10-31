package dev.nacho.wibox.ui

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import dev.nacho.wibox.data.WiboxRepository

class RepositoryViewModelFactory<T : ViewModel>(
    private val repository: WiboxRepository,
    private val creator: (WiboxRepository, SavedStateHandle) -> T,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return creator(repository, extras.createSavedStateHandle()) as T
    }
}

fun <T : ViewModel> WiboxRepository.factoryFor(
    creator: (WiboxRepository, SavedStateHandle) -> T,
): ViewModelProvider.Factory = RepositoryViewModelFactory(this, creator)
