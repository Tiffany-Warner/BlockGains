package com.tiffles.blockgains.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Activity-level state: theme settings and app-wide snackbar messages. */
@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, userMessages: UserMessages) : ViewModel() {

    /** Null until settings load, so the first frame doesn't flash the wrong theme choice. */
    val settings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: SharedFlow<UserMessage> = userMessages.messages
}
