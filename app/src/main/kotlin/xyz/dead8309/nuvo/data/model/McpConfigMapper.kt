package xyz.dead8309.nuvo.data.model

import xyz.dead8309.nuvo.core.database.model.McpServerEntity
import xyz.dead8309.nuvo.core.model.McpServer

fun McpServerEntity.asDomainModel(): McpServer {
    return McpServer(
        id = this.id,
        name = this.name,
        url = this.url,
        headers = this.headers,
        enabled = this.enabled
    )
}

fun McpServer.asEntity(): McpServerEntity {
    return McpServerEntity(
        name = this.name,
        url = this.url,
        headers = this.headers,
        enabled = this.enabled
    )
}