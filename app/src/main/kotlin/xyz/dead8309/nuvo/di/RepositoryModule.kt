package xyz.dead8309.nuvo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.dead8309.nuvo.data.remote.oauth.OAuthService
import xyz.dead8309.nuvo.data.remote.oauth.OAuthServiceImpl
import xyz.dead8309.nuvo.data.repository.AuthStateManager
import xyz.dead8309.nuvo.data.repository.AuthStateManagerImpl
import xyz.dead8309.nuvo.data.repository.ChatRepository
import xyz.dead8309.nuvo.data.repository.ChatRepositoryImpl
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import xyz.dead8309.nuvo.data.repository.SettingsRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAuthorizationService(
        impl: OAuthServiceImpl
    ): OAuthService

    @Binds
    @Singleton
    abstract fun bindAuthStateManager(
        impl: AuthStateManagerImpl
    ): AuthStateManager
}