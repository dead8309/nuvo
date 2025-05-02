package xyz.dead8309.nuvo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor
import xyz.dead8309.nuvo.data.remote.openai.OpenAIService
import xyz.dead8309.nuvo.data.remote.openai.OpenAIServiceImpl
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object OpenAIModule {

    @Provides
    @Singleton
    fun providesOpenAIService(
        settingsRepository: SettingsRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        mcpToolExecutor: McpToolExecutor
    ): OpenAIService {
        return OpenAIServiceImpl(settingsRepository, mcpToolExecutor, dispatcher)
    }
}