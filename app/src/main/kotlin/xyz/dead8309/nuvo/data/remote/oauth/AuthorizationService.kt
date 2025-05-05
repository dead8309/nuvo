package xyz.dead8309.nuvo.data.remote.oauth

import xyz.dead8309.nuvo.core.model.AuthorizationServerMetadata
import xyz.dead8309.nuvo.core.model.ClientRegistrationRequest
import xyz.dead8309.nuvo.core.model.ClientRegistrationResponse
import xyz.dead8309.nuvo.core.model.TokenResponse

/**
 * OAuth 2.0 Authorization Service
 */
interface AuthorizationService {
    /**
     * Fetches metadata from the MCP Server's well-known endpoint
     */
//    suspend fun getResourceServerMetadata(resourceUrl: String): Result<ResourceServerMetadata>

    /**
     * Fetches the authorization URL for the given resource URL
     */
    suspend fun getAuthorizationServerMetadata(metadataUrl: String): Result<AuthorizationServerMetadata>

    /**
     * Performs Dynamic client registration
     */
    suspend fun registerClient(
        registrationEndpoint: String,
        request: ClientRegistrationRequest
    ): Result<ClientRegistrationResponse>


    /**
     * Exchanges an authorization code for an access token using PKCE
     */
    suspend fun exchangeCodeForToken(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        code: String,
        redirectUri: String,
        codeVerifier: String // PKCE
    ): Result<TokenResponse>

    /**
     * Refreshes an access token using a refresh token
     */
    suspend fun refreshAccessToken(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        refreshToken: String
    ): Result<TokenResponse>

    companion object {
        const val REDIRECT_URI = "xyz.dead8309.nuvo:/callback"
    }
}