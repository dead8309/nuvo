package xyz.dead8309.nuvo.ui.screens.settings

import androidx.compose.runtime.Immutable
import xyz.dead8309.nuvo.core.model.McpServer

@Immutable
data class SettingsUiState(
    val openAiApiKey: String = "",
    val mcpServers: List<McpServer> = emptyList(),
    val userMessage: String? = null,
)