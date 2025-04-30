package xyz.dead8309.nuvo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.dead8309.nuvo.data.repository.ChatRepository
import xyz.dead8309.nuvo.data.repository.ChatRepositoryImpl
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import xyz.dead8309.nuvo.data.repository.SettingsRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
}