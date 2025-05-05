package xyz.dead8309.nuvo.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import xyz.dead8309.nuvo.BuildConfig
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.datastore.PreferenceDataStore
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.ClientRegistrationRequest
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.core.model.PersistedOAuthDetails
import xyz.dead8309.nuvo.core.model.TokenResponse
import xyz.dead8309.nuvo.data.model.asDomainModel
import xyz.dead8309.nuvo.data.model.asEntity
import xyz.dead8309.nuvo.data.remote.oauth.AuthorizationService
import xyz.dead8309.nuvo.di.IoDispatcher
import xyz.dead8309.nuvo.ui.components.AuthStatusIndicator
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SettingsRepositoryImpl"
const val OAUTH_SECURE_PREFS_FILENAME = "oauth_secure_prefs"
const val KEY_CLIENT_SECRET_PREFIX = "client_secret_"
const val KEY_ACCESS_TOKEN_PREFIX = "access_token_"
const val KEY_REFRESH_TOKEN_PREFIX = "refresh_token_"
const val KEY_TOKEN_EXPIRY_PREFIX = "token_expiry_"
const val KEY_SCOPES_PREFIX = "scopes_"

private const val TOKEN_EXPIRY_BUFFER_TIME = 60

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferenceDataStore: PreferenceDataStore,
    private val mcpServerDao: McpServerDao,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val authorizationService: AuthorizationService
) : SettingsRepository {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePreferences: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                OAUTH_SECURE_PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw RuntimeException("Could not initialize encrypted preferences", e)
        }
    }

    override val appSettingsFlow: Flow<AppSettings> =
        preferenceDataStore.appSettingsFlow.distinctUntilChanged()

    override suspend fun setOpenaiAPIKey(apiKey: String?) {
        preferenceDataStore.setOpenaiAPIKey(apiKey)
    }

    override fun getAllMcpServers(): Flow<List<McpServer>> {
        return mcpServerDao.getAllServers()
            .map { entities -> entities.map { it.asDomainModel() } }
            .distinctUntilChanged()
    }

    override suspend fun getMcpServer(id: Long): McpServer? = withContext(ioDispatcher) {
        mcpServerDao.getServerById(id)?.asDomainModel()
    }

    override suspend fun saveMcpSever(config: McpServer): Long = withContext(ioDispatcher) {
        val currentEntity = mcpServerDao.getServerById(config.id)
        val entityToSave = mcpServerDao.upsertServer(config.asEntity(currentEntity))
        return@withContext entityToSave
    }

    override suspend fun setActiveMcpServer(id: Long, enabled: Boolean) =
        withContext(ioDispatcher) {
            mcpServerDao.setServerEnabled(id, enabled)
            if (!enabled) {
                // reset auth state when disabled
                updateAuthStatus(
                    id,
                    AuthStatus.NOT_CHECKED
                )
            }
        }

    override suspend fun deleteMcpServer(id: Long): Unit = withContext(ioDispatcher) {
        mcpServerDao.deleteServer(id)
        clearOAuthDetails(id)
        Log.d(TAG, "Deleted McpServer $id and oauth details")
    }

    override suspend fun saveAuthorizationServerMetadataUrl(serverId: Long, url: String?): Unit =
        withContext(ioDispatcher) {
            val server = mcpServerDao.getServerById(serverId)
            if (server != null) {
                if (server.authorizationServerMetadataUrl != url) {
                    mcpServerDao.upsertServer(server.copy(authorizationServerMetadataUrl = url))
                    Log.d(TAG, "Updated AS metadata URL for server $serverId to $url")
                }
            } else {
                Log.w(TAG, "Server not found to save metadata URL: $serverId")
            }
        }

    override suspend fun saveOAuthClientId(serverId: Long, clientId: String): Unit =
        withContext(ioDispatcher) {
            val server = mcpServerDao.getServerById(serverId)
            if (server != null) {
                if (server.oauthClientId != clientId) {
                    mcpServerDao.upsertServer(server.copy(oauthClientId = clientId))
                    Log.d(TAG, "Saved client ID for server $serverId")
                }
            } else {
                Log.w(TAG, "Server not found to save client ID: $serverId")
            }
        }

    override suspend fun saveOAuthClientSecret(serverId: Long, clientSecret: String?) =
        withContext(ioDispatcher) {
            securePreferences.edit {
                val key = "${KEY_CLIENT_SECRET_PREFIX}$serverId"
                if (clientSecret != null) {
                    putString(key, clientSecret)
                    Log.d(TAG, "Saved client secret for server $serverId")
                } else {
                    remove(key)
                    Log.d(TAG, "Removed client secret for server $serverId")
                }
            }
        }

    override suspend fun saveOAuthTokens(serverId: Long, tokenResponse: TokenResponse) =
        withContext(ioDispatcher) {
            val expiryTimeSeconds =
                tokenResponse.expiresIn?.let { Clock.System.now().epochSeconds + it - TOKEN_EXPIRY_BUFFER_TIME }
            securePreferences.edit {
                putString("${KEY_ACCESS_TOKEN_PREFIX}$serverId", tokenResponse.accessToken)
                if (tokenResponse.refreshToken != null) {
                    putString(
                        "${KEY_REFRESH_TOKEN_PREFIX}$serverId",
                        tokenResponse.refreshToken
                    )
                } else {
                    remove("${KEY_REFRESH_TOKEN_PREFIX}$serverId")
                }

                if (expiryTimeSeconds != null && expiryTimeSeconds > Clock.System.now().epochSeconds) {
                    putLong("${KEY_TOKEN_EXPIRY_PREFIX}$serverId", expiryTimeSeconds)
                } else {
                    remove("${KEY_TOKEN_EXPIRY_PREFIX}$serverId")
                }
                if (tokenResponse.scope != null) {
                    putString("${KEY_SCOPES_PREFIX}$serverId", tokenResponse.scope)
                } else {
                    remove("${KEY_SCOPES_PREFIX}$serverId")
                }
                Log.d(TAG, "Saved tokens for server $serverId. Expiry: $expiryTimeSeconds")
            }
            updateAuthStatus(serverId, AuthStatus.AUTHORIZED)
        }

    override suspend fun getPersistedOAuthDetails(serverId: Long): PersistedOAuthDetails? =
        withContext(ioDispatcher) {
            val clientId =
                mcpServerDao.getServerById(serverId)?.oauthClientId ?: return@withContext null
            PersistedOAuthDetails(
                clientId = clientId,
                clientSecret = securePreferences.getString(
                    "${KEY_CLIENT_SECRET_PREFIX}$serverId",
                    null
                ),
                accessToken = securePreferences.getString(
                    "${KEY_ACCESS_TOKEN_PREFIX}$serverId",
                    null
                ),
                refreshToken = securePreferences.getString(
                    "${KEY_REFRESH_TOKEN_PREFIX}$serverId",
                    null
                ),
                tokenExpiryEpochSeconds = securePreferences.getLong(
                    "${KEY_TOKEN_EXPIRY_PREFIX}$serverId",
                    0
                ).takeIf { it > 0 },
                scopes = securePreferences.getString("${KEY_SCOPES_PREFIX}$serverId", null)
            )
        }

    override suspend fun clearOAuthDetails(serverId: Long) = withContext(ioDispatcher) {
        Log.i(TAG, "Clearing OAuth details for server $serverId")
        securePreferences.edit {
            remove("${KEY_CLIENT_SECRET_PREFIX}$serverId")
            remove("${KEY_ACCESS_TOKEN_PREFIX}$serverId")
            remove("${KEY_REFRESH_TOKEN_PREFIX}$serverId")
            remove("${KEY_TOKEN_EXPIRY_PREFIX}$serverId")
            remove("${KEY_SCOPES_PREFIX}$serverId")
        }
        mcpServerDao.updateServerClientId(serverId, null)
        val server = mcpServerDao.getServerById(serverId)
        if (server?.requiresAuth == true) {
            updateAuthStatus(serverId, AuthStatus.REQUIRED_NOT_AUTHORIZED)
        } else {
            updateAuthStatus(serverId, AuthStatus.NOT_REQUIRED)
        }
    }

    override suspend fun updateAuthStatus(serverId: Long, status: AuthStatus): Unit =
        withContext(ioDispatcher) {
            mcpServerDao.updateServerAuthStatus(serverId, status)
            Log.d(TAG, "Updated auth status for server $serverId to $status")
        }

    override suspend fun getValidAccessToken(serverId: Long): String? = withContext(ioDispatcher) {
        val details = getPersistedOAuthDetails(serverId) ?: run {
            Log.d(TAG, "No OAuth details found in secure storage")
            return@withContext null
        }
        val now = Clock.System.now().epochSeconds

        if (details.accessToken != null && (details.tokenExpiryEpochSeconds == null || details.tokenExpiryEpochSeconds > now)) {
            Log.v(TAG, "Access token is valid")
            return@withContext details.accessToken
        }

        val server = mcpServerDao.getServerById(serverId)
        if (details.refreshToken != null) {
            Log.i(TAG, "Access token expired/missing for server $serverId. Refreshing...")
            val metadataUrl = server?.authorizationServerMetadataUrl ?: run {
                Log.e(TAG, "Cannot refresh token: Missing AS metadata URL for server $serverId")
                updateAuthStatus(serverId, AuthStatus.ERROR)
                return@withContext null
            }
            val metadataResult = authorizationService.getAuthorizationServerMetadata(metadataUrl)

            metadataResult.fold(
                onSuccess = { metadata ->
                    val refreshedResult = authorizationService.refreshAccessToken(
                        tokenEndpoint = metadata.tokenEndpoint,
                        clientId = details.clientId,
                        clientSecret = details.clientSecret, // TODO: maybe null ?
                        refreshToken = details.refreshToken
                    )
                    refreshedResult.fold(
                        onSuccess = { tokenResponse ->
                            Log.i(TAG, "token refreshed for $serverId")
                            saveOAuthTokens(serverId, tokenResponse)
                            return@withContext tokenResponse.accessToken
                        },
                        onFailure = { error ->
                            Log.e(TAG, "failed to refresh token for $serverId", error)
                            clearOAuthDetails(serverId)
                            updateAuthStatus(serverId, AuthStatus.REQUIRED_USER_ACTION)
                            return@withContext null
                        }
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "no valid access/refresh token for server $serverId")
                    updateAuthStatus(serverId, AuthStatus.ERROR)
                    return@withContext null
                }
            )
        } else {
            Log.w(TAG, "No valid access/refresh token for server $serverId. Reauthorization needed")
            if (server?.requiresAuth == true) {
                updateAuthStatus(serverId, AuthStatus.REQUIRED_USER_ACTION)
            }
            return@withContext null
        }
    }

    override suspend fun handleAuthorizationCodeExchange(
        serverId: Long,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            updateAuthStatus(serverId, AuthStatus.REQUIRED_TOKEN_EXCHANGE)

            val server = mcpServerDao.getServerById(serverId)
                ?: throw Exception("server $serverId not found during token exchange")

            val clientId = server.oauthClientId
                ?: throw Exception("Client ID missing for server $serverId")

            val clientSecret = getPersistedOAuthDetails(serverId)?.clientSecret
            val metadataUrl = server.authorizationServerMetadataUrl
                ?: throw Exception("AS metadata URL missing for server $serverId")

            val metadata =
                authorizationService.getAuthorizationServerMetadata(metadataUrl).getOrThrow()

            Log.d(TAG, "Exchanging code for token for server $serverId")
            val tokenResult = authorizationService.exchangeCodeForToken(
                tokenEndpoint = metadata.tokenEndpoint,
                clientId = clientId,
                clientSecret = clientSecret,
                code = code,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier,
            )

            tokenResult.fold(
                onSuccess = { tokenResponse ->
                    Log.i(TAG, "Token exchange successful for server $serverId")
                    saveOAuthTokens(serverId, tokenResponse)
                },
                onFailure = { error ->
                    Log.e(TAG, "Token exchange failed for server $serverId", error)
                    updateAuthStatus(serverId, AuthStatus.ERROR)
                    throw error
                }
            )
        }
    }.onFailure {
        Log.e(TAG, "Handle authorization code exchange failed for server $serverId", it)
        updateAuthStatus(serverId, AuthStatus.ERROR)
    }

    override suspend fun setRequiresAuth(serverId: Long, requiresAuth: Boolean): Unit =
        withContext(ioDispatcher) {
            mcpServerDao.getServerById(serverId)?.let {
                mcpServerDao.upsertServer(it.copy(requiresAuth = requiresAuth))
                if (!requiresAuth) {
                    clearOAuthDetails(serverId)
                    updateAuthStatus(serverId, AuthStatus.NOT_REQUIRED)
                }
            } ?: Log.w(TAG, "setRequiresAuth: Server not found $serverId")
        }

    override suspend fun performInitialAuthDiscovery(serverId: Long): Result<AuthStatus> =
        runCatching {
            withContext(ioDispatcher) {
                val server = mcpServerDao.getServerById(serverId)?.asDomainModel()
                    ?: throw Exception("Server $serverId not found for discovery")

                // skip for disabled
                if (!server.enabled) return@withContext AuthStatus.NOT_REQUIRED

                Log.d(TAG, "Performing initial auth discovery for server $serverId")
                updateAuthStatus(serverId, AuthStatus.REQUIRED_DISCOVERY)

                val directAsMetadataUrl = try {
                    Uri.parse(server.url)
                        .buildUpon()
                        .path("/.well-known/oauth-authorization-server")
                        .clearQuery()
                        .build()
                        .toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid base URL format for server ${server.id}: ${server.url}", e)
                    throw Exception("Invalid server URL format", e)
                }

                Log.d(TAG, "Attempting direct AS metadata fetch from: $directAsMetadataUrl")
                val directAsResult =
                    authorizationService.getAuthorizationServerMetadata(directAsMetadataUrl)

                var finalStatus: AuthStatus = AuthStatus.NOT_REQUIRED
                var authRequired = false
                var metadataUrlToSave: String? = null

                directAsResult.fold(
                    onSuccess = { metadata ->
                        Log.i(TAG, "Direct AS metadata discovery successful for server $serverId")
                        authRequired = true
                        metadataUrlToSave = directAsMetadataUrl
                        finalStatus = AuthStatus.REQUIRED_NOT_AUTHORIZED

//                    val asMetadataUrl = metadata.authorizationServers.firstOrNull()
//                    if (asMetadataUrl != null) {
//                        Log.i(
//                            TAG,
//                            "Discovered AS metadata URL for server $serverId: $asMetadataUrl"
//                        )
//                        saveAuthorizationServerMetadataUrl(serverId, asMetadataUrl)
//                        // check for credentials
//                        val details = getPersistedOAuthDetails(serverId)
//                        val newStatus =
//                            if (details?.clientId != null && (details.accessToken != null || details.refreshToken != null)) {
//                                AuthStatus.AUTHORIZED
//                            } else {
//                                AuthStatus.REQUIRED_USER_ACTION
//                            }
//                        updateAuthStatus(serverId, newStatus)
//                        setRequiresAuth(serverId, true)
//                        Result.success(newStatus)
//                    } else {
//                        Log.w(
//                            TAG,
//                            "Resource server $serverId metadata did not contain authorization_servers"
//                        )
//                        setRequiresAuth(serverId, false)
//                        updateAuthStatus(serverId, AuthStatus.NOT_REQUIRED)
//                        Result.success(AuthStatus.NOT_REQUIRED)
//                    }
                    },
                    onFailure = { error ->
                        Log.w(
                            TAG,
                            "Direct AS metadata discovery failed for server $serverId",
                            error
                        )
                        // TODO: Comeback to this later
                        // how to handle this?
                        // for now I'm just gonna assume failure means we can't determine auth requirement definitively.
                        authRequired = false
                        metadataUrlToSave = null
                        finalStatus = AuthStatus.ERROR
//                    updateAuthStatus(serverId, AuthStatus.ERROR)
//                    Result.failure(error)
                    }
                )

                mcpServerDao.updateServerAuthDetails(
                    configId = serverId,
                    requiresAuth = authRequired,
                    authStatus = finalStatus,
                    metadataUrl = metadataUrlToSave
                )
                Log.d(
                    TAG,
                    "Discovery finished for server $serverId. RequiresAuth=$authRequired, Status=$finalStatus"
                )
                return@withContext finalStatus
            }
        }.onFailure {
            Log.e(TAG, "Error during discovery for server $serverId", it)
            updateAuthStatus(serverId, AuthStatus.ERROR)
        }


    override suspend fun getAuthorizationRequestDetails(serverId: Long): Result<AuthRequestDetails> =
        runCatching {
            withContext(ioDispatcher) {
                val server = mcpServerDao.getServerById(serverId)
                    ?: throw Exception("server $serverId not found")

                updateAuthStatus(serverId, AuthStatus.REQUIRED_DISCOVERY)

                if (!server.requiresAuth) {
                    val currentStatus = server.authStatus
                    if (currentStatus == AuthStatus.NOT_REQUIRED || currentStatus == AuthStatus.NOT_CHECKED) {
                        throw Exception("Server $serverId does not require authorization or discovery pending.")
                    }
                    // TODO: come back to this
                    // fail for now
                    if (currentStatus == AuthStatus.ERROR) {
                        throw Exception("server $serverId does not require auth")
                    }
                }

                val asMetadataUrl = server.authorizationServerMetadataUrl ?: run {
                    Log.w(
                        TAG,
                        "AS metadata URL missing for server $serverId, attempting discovery..."
                    )
                    // TODO: come back to this
                    // should we attempt discovery again ? or just fail outright
                    val discoveryResult = performInitialAuthDiscovery(serverId).getOrThrow()
                    if (discoveryResult != AuthStatus.REQUIRED_USER_ACTION && discoveryResult != AuthStatus.AUTHORIZED) {
                        throw Exception("initial discovery failed or auth not needed, status: $discoveryResult")
                    }
                    mcpServerDao.getServerById(serverId)?.authorizationServerMetadataUrl
                        ?: throw Exception("AS metadata URL still missing after discovery for server $serverId")
                }

                // fetching AS details
                updateAuthStatus(serverId, AuthStatus.REQUIRED_DISCOVERY)

                val asMetadata =
                    authorizationService.getAuthorizationServerMetadata(asMetadataUrl).getOrThrow()

                var clientId = server.oauthClientId
                var clientSecret = getPersistedOAuthDetails(serverId)?.clientSecret

                if (clientId == null && asMetadata.registrationEndpoint != null) {
                    updateAuthStatus(serverId, AuthStatus.REQUIRED_REGISTRATION)
                    Log.i(TAG, "Attempting dynamic client registration for server $serverId")
                    val registrationRequest = ClientRegistrationRequest(
                        redirectUris = listOf(AuthorizationService.REDIRECT_URI),
                        clientName = "Nuvo Android Client v${BuildConfig.VERSION_NAME}",
                        softwareId = BuildConfig.APPLICATION_ID,
                        softwareVersion = BuildConfig.VERSION_NAME,
                        tokenEndpointAuthMethod = "none"
                    )
                    val registrationResponse = authorizationService.registerClient(
                        asMetadata.registrationEndpoint,
                        registrationRequest
                    ).getOrThrow()

                    clientId = registrationResponse.clientId
                    clientSecret = registrationResponse.clientSecret

                    saveOAuthClientId(serverId, clientId)
                    saveOAuthClientSecret(serverId, clientSecret)
                    Log.i(TAG, "Dynamic registration successful for server $serverId")

                } else if (clientId == null) {
                    throw Exception("Client ID missing and dynamic registration unavailable/failed for server $serverId")
                }

                updateAuthStatus(serverId, AuthStatus.REQUIRED_USER_ACTION)

                AuthRequestDetails(
                    authorizationEndpointUri = Uri.parse(asMetadata.authorizationEndpoint),
                    tokenEndpointUri = Uri.parse(asMetadata.tokenEndpoint),
                    registrationEndpointUri = asMetadata.registrationEndpoint?.let { Uri.parse(it) },
                    clientId = clientId,
                    scopes = asMetadata.scopesSupported
                )
            }
        }.onFailure {
            Log.e(TAG, "Failed to get authorization request for server $serverId", it)
            updateAuthStatus(serverId, AuthStatus.ERROR)
        }
}
