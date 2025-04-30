package xyz.dead8309.nuvo.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import kotlinx.datetime.Instant
import xyz.dead8309.nuvo.core.model.ChatMessage

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id", "timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "session_id", index = true)
    val sessionId: String,
    @ColumnInfo(name = "role")
    val role: String,
    @ColumnInfo(name = "content")
    val content: String?,
    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String?,
    @ColumnInfo(name = "name")
    val name: String?,
    @ColumnInfo(name = "timestamp", index = true)
    val timestamp: Instant,
    /** JSON string representation of List<ChatMessage.ToolCall>, nullable. Requires ToolCallListConverter. */
    @ColumnInfo(name = "tool_calls_json")
    val toolCallsJson: List<ChatMessage.ToolCall>?,
    /** JSON string representation of ChatMessage.ToolResult, nullable. Requires ToolResultConverter. */
    @ColumnInfo(name = "tool_result_json")
    val toolResultJson: ChatMessage.ToolResult?
)