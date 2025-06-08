package xyz.dead8309.nuvo.ui.screens.settings

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application as Application) {

    private val _userMessage = MutableStateFlow<String?>(null)
    private val _openApiKeyInput = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.appSettingsFlow,
        _openApiKeyInput,
        _userMessage,
    ) { settings, currentApiKeyInput, message ->
        val displayValue = currentApiKeyInput ?: settings.openaiApiKey ?: ""
        if (currentApiKeyInput == null && settings.openaiApiKey != null) {
            _openApiKeyInput.value = settings.openaiApiKey
        }

        SettingsUiState(
            openAiApiKey = displayValue,
            userMessage = message
        )
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
        initialValue = SettingsUiState()
    )

    fun updateOpenAiApiKey(apiKey: String) {
        _openApiKeyInput.value = apiKey
        val keyToSave = apiKey.trim().ifBlank { null }
        viewModelScope.launch {
            settingsRepository.setOpenaiAPIKey(keyToSave)
            Log.d(TAG, "OpenAI API key updated to: $keyToSave")
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }
}