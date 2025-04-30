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
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.database.model.InstantConverter
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
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
        instantConverter: InstantConverter,
        toolCallListConverter: ToolCallListConverter,
        toolResultConverter: ToolResultConverter
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "nuvo_database"
    )
        .addTypeConverter(instantConverter)
        .addTypeConverter(toolCallListConverter)
        .addTypeConverter(toolResultConverter)
        .build()

    @Provides
    fun providesChatSessionDao(
        appDatabase: AppDatabase
    ): ChatSessionDao = appDatabase.chatSessionDao()

    @Provides
    fun providesChatMessageDao(
        appDatabase: AppDatabase
    ): ChatMessageDao = appDatabase.chatMessageDao()
}
