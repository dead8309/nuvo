package xyz.dead8309.nuvo.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.ClientRegistrationRequest
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.core.model.PersistedOAuthDetails
import xyz.dead8309.nuvo.core.model.TokenResponse

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
     * Saves discovered Authorization Server metadata URL.
     */
    suspend fun saveAuthorizationServerMetadataUrl(serverId: Long, url: String?)

    /**
     * Saves the OAuth client ID obtained via dynamic registration
     */
    suspend fun saveOAuthClientId(serverId: Long, clientId: String)

    /**
     * Saves the OAuth client secret
     */
    suspend fun saveOAuthClientSecret(serverId: Long, clientSecret: String?)

    /**
     * Saves the obtained OAuth tokens (access and refresh tokens stored securely)
     */
    suspend fun saveOAuthTokens(serverId: Long, tokenResponse: TokenResponse)

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
     * Attempts to get a valid access token, refreshing if necessary
     */
    suspend fun getValidAccessToken(serverId: Long): String?

    // TODO: REMOVE THIS
//    /**
//     * Initiates the OAuth flow.
//     * 1. Fetches AS metadata
//     * 2. Performs dynamic registration if needed
//     * 3. Generates PKCE values & state
//     * 4. Stores temp PKCE/state values
//     * 5. Returns the authorization URL to be opened in the browser
//     * Returns [Result.failure] if any step fails
//     */
//    suspend fun initiateAuthorization(
//        serverId: Long,
//        redirectUri: String,
//        clientRegistrationRequest: ClientRegistrationRequest? = null
//    ): Result<String>
//
//    /**
//     * Handles the callback after user authorization:
//     * 1. Verifies the state parameter
//     * 2. Retrieves the stored PKCE
//     * 3. Exchanges the authorization code for tokens
//     * 4. Saves the tokens securely
//     * 5. Cleans up temp PKCE/state values
//     * Returns [Result.failure] if any step fails
//     */
//    suspend fun handleAuthorizationCallback(
//        serverId: Long,
//        code: String,
//        receivedState: String,
//        redirectUri: String
//    ): Result<Unit>
//
    /**
     * Fetches the necessary details from the Authorization Server to build an
     * Authorization Request using AppAuth. Performs discovery and registration if needed.
     *
     * @param serverId The ID of the MCP server.
     * @return A [Result] containing AuthRequestDetails on success, or an Exception on failure.
     */
    suspend fun getAuthorizationRequestDetails(serverId: Long): Result<AuthRequestDetails>

    /**
     * Handles the callback after user authorization, exchanging the code for tokens.
     *
     * @param serverId The ID of the MCP server being authorized.
     * @param code The authorization code received from the server.
     * @param codeVerifier The PKCE code verifier used in the initial request.
     * @param redirectUri The redirect URI used in the initial request.
     * @return A [Result] indicating success or failure of the token exchange and storage.
     */
    suspend fun handleAuthorizationCodeExchange(
        serverId: Long,
        code: String,
        codeVerifier: String, // Pass the verifier from AppAuth flow manager
        redirectUri: String,
    ): Result<Unit>


    /**
     * Updates the 'requiresAuth' flag for a server, usually based on discovery
     */
    suspend fun setRequiresAuth(serverId: Long, requiresAuth: Boolean)

    /**
     * Performs the initial discovery step for a server to check if auth is needed and get metadata URL
     */
    suspend fun performInitialAuthDiscovery(serverId: Long): Result<AuthStatus>
}

/**
 * information needed by AppAuth to build an AuthorizationRequest.
 */
data class AuthRequestDetails(
    val authorizationEndpointUri: android.net.Uri,
    val tokenEndpointUri: android.net.Uri,
    val registrationEndpointUri: android.net.Uri?,
    val clientId: String,
    val scopes: List<String>?, // null if not specified/needed
)
