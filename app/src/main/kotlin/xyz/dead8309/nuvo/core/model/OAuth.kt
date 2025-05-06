package xyz.dead8309.nuvo.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.dead8309.nuvo.BuildConfig

// RFC 9278
//@Serializable
//data class ResourceServerMetadata(
//    val resource: String,
//    @SerialName("authorization_servers")
//    val authorizationServers: List<String>
//)

// RFC 8144
@Serializable
data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("registration_endpoint")
    val registrationEndpoint: String? = null,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>,
    @SerialName("response_modes_supported")
    val responseModesSupported: List<String>? = null,
    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("revocation_endpoint")
    val revocationEndpoint: String? = null,
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>? = null,
    // TODO: come back to this
    // maybe not needed
    @SerialName("scopes_supported")
    val scopesSupported: List<String>? = null,
)

// RFC 7591
@Serializable
data class ClientRegistrationRequest(
    @SerialName("redirect_uris")
    val redirectUris: List<String>,
    @SerialName("client_name")
    val clientName: String? = null,
    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String = "none",
    @SerialName("grant_types")
    val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    @SerialName("response_types")
    val responseTypes: List<String> = listOf("code"),
    @SerialName("software_id")
    val softwareId: String? = BuildConfig.APPLICATION_ID,
    @SerialName("software_version")
    val softwareVersion: String? = BuildConfig.VERSION_NAME,
)

// RFC 7591
@Serializable
data class ClientRegistrationResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("redirect_uris")
    val redirectUris: List<String>? = null,
    @SerialName("client_name")
    val clientName: String? = null,
    @SerialName("client_uri")
    val clientUri: String? = null,
    @SerialName("grant_types")
    val grantTypes: List<String> = emptyList(),
    @SerialName("response_types")
    val responseTypes: List<String> = emptyList(),
    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String = "none",
    @SerialName("registration_client_uri")
    val registrationClientUri: String? = null,
    @SerialName("client_id_issued_at")
    val clientIdIssuedAt: Long? = null,

    // TODO: come back to this
    // Are these actually needed?
//    @SerialName("client_secret")
//    val clientSecret: String? = null,
//    @SerialName("client_secret_expires_at")
//    val clientSecretExpiresAt: Long? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Long? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("scope")
    val scope: String? = null,
)

data class PersistedOAuthDetails(
    val clientId: String,
//    val clientSecret: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenExpiryEpochSeconds: Long?,
    val scopes: String?,
)