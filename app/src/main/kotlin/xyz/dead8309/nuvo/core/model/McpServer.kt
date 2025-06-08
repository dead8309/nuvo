package xyz.dead8309.nuvo.core.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Clock
import xyz.dead8309.nuvo.core.database.entities.McpServerEntity

@Immutable
data class McpServer(
    val id: Long = 0,
    val name: String,
    val url: String,
    val headers: Map<String, String>,
    val enabled: Boolean,
    val requiresAuth: Boolean = false,
    val authStatus: AuthStatus = AuthStatus.NOT_CHECKED,
    // Discovered AS metadata URL
    val authorizationServerMetadataUrl: String? = null,
    val version: String? = null,
)

fun McpServerEntity.asDomainModel(): McpServer {
    return McpServer(
        id = this.id,
        name = this.name,
        url = this.url,
        headers = this.headers,
        enabled = this.enabled,
        requiresAuth = this.requiresAuth,
        authStatus = authStatus,
        authorizationServerMetadataUrl = this.authorizationServerMetadataUrl,
        version = this.version
    )
}

fun McpServer.asEntity(currentEntity: McpServerEntity? = null): McpServerEntity {
    return McpServerEntity(
        id = this.id,
        name = this.name,
        url = this.url,
        headers = this.headers,
        enabled = this.enabled,
        requiresAuth = this.requiresAuth,
        authStatus = this.authStatus,
        authorizationServerMetadataUrl = this.authorizationServerMetadataUrl,
        // Only update clientId if it's part of the model being saved,
        oauthClientId = currentEntity?.oauthClientId,
        version = this.version ?: currentEntity?.version,
        createdAt = Clock.System.now()
    )
}