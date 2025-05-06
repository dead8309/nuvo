package xyz.dead8309.nuvo.data.repository

import android.net.Uri
import android.util.Log
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import xyz.dead8309.nuvo.BuildConfig
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.datastore.PreferenceDataStore
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.ClientRegistrationRequest
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.core.model.PersistedOAuthDetails
import xyz.dead8309.nuvo.data.model.asDomainModel
import xyz.dead8309.nuvo.data.model.asEntity
import xyz.dead8309.nuvo.data.remote.oauth.OAuthService
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SettingsRepositoryImpl"

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val preferenceDataStore: PreferenceDataStore,
    private val mcpServerDao: McpServerDao,
    private val oauthService: OAuthService,
    private val authStateManager: AuthStateManager
) : SettingsRepository {

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
        if (currentEntity != null && currentEntity.url != config.url) {
            Log.w(TAG, "Server URL changed for ${config.id}. clearing AuthState")
            authStateManager.clearAuthState(config.id)
            val entityToSave = config.asEntity(currentEntity).copy(
                authStatus = AuthStatus.NOT_CHECKED,
                authorizationServerMetadataUrl = null,
                oauthClientId = null
            )
            mcpServerDao.upsertServer(entityToSave)
        } else {
            mcpServerDao.upsertServer(config.asEntity(currentEntity))
        }
        return@withContext config.id
    }

    override suspend fun setActiveMcpServer(id: Long, enabled: Boolean) =
        withContext(ioDispatcher) {
            mcpServerDao.setServerEnabled(id, enabled)
            if (!enabled) {
                // reset auth state when disabled
                updateAuthStatus(id, AuthStatus.NOT_CHECKED)
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

    override suspend fun saveInitialAuthState(serverId: Long, authState: AuthState) {
        authStateManager.saveAuthState(serverId, authState)
        Log.d(TAG, "Saved initial AuthState for server $serverId")
    }

    override suspend fun updateAuthStateWithTokenResponse(
        serverId: Long,
        tokenResponse: net.openid.appauth.TokenResponse
    ) {
        var currentAuthState = authStateManager.getAuthState(serverId)

        if (currentAuthState == null) {
            Log.e(
                TAG,
                "Cannot update AuthState: Initial AuthState missing for server $serverId"
            )
            updateAuthStatus(serverId, AuthStatus.ERROR)
            throw IllegalStateException("Initial AuthState missing for server $serverId")
        }

        currentAuthState.update(tokenResponse, null)
        authStateManager.saveAuthState(serverId, currentAuthState)
        updateAuthStatus(serverId, AuthStatus.AUTHORIZED)
        Log.i(TAG, "Updated and saved AuthState with new tokens for server $serverId")
    }

    override suspend fun getAuthState(serverId: Long): AuthState? {
        return authStateManager.getAuthState(serverId)
    }

    override suspend fun getPersistedOAuthDetails(serverId: Long): PersistedOAuthDetails? =
        withContext(ioDispatcher) {
            val server = mcpServerDao.getServerById(serverId)
            val clientId = server?.oauthClientId

            if (clientId == null) {
                Log.d(TAG, "No client id found for server $serverId")
                return@withContext null
            }

            val authState = authStateManager.getAuthState(serverId)
            PersistedOAuthDetails(
                clientId = clientId,
                accessToken = null,
                refreshToken = null,
                tokenExpiryEpochSeconds = authState?.accessTokenExpirationTime?.div(1000),
                scopes = authState?.scope
            )
        }

    override suspend fun clearOAuthDetails(serverId: Long) = withContext(ioDispatcher) {
        Log.i(TAG, "Clearing OAuth details for server $serverId")
        authStateManager.clearAuthState(serverId)
        mcpServerDao.updateServerClientId(serverId, null)

        val server = mcpServerDao.getServerById(serverId)
        val newStatus = if (server?.requiresAuth == true) {
            AuthStatus.REQUIRED_NOT_AUTHORIZED
        } else {
            AuthStatus.NOT_REQUIRED
        }
        updateAuthStatus(serverId, newStatus)
    }

    override suspend fun updateAuthStatus(serverId: Long, status: AuthStatus): Unit =
        withContext(ioDispatcher) {
            mcpServerDao.updateServerAuthStatus(serverId, status)
            Log.d(TAG, "Updated auth status for server $serverId to $status")
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
                    oauthService.getAuthorizationServerMetadata(asMetadataUrl).getOrThrow()

                var clientId = server.oauthClientId

                if (clientId == null && asMetadata.registrationEndpoint != null) {
                    updateAuthStatus(serverId, AuthStatus.REQUIRED_REGISTRATION)
                    Log.i(TAG, "Attempting dynamic client registration for server $serverId")
                    val registrationRequest = ClientRegistrationRequest(
                        redirectUris = listOf(OAuthService.REDIRECT_URI),
                        clientName = "Nuvo v${BuildConfig.VERSION_NAME}",
                        softwareId = BuildConfig.APPLICATION_ID,
                        softwareVersion = BuildConfig.VERSION_NAME,
                        tokenEndpointAuthMethod = "none"
                    )
                    val registrationResponse = oauthService.registerClient(
                        asMetadata.registrationEndpoint,
                        registrationRequest
                    ).getOrThrow()

                    clientId = registrationResponse.clientId
                    saveOAuthClientId(serverId, clientId)
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

    override suspend fun setRequiresAuth(serverId: Long, requiresAuth: Boolean): Unit =
        withContext(ioDispatcher) {
            val currentServer = mcpServerDao.getServerById(serverId)

            if (currentServer == null) {
                Log.w(TAG, "setRequiresAuth: Server not found $serverId")
                return@withContext
            }

            if (currentServer.requiresAuth != requiresAuth) {
                Log.d(
                    TAG,
                    "Setting requiresAuth=$requiresAuth for server $serverId, was ${currentServer.requiresAuth}"
                )

                val newAuthStatus = when {
                    !requiresAuth -> {
                        Log.d(TAG, "Server no longer requires auth")
                        clearOAuthDetails(serverId)
                        AuthStatus.NOT_REQUIRED
                    }

                    requiresAuth -> {
                        Log.d(TAG, "Server now requires auth")
                        AuthStatus.REQUIRED_USER_ACTION
                    }

                    else -> currentServer.authStatus
                }

                mcpServerDao.upsertServer(
                    currentServer.copy(
                        requiresAuth = requiresAuth,
                        authStatus = newAuthStatus
                    )
                )
                Log.d(
                    TAG,
                    "Updated server $serverId: requiresAuth: $requiresAuth, authStatus: $newAuthStatus"
                )

            } else {
                Log.d(TAG, "No change in requiresAuth for server $serverId, skipping update")
            }
        }

    override suspend fun performInitialAuthDiscovery(serverId: Long): Result<AuthStatus> =
        runCatching {
            withContext(ioDispatcher) {
                val server = mcpServerDao.getServerById(serverId)?.asDomainModel()
                    ?: throw Exception("Server $serverId not found for discovery")

                // skip for disabled
                if (!server.enabled) {
                    Log.d(TAG, "Server $serverId is disabled, skipping discovery.")
                    return@withContext AuthStatus.NOT_CHECKED

                }

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
                    updateAuthStatus(serverId, AuthStatus.ERROR)
                    throw Exception("Invalid server URL format", e)
                }

                Log.d(TAG, "Attempting direct AS metadata fetch from: $directAsMetadataUrl")
                val directAsResult =
                    oauthService.getAuthorizationServerMetadata(directAsMetadataUrl)

                var authRequired = false
                var finalStatus: AuthStatus = AuthStatus.REQUIRED_NOT_AUTHORIZED
                var metadataUrlToSave: String? = null

                directAsResult.fold(
                    onSuccess = { metadata ->
                        Log.i(TAG, "Direct AS metadata discovery successful for server $serverId")
                        authRequired = true
                        metadataUrlToSave = directAsMetadataUrl
                        finalStatus = AuthStatus.REQUIRED_NOT_AUTHORIZED

                        val existingAuthState = authStateManager.getAuthState(serverId)
                        finalStatus = if (existingAuthState?.isAuthorized == true) {
                            Log.d(
                                TAG,
                                "Existing valid AuthState found for server $serverId after discovery"
                            )
                            AuthStatus.AUTHORIZED
                        } else {
                            AuthStatus.REQUIRED_USER_ACTION
                        }
                    },
                    onFailure = { error ->
                        if (error is ClientRequestException && error.response.status == HttpStatusCode.NotFound) {
                            Log.w(
                                TAG,
                                "AS metadata discovery failed with 404 for server $serverId. Assuming auth not required.",
                                error
                            )
                            authRequired = false
                            finalStatus = AuthStatus.NOT_REQUIRED
                            metadataUrlToSave = null

                            authStateManager.clearAuthState(serverId)
                            mcpServerDao.updateServerClientId(serverId, null)
                        } else {
                            Log.e(TAG, "AS metadata discovery failed for server $serverId", error)
                            finalStatus = AuthStatus.ERROR
                            authRequired = server.requiresAuth
                            metadataUrlToSave = server.authorizationServerMetadataUrl
                        }
                    }
                )

                Log.d(
                    TAG,
                    "Updating server $serverId auth details: requiresAuth=$authRequired, status=$finalStatus, metadataUrl=$metadataUrlToSave"
                )
                mcpServerDao.updateServerAuthDetails(
                    configId = serverId,
                    requiresAuth = authRequired,
                    authStatus = finalStatus,
                    metadataUrl = metadataUrlToSave
                )

                return@withContext finalStatus
            }
        }.onFailure {
            Log.e(TAG, "Error during discovery for server $serverId", it)
            updateAuthStatus(serverId, AuthStatus.ERROR)
        }
}