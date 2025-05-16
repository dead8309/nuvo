package xyz.dead8309.nuvo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.database.dao.McpToolDao
import xyz.dead8309.nuvo.core.database.entities.ChatMessageEntity
import xyz.dead8309.nuvo.core.database.entities.ChatSessionEntity
import xyz.dead8309.nuvo.core.database.entities.McpServerEntity
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        McpServerEntity::class,
        McpToolEntity::class
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    DatabaseConverters::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun mcpToolDao(): McpToolDao
}