package xyz.dead8309.nuvo.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "created_time")
    val createdTime: Instant,
    @ColumnInfo(name = "last_modified_time")
    val lastModifiedTime: Instant,
)