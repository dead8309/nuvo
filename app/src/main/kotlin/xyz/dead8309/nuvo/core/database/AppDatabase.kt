package xyz.dead8309.nuvo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.database.model.ChatMessageEntity
import xyz.dead8309.nuvo.core.database.model.ChatSessionEntity
import xyz.dead8309.nuvo.core.database.model.InstantConverter
import xyz.dead8309.nuvo.core.database.model.ToolCallListConverter
import xyz.dead8309.nuvo.core.database.model.ToolResultConverter

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(
    InstantConverter::class,
    ToolCallListConverter::class,
    ToolResultConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
}