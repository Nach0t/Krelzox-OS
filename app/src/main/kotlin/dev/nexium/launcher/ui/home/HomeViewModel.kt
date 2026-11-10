package dev.nexium.launcher.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nexium.launcher.core.data.AppEntry
import dev.nexium.launcher.core.data.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val apps: List<AppEntry>) : HomeUiState
    data object Error : HomeUiState
}

class HomeViewModel(repository: AppRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeApps()
        .map<List<AppEntry>, HomeUiState> { HomeUiState.Ready(it) }
        .catch { e ->
            Log.e("NexiumHome", "Failed to load apps", e)
            emit(HomeUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    companion object {
        fun factory(repository: AppRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
