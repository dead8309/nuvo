package xyz.dead8309.nuvo.di

import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutorImpl
import xyz.dead8309.nuvo.data.remote.mcp.client.McpConnectionManager
import xyz.dead8309.nuvo.data.remote.mcp.client.McpConnectionManagerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class McpModuleBinder {
    @Binds
    @Singleton
    abstract fun bindMcpConnectionManager(impl: McpConnectionManagerImpl): McpConnectionManager

    @Binds
    abstract fun bindMcpToolExecutor(impl: McpToolExecutorImpl): McpToolExecutor
}

@Module
@InstallIn(SingletonComponent::class)
object McpClientModuleProvider {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(SSE)
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.v("MCPHttpClient", message)
                    }
                }
                level = LogLevel.HEADERS
            }
            engine {
                requestTimeout = 60_000
            }
        }
    }
}