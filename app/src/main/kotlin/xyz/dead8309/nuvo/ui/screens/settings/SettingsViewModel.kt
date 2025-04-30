package xyz.dead8309.nuvo.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.api
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _openApiKeyInput = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = settingsRepository.appSettingsFlow
        .combine(_openApiKeyInput) { settings, input ->
            val displayValue = input ?: settings.openaiApiKey ?: ""

            if (input == null && settings.openaiApiKey != null) {
                _openApiKeyInput.value = settings.openaiApiKey
            }

            SettingsUiState(
                openAiApiKey = displayValue
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState()
        )

    fun updateOpenAiApiKey(apiKey: String) {
        _openApiKeyInput.value = apiKey
        val keyToSave = apiKey.trim().ifBlank { null }
        viewModelScope.launch {
            settingsRepository.setOpenaiAPIKey(keyToSave)
            Log.d("SettingsViewModel", "OpenAI API key updated to: $keyToSave")
        }
    }
}