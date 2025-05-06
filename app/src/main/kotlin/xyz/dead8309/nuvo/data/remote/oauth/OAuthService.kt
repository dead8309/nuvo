package xyz.dead8309.nuvo.data.remote.oauth

import xyz.dead8309.nuvo.core.model.AuthorizationServerMetadata
import xyz.dead8309.nuvo.core.model.ClientRegistrationRequest
import xyz.dead8309.nuvo.core.model.ClientRegistrationResponse

/**
 * OAuth 2.0 Authorization Service
 */
interface OAuthService {
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

    companion object {
        const val REDIRECT_URI = "xyz.dead8309.nuvo:/callback"
    }
}