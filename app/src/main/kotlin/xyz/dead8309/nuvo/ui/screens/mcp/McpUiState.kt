package xyz.dead8309.nuvo.ui.screens.mcp

import androidx.compose.runtime.Immutable
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.remote.mcp.client.ConnectionState

@Immutable
data class McpUiState(
    val mcpServers: List<McpServer> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val serverTools: Map<Long, List<McpToolEntity>> = emptyMap(),
    val userMessage: String? = null
)