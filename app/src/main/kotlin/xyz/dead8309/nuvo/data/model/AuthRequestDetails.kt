package xyz.dead8309.nuvo.data.model

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
