package xyz.dead8309.nuvo.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.datastore.proto.AppSettingsProto
import xyz.dead8309.nuvo.datastore.proto.copy
import javax.inject.Inject

class PreferenceDataStore @Inject constructor(
    private val appSettingsDataStore: DataStore<AppSettingsProto>
){
    val appSettingsFlow = appSettingsDataStore.data
        .map {
            AppSettings(
                openaiApiKey = it.openaiApiKey.ifEmpty { null }
            )
        }

    suspend fun setOpenaiAPIKey(apiKey: String?) {
        try {
            appSettingsDataStore.updateData { p ->
                val newPrefs = p.copy {
                    this.openaiApiKey = apiKey ?: ""
                }
                newPrefs
            }
            Log.d("PreferenceDataStore", "OpenAI API key updated to: $apiKey")
        } catch (e: Exception) {
            Log.e("PreferenceDataStore", "Error setting OpenAI API key", e)
        }
    }
}