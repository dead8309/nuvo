package xyz.dead8309.nuvo.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.model.AppSettings

interface SettingsRepository {
    val appSettingsFlow: Flow<AppSettings>
    suspend fun setOpenaiAPIKey(apiKey: String?)
    // TODO: add more settings
}