package xyz.dead8309.nuvo.ui.screens.settings

import androidx.compose.runtime.Immutable
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.remote.mcp.client.ConnectionState

@Immutable
data class SettingsUiState(
    val openAiApiKey: String = "",
    val mcpServers: List<McpServer> = emptyList(),
    val userMessage: String? = null,
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val serverTools: Map<Long, List<McpToolEntity>> = emptyMap()
)