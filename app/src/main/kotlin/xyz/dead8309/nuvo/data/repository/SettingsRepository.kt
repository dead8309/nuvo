package xyz.dead8309.nuvo.data.repository

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.flow.Flow
import net.openid.appauth.AuthState
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.core.model.PersistedOAuthDetails
import xyz.dead8309.nuvo.data.model.AuthRequestDetails

interface SettingsRepository {
    val appSettingsFlow: Flow<AppSettings>

    /** Sets the OpenAI API key. Null or blank will clear the key. */
    suspend fun setOpenaiAPIKey(apiKey: String?)

    /** Flow of all MCP servers sorted by name. */
    fun getAllMcpServers(): Flow<List<McpServer>>

    /** Gets a MCP server by its unique ID. */
    suspend fun getMcpServer(id: Long): McpServer?

    /**
     * Saves (inserts or updates) an MCP server configuration.
     * The `config.id` should be unique for identification.
     */
    suspend fun saveMcpSever(config: McpServer): Long

    /**
     * Enables or disables a specific MCP server configuration.
     */
    suspend fun setActiveMcpServer(id: Long, enabled: Boolean)

    /**
     * Deletes an MCP server configuration by its unique ID.
     */
    suspend fun deleteMcpServer(id: Long)

    /**
     * Updates the tools for a specific server.
     */
    suspend fun updateToolsForServer(serverId: Long, tools: List<Tool>): Result<Unit>

    /**
     * Clears locally stored tools for a server.
     */
    suspend fun clearToolsForServer(serverId: Long)

    /**
     * Updates server information.
     */
    suspend fun updateServerInfo(serverId: Long, serverVersion: String?)

    /**
     * Gets a flow of all tools for a specific server, for display in settings.
     * (We'll return McpToolEntity for now, could map to a domain model later)
     */
    fun getToolsForServerSettings(serverId: Long): Flow<List<McpToolEntity>>

    /**
     * Enables or disables a specific tool.
     */
    suspend fun setToolEnabled(toolId: Long, isEnabled: Boolean)

    /**
     * Saves discovered Authorization Server metadata URL.
     */
    suspend fun saveAuthorizationServerMetadataUrl(serverId: Long, url: String?)

    /**
     * Saves the OAuth client ID obtained via dynamic registration
     */
    suspend fun saveOAuthClientId(serverId: Long, clientId: String)

    /**
     * Saves the initial AuthState for a server.
     */
    suspend fun saveInitialAuthState(serverId: Long, authState: AuthState)

    /**
     * Updates the AuthState with the token response from AppAuth
     */
    suspend fun updateAuthStateWithTokenResponse(
        serverId: Long,
        tokenResponse: net.openid.appauth.TokenResponse
    )

    /**
     * Retrieves the persisted [net.openid.appauth.AuthState] for a server.
     */
    suspend fun getAuthState(serverId: Long): AuthState?

    /**
     * Retrieves the OAuth details for a server
     */
    suspend fun getPersistedOAuthDetails(serverId: Long): PersistedOAuthDetails?

    /**
     * Clears all OAuth tokens and credentials for a server
     */
    suspend fun clearOAuthDetails(serverId: Long)

    /**
     * Updates the authorization status of a server
     */
    suspend fun updateAuthStatus(serverId: Long, status: AuthStatus)

    /**
     * Fetches the necessary details from the Authorization Server to build an
     * Authorization Request using AppAuth. Performs discovery and registration if needed.
     *
     * @param serverId The ID of the MCP server.
     * @return A [Result] containing AuthRequestDetails on success, or an Exception on failure.
     */
    suspend fun getAuthorizationRequestDetails(serverId: Long): Result<AuthRequestDetails>

    /**
     * Updates the 'requiresAuth' flag for a server, usually based on discovery
     */
    suspend fun setRequiresAuth(serverId: Long, requiresAuth: Boolean)

    /**
     * Performs the initial discovery step for a server to check if auth is needed and get metadata URL
     */
    suspend fun performInitialAuthDiscovery(serverId: Long): Result<AuthStatus>
}

