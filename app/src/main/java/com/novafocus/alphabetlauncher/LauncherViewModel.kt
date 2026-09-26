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
    private val _personalization = MutableStateFlow(repository.personalization())
    val personalization: StateFlow<LauncherPersonalization> = _personalization.asStateFlow()

    init { refresh() }

    fun refresh(showLoading: Boolean = true) {
        if (showLoading) _loadState.value = LoadState.Loading
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { _loadState.value = LoadState.Ready(it) }
                .onFailure {
                    if (showLoading || _loadState.value !is LoadState.Ready) {
                        _loadState.value = LoadState.Failed("Couldn't load apps")
                    }
                }
        }
    }

    fun launch(app: LaunchableApp, onFailure: () -> Unit) {
        if (repository.launch(app).isSuccess) {
            _personalization.value = repository.recordLaunch(app)
        } else {
            onFailure()
        }
    }

    fun setFavourite(app: LaunchableApp, favourite: Boolean) {
        _personalization.value = repository.setFavourite(app, favourite)
    }
}
