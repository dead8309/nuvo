package xyz.dead8309.nuvo.core.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant
import xyz.dead8309.nuvo.core.database.entities.ChatSessionEntity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Immutable
data class ChatSession(
    val id: String = Uuid.random().toHexDashString(),
    val title: String? = null,
    val createdTime: Instant,
    val lastModifiedTime: Instant,
)

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