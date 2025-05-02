package xyz.dead8309.nuvo.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _openApiKeyInput = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.appSettingsFlow,
        settingsRepository.getAllMcpServers(),
        _openApiKeyInput
    ) { settings, mcpServers, currentApiKeyInput ->
        val displayValue = currentApiKeyInput ?: settings.openaiApiKey ?: ""

        if (currentApiKeyInput == null && settings.openaiApiKey != null) {
            _openApiKeyInput.value = settings.openaiApiKey
        }

        SettingsUiState(
            openAiApiKey = displayValue,
            mcpServers = mcpServers
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10.seconds),
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

    fun addOrUpdateMcpServer(config: McpServer) {
        viewModelScope.launch {
            settingsRepository.saveMcpSever(config)
            // TODO: show snackbar
        }
    }

    fun deleteMcpServer(id: Long) {
        viewModelScope.launch {
            settingsRepository.deleteMcpServer(id)
            // TODO: show snackbar
        }
    }

    fun setMcpServerEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setActiveMcpServer(id, enabled)
        }
    }
}