package xyz.dead8309.nuvo.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject

private const val TAG = "AuthStateManagerImpl"
private const val AUTH_STATE_PREFS_FILENAME = "oauth_secure_prefs"
private const val KEY_AUTH_STATE_PREFIX = "auth_state_"

class AuthStateManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthStateManager {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val secureAuthStatePrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                AUTH_STATE_PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secure shared preferences", e)
            throw RuntimeException("Failed to create secure shared preferences", e)
        }
    }

    override suspend fun getAuthState(serverId: Long): AuthState? = withContext(ioDispatcher) {
        val key = "$KEY_AUTH_STATE_PREFIX$serverId"
        val jsonString = secureAuthStatePrefs.getString(key, null)
        if (jsonString == null) {
            Log.d(TAG, "No AuthState found for server ID: $serverId")
            return@withContext null
        }

        return@withContext try {
            AuthState.jsonDeserialize(jsonString)
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Failed to deserialize AuthState for server ID: $serverId", e)
            clearAuthState(serverId)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while deserializing AuthState for server ID: $serverId", e)
            null
        }
    }

    override suspend fun saveAuthState(serverId: Long, authState: AuthState) {
        val key = "$KEY_AUTH_STATE_PREFIX$serverId"
        val jsonString = authState.jsonSerializeString()
        secureAuthStatePrefs.edit().putString(key, jsonString).apply()
        Log.d(
            TAG,
            "Saved AuthState for server ID: $serverId. Needs refresh: ${authState.needsTokenRefresh}"
        )
    }

    override suspend fun clearAuthState(serverId: Long) {
        val key = "$KEY_AUTH_STATE_PREFIX$serverId"
        secureAuthStatePrefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared AuthState for server ID: $serverId")
    }
}