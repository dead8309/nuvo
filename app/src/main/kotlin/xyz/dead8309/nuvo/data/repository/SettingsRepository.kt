package xyz.dead8309.nuvo.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.core.model.McpServer

interface SettingsRepository {
    val appSettingsFlow: Flow<AppSettings>

    /** Sets the OpenAI API key. Null or blank will clear the key. */
    suspend fun setOpenaiAPIKey(apiKey: String?)

    /** Flow of all MCP servers sorted by name. */
    suspend fun getAllMcpServers(): Flow<List<McpServer>>

    /**
     * Saves (inserts or updates) an MCP server configuration.
     * The `config.id` should be unique for identification.
     */
    suspend fun saveMcpSever(config: McpServer)

    /**
     * Enables or disables a specific MCP server configuration.
     */
    suspend fun setActiveMcpServer(id: Long, enabled: Boolean)

    /**
     * Deletes an MCP server configuration by its unique ID.
     */
    suspend fun deleteMcpServer(id: Long)
}