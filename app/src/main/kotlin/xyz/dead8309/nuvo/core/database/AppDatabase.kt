package xyz.dead8309.nuvo.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.database.model.ChatMessageEntity
import xyz.dead8309.nuvo.core.database.model.ChatSessionEntity
import xyz.dead8309.nuvo.core.database.model.InstantConverter
import xyz.dead8309.nuvo.core.database.model.McpServerEntity
import xyz.dead8309.nuvo.core.database.model.McpServerHeadersConverter
import xyz.dead8309.nuvo.core.database.model.ToolCallListConverter
import xyz.dead8309.nuvo.core.database.model.ToolResultConverter

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        McpServerEntity::class
    ],
    version = 2,
    exportSchema = true,
    // TODO: Ill reset the database when we have a proper working app
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(
    InstantConverter::class,
    ToolCallListConverter::class,
    ToolResultConverter::class,
    McpServerHeadersConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun mcpServerDao(): McpServerDao
}