package xyz.dead8309.nuvo.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import xyz.dead8309.nuvo.core.database.AppDatabase
import xyz.dead8309.nuvo.core.database.MIGRATION_2_3
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.database.model.InstantConverter
import xyz.dead8309.nuvo.core.database.model.McpServerAuthStatusConverter
import xyz.dead8309.nuvo.core.database.model.McpServerHeadersConverter
import xyz.dead8309.nuvo.core.database.model.ToolCallListConverter
import xyz.dead8309.nuvo.core.database.model.ToolResultConverter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesNetworkJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults =true
    }

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
        instantConverter: InstantConverter,
        toolCallListConverter: ToolCallListConverter,
        toolResultConverter: ToolResultConverter,
        mcpServerHeadersConverter: McpServerHeadersConverter,
        mcpServerAuthStatusConverter: McpServerAuthStatusConverter
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "nuvo_database"
    )
        .addMigrations(MIGRATION_2_3)
        .addTypeConverter(instantConverter)
        .addTypeConverter(toolCallListConverter)
        .addTypeConverter(toolResultConverter)
        .addTypeConverter(mcpServerHeadersConverter)
        .addTypeConverter(mcpServerAuthStatusConverter)
        .build()

    @Provides
    fun providesChatSessionDao(
        appDatabase: AppDatabase
    ): ChatSessionDao = appDatabase.chatSessionDao()

    @Provides
    fun providesChatMessageDao(
        appDatabase: AppDatabase
    ): ChatMessageDao = appDatabase.chatMessageDao()

    @Provides
    fun providesMcpServerDao(
        appDatabase: AppDatabase
    ): McpServerDao = appDatabase.mcpServerDao()
}
