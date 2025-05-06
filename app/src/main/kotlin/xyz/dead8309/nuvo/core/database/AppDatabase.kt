package xyz.dead8309.nuvo.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.database.model.ChatMessageEntity
import xyz.dead8309.nuvo.core.database.model.ChatSessionEntity
import xyz.dead8309.nuvo.core.database.model.InstantConverter
import xyz.dead8309.nuvo.core.database.model.McpServerAuthStatusConverter
import xyz.dead8309.nuvo.core.database.model.McpServerEntity
import xyz.dead8309.nuvo.core.database.model.McpServerHeadersConverter
import xyz.dead8309.nuvo.core.database.model.ToolCallListConverter
import xyz.dead8309.nuvo.core.database.model.ToolResultConverter
import xyz.dead8309.nuvo.core.model.AuthStatus

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        McpServerEntity::class
    ],
    version = 3,
    exportSchema = true,
    // TODO: Ill reset the database when we have a proper working app
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ]
)
@TypeConverters(
    InstantConverter::class,
    ToolCallListConverter::class,
    ToolResultConverter::class,
    McpServerHeadersConverter::class,
    McpServerAuthStatusConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun mcpServerDao(): McpServerDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE mcp_servers ADD COLUMN requires_auth INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE mcp_servers ADD COLUMN auth_status TEXT NOT NULL DEFAULT '${AuthStatus.NOT_CHECKED.name}'")
        db.execSQL("ALTER TABLE mcp_servers ADD COLUMN auth_server_metadata_url TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE mcp_servers ADD COLUMN oauth_client_id TEXT DEFAULT NULL")
    }
}
