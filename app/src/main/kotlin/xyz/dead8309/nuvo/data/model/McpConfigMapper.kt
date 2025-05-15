package xyz.dead8309.nuvo.data.model

import xyz.dead8309.nuvo.core.database.model.McpServerEntity
import xyz.dead8309.nuvo.core.model.McpServer

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
        version = this.version ?: currentEntity?.version
    )
}