package xyz.dead8309.nuvo.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.datastore.PreferenceDataStore
import xyz.dead8309.nuvo.core.model.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferenceDataStore: PreferenceDataStore
) : SettingsRepository {
    override val appSettingsFlow: Flow<AppSettings> = preferenceDataStore.appSettingsFlow

    override suspend fun setOpenaiAPIKey(apiKey: String?) {
        preferenceDataStore.setOpenaiAPIKey(apiKey)
    }
}