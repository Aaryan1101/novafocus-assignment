package com.novafocus.alphabetlauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val apps: List<LaunchableApp>) : LoadState
    data class Failed(val message: String) : LoadState
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _loadState.value = LoadState.Loading
        viewModelScope.launch {
            _loadState.value = runCatching { repository.load() }
                .fold({ LoadState.Ready(it) }, { LoadState.Failed("Couldn't load apps") })
        }
    }

    fun launch(app: LaunchableApp, onFailure: () -> Unit) {
        if (repository.launch(app).isFailure) onFailure()
    }
}
