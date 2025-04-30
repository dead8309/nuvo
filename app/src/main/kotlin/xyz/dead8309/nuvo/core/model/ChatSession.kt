package xyz.dead8309.nuvo.core.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant
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