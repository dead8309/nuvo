package xyz.dead8309.nuvo.data.model

import xyz.dead8309.nuvo.core.database.model.ChatSessionEntity
import xyz.dead8309.nuvo.core.model.ChatSession

fun ChatSessionEntity.asDomainModel(): ChatSession {
    return ChatSession(
        id = id,
        title = title,
        createdTime = this.createdTime,
        lastModifiedTime = this.lastModifiedTime
    )
}

fun ChatSession.asEntity(): ChatSessionEntity {
    return ChatSessionEntity(
        id = id,
        title = title ?: "Untitled",
        createdTime = this.createdTime,
        lastModifiedTime = this.lastModifiedTime
    )
}