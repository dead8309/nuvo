package xyz.dead8309.nuvo.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class McpServer(
    val id: Long,
    val name: String,
    val url: String,
    val headers: Map<String, String>,
    val enabled: Boolean
)