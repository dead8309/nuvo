package xyz.dead8309.nuvo.data.model

import xyz.dead8309.nuvo.core.database.model.ChatMessageEntity
import xyz.dead8309.nuvo.core.model.ChatMessage

fun ChatMessageEntity.asDomainModel(): ChatMessage {
    val domainRole = try {
        ChatMessage.Role.valueOf(this.role)
    } catch (e: IllegalArgumentException) {
        ChatMessage.Role.USER
    }

    return ChatMessage(
        id = this.id,
        sessionId = this.sessionId,
        role = domainRole,
        content = this.content,
        timestamp = this.timestamp,
        toolCalls = this.toolCallsJson,
        toolCallId = this.toolCallId,
        name = this.name,
        toolResult = this.toolResultJson,
    )
}

fun ChatMessage.asEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = this.id,
        sessionId = this.sessionId,
        role = this.role.name,
        content = this.content,
        timestamp = this.timestamp,
        toolCallsJson = this.toolCalls,
        toolCallId = this.toolCallId,
        name = this.name,
        toolResultJson = this.toolResult,
    )
}