package xyz.dead8309.nuvo.ui.screens.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    val openAiApiKey: String = "",
    val userMessage: String? = null
)

