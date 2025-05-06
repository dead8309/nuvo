package xyz.dead8309.nuvo.data.remote.mcp.client

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import xyz.dead8309.nuvo.BuildConfig
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.repository.AuthStateManager
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import xyz.dead8309.nuvo.di.ApplicationScope
import xyz.dead8309.nuvo.di.IoDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private const val TAG = "McpConnectionManagerImpl"
private val CONNECT_TIMEOUT = 20.seconds
private val CLOSE_TIMEOUT = 5.seconds

// TODO: use exponential backoff
private const val MAX_RETRIES = 3
private const val RETRY_DELAY = 1L

class McpConnectionManagerImpl @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsRepository: SettingsRepository,
    private val httpClient: HttpClient,
    private val appAuthService: AuthorizationService,
    private val authStateManager: AuthStateManager
) : McpConnectionManager {
    // NOTE
    // FUTURE_ME: PLEASE DON'T RENAME THIS, ANDROID STUDIO ALWAYS PLACES IT AT THE TOP OF
    // COMPLETION LIST
    private val _implementation = Implementation(
        name = BuildConfig.APPLICATION_ID,
        version = BuildConfig.VERSION_NAME
    )

    private val activeClients = ConcurrentHashMap<ClientId, Client>()
    private val connectionJobs = ConcurrentHashMap<ClientId, Job>()
    private val clientEventJobs = ConcurrentHashMap<ClientId, Job>()

    private val _connectionStates = MutableStateFlow<Map<ClientId, ConnectionState>>(emptyMap())

    override val connectionState: StateFlow<Map<ClientId, ConnectionState>> =
        _connectionStates.asStateFlow()


    init {
        Log.d(TAG, "Initializing McpConnectionManager")
        appScope.launch(ioDispatcher) {
            settingsRepository.getAllMcpServers()
                .map { configs -> configs.filter { it.enabled && it.url.isNotBlank() } }
                .distinctUntilChanged()
                .collectLatest { enabledConfigs ->
                    Log.i(TAG, "Enabled MCP configs changed. Count: ${enabledConfigs.size}")
                    val enabledConfigIds = enabledConfigs.map { it.id }.toSet()

                    val clientsToDisconnect =
                        activeClients.keys.filterNot { it in enabledConfigIds }
                    if (clientsToDisconnect.isNotEmpty()) {
                        Log.d(
                            TAG,
                            "Disconnecting clients for disabled/removed servers: $clientsToDisconnect"
                        )
                        clientsToDisconnect.forEach { clientId ->
                            launch { disconnectClientInternal(clientId) }
                        }
                    }

                    enabledConfigs.forEach { config ->
                        launch { getOrConnectClientInternal(config) }
                    }
                }
        }
    }

    override suspend fun getOrConnectClient(clientId: ClientId): Client? =
        withContext(ioDispatcher) {
            val config = settingsRepository.getAllMcpServers().first().find { it.id == clientId }
            if (config == null || !config.enabled || config.url.isBlank()) {
                Log.w(TAG, "Client not found, disabled, or URL is blank: $clientId")
                updateConnectionState(clientId, ConnectionState.DISCONNECTED)
                return@withContext null
            }
            getOrConnectClientInternal(config = config)
        }


    override suspend fun getExistingClient(clientId: ClientId): Client? {
        val client = activeClients[clientId]
        return if (client?.isConnected() == true) {
            client
        } else {
            null
        }
    }


    private suspend fun getOrConnectClientInternal(config: McpServer): Client? {
        val clientServerId = config.id
        var retryCount = 0

        activeClients[clientServerId]?.let { existingClient ->
            if (existingClient.isConnected()) {
                Log.v(TAG, "Returning existing client for $clientServerId")
                return existingClient
            } else {
                Log.w(
                    TAG,
                    "Existing client found for $clientServerId but not connected, attempting reconnect."
                )
                activeClients.remove(clientServerId)
                updateConnectionState(clientServerId, ConnectionState.DISCONNECTED)
            }
        }

        while (retryCount < MAX_RETRIES) {
            val currentState = _connectionStates.value[clientServerId]
            if (currentState == ConnectionState.CONNECTED) {
                activeClients[clientServerId]?.let {
                    Log.v(TAG, "Returning existing client for $clientServerId")
                    return it
                }

                Log.w(TAG, "State is CONNECTED but client is null for $clientServerId")
                updateConnectionState(clientServerId, ConnectionState.DISCONNECTED)
            }

            connectionJobs[clientServerId]?.let { existingJob ->
                if (existingJob.isActive) {
                    Log.d(TAG, "Connection job already exists for $clientServerId")
                    try {
                        existingJob.join()
                        val client = activeClients[clientServerId]
                        if (client != null && _connectionStates.value[clientServerId] == ConnectionState.CONNECTED) {
                            Log.d(
                                TAG,
                                "Joined active job for $clientServerId, connection successful"
                            )
                            return client
                        } else {
                            Log.d(
                                TAG,
                                "Joined active job for $clientServerId, but connection failed"
                            )
                        }
                    } catch (_: CancellationException) {
                        Log.w(TAG, "Joined job for $clientServerId was cancelled.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error joining existing connection job for $clientServerId", e)
                    }
                } else {
                    // cleanup
                    connectionJobs.remove(clientServerId)
                    Log.d(
                        TAG,
                        "Previous connection job for $clientServerId was finished (possibly failed), allowing new request"
                    )
                }
            }

            Log.i(TAG, "Starting new connection job for $clientServerId")
            updateConnectionState(clientServerId, ConnectionState.CONNECTING)
            var attemptClient: Client? = null
            var connectJob = Job()
            connectionJobs[clientServerId] = connectJob

            try {
                var accessTokenForTransport: String? = null
                var authStateToPersist: AuthState? = null

                if (config.requiresAuth) {
                    val authState = authStateManager.getAuthState(clientServerId)
                    if (authState == null || !authState.isAuthorized) {
                        Log.w(
                            TAG,
                            "Server $clientServerId requires auth but not authorized. Current: ${config.authStatus}"
                        )

                        if (config.authStatus != AuthStatus.ERROR && config.authStatus != AuthStatus.REQUIRED_DISCOVERY && config.authStatus != AuthStatus.REQUIRED_AWAITING_CALLBACK) {
                            settingsRepository.updateAuthStatus(
                                clientServerId,
                                AuthStatus.REQUIRED_USER_ACTION
                            )
                        }
                        updateConnectionState(clientServerId, ConnectionState.FAILED)
                        connectionJobs.remove(clientServerId)
                        return null
                    }

                    Log.d(TAG, "Attempting to get fresh token for $clientServerId...")
                    try {
                        accessTokenForTransport = suspendCancellableCoroutine { continuation ->
                            authState.performActionWithFreshTokens(appAuthService) { accessToken, _, ex ->
                                if (ex != null) {
                                    Log.e(
                                        TAG,
                                        "Failed to get fresh token for $clientServerId",
                                        ex
                                    )
                                    val newStatus = when (ex.code) {
                                        AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR.code,
                                        AuthorizationException.GeneralErrors.ID_TOKEN_PARSING_ERROR.code,
                                        AuthorizationException.TokenRequestErrors.INVALID_GRANT.code,
                                        AuthorizationException.TokenRequestErrors.INVALID_CLIENT.code -> AuthStatus.REQUIRED_USER_ACTION

                                        else -> AuthStatus.ERROR
                                    }
                                    appScope.launch {
                                        settingsRepository.updateAuthStatus(
                                            clientServerId,
                                            newStatus
                                        )
                                    }
                                    continuation.resumeWithException(ex)
                                } else if (accessToken == null) {
                                    Log.e(
                                        TAG,
                                        "Fresh token for $clientServerId was null"
                                    )
                                    appScope.launch {
                                        settingsRepository.updateAuthStatus(
                                            clientServerId,
                                            AuthStatus.ERROR
                                        )
                                    }
                                    continuation.resumeWithException(IllegalStateException("Null token received"))
                                } else {
                                    Log.d(
                                        TAG,
                                        "Successfully obtained fresh token for $clientServerId"
                                    )
                                    authStateToPersist = authState
                                    continuation.resume(accessToken)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during fresh token retrieval for $clientServerId", e)
                        updateConnectionState(clientServerId, ConnectionState.FAILED)
                        connectionJobs.remove(clientServerId)
                        return null
                    }
                }

                Log.d(
                    TAG,
                    "Proceeding to connect client for $clientServerId, AuthRequired: ${config.requiresAuth}"
                )
                attemptClient = Client(clientInfo = _implementation)

                val transport = CustomSSEClientTransport(
                    client = httpClient,
                    urlString = config.url
                ) {
                    config.headers.forEach { (k, v) -> header(k, v) }

                    accessTokenForTransport?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                        Log.v(TAG, "Added auth header for $clientServerId")
                    }
                }

                withTimeout(CONNECT_TIMEOUT) {
                    attemptClient.connect(transport)
                }

                Log.i(
                    TAG,
                    "Successfully connected client for ${config.id}. Server ${attemptClient.serverVersion}"
                )
                activeClients[clientServerId] = attemptClient
                updateConnectionState(
                    clientServerId,
                    ConnectionState.CONNECTED
                )
                clientEventJobs[clientServerId] =
                    appScope.launch(ioDispatcher + SupervisorJob()) {
                        handleClientEvents(attemptClient, clientServerId)
                    }

                authStateToPersist?.let {
                    try {
                        authStateManager.saveAuthState(clientServerId, it)
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to save updated authstate for $clientServerId after connection",
                            e
                        )
                    }
                }

                connectionJobs.remove(clientServerId)
                return attemptClient
            } catch (e: CancellationException) {
                Log.w(
                    TAG,
                    "Connection attempt cancelled for $clientServerId",
                    e
                )
                updateConnectionState(
                    clientServerId,
                    ConnectionState.FAILED
                )
                attemptClient?.closeQuietly(clientServerId)
            } catch (e: TimeoutCancellationException) {
                Log.w(
                    TAG,
                    "Connection attempt timed out for $clientServerId (Attempt ${retryCount + 1})",
                    e
                )
                updateConnectionState(clientServerId, ConnectionState.FAILED)
                attemptClient?.closeQuietly(clientServerId)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Connection attempt failed for $clientServerId",
                    e
                )
                updateConnectionState(
                    clientServerId,
                    ConnectionState.FAILED
                )
                attemptClient?.closeQuietly(clientServerId)

                if (e is ResponseException && e.response.status == HttpStatusCode.Unauthorized) {
                    Log.w(
                        TAG,
                        "Connection failed with 401. token might be invalid"
                    )
                    // NOTE: needs re-auth
                    settingsRepository.updateAuthStatus(
                        clientServerId,
                        AuthStatus.REQUIRED_USER_ACTION
                    )
                    authStateManager.clearAuthState(clientServerId)
                } else if (config.requiresAuth && config.authStatus != AuthStatus.REQUIRED_USER_ACTION) {
                    settingsRepository.updateAuthStatus(clientServerId, AuthStatus.ERROR)
                }
            } finally {
                if (connectionJobs[clientServerId] == connectJob) {
                    connectionJobs.remove(clientServerId)
                }
                Log.d(TAG, "Connection job finished for $clientServerId")
            }

            retryCount++
            if (retryCount < MAX_RETRIES) {
                Log.w(TAG, "Retrying connection for $clientServerId, attempt $retryCount")
                delay(RETRY_DELAY.seconds)
            }
        }

        Log.e(TAG, "Failed to connect client for $clientServerId after $MAX_RETRIES attempts.")
        updateConnectionState(clientServerId, ConnectionState.FAILED) // Final state is FAILED
        return null
    }

    private suspend fun handleClientEvents(client: Client, clientId: ClientId) {
        Log.d(TAG, "Starting event listeners for client $clientId")
        try {
            coroutineScope {
                launch {
                    client.transport?.onError {
                        Log.e(TAG, "Client transport error: $it")
                        updateConnectionState(clientId, ConnectionState.FAILED)
                        disconnectClientInternal(clientId)
                    }
                }
                launch {
                    client.transport?.onClose {
                        Log.w(TAG, "Client transport closed: for $clientId")
                        if (activeClients.containsKey(clientId)) {
                            activeClients.remove(clientId)
                            updateConnectionState(clientId, ConnectionState.DISCONNECTED)
                            // cancel siblings too
                            clientEventJobs.remove(clientId)?.cancel()
                        }
                    }
                }

                try {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        continuation.invokeOnCancellation {
                            Log.d(TAG, "Event listener scope cancelled for $clientId")
                        }
                    }
                } catch (_: CancellationException) {
                    Log.d(TAG, "Event listener scope cancelled for $clientId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client events for $clientId", e)
            updateConnectionState(clientId, ConnectionState.DISCONNECTED)
            disconnectClientInternal(clientId)
        }
    }

    private fun disconnectClientInternal(clientId: ClientId) {
        appScope.launch(ioDispatcher) {
            Log.i(TAG, "Internal disconnect for $clientId")
            connectionJobs.remove(clientId)?.cancel()
            clientEventJobs.remove(clientId)?.cancel()

            activeClients.remove(clientId)?.let { client ->
                Log.d(TAG, "Closing client instance for $clientId")
                client.closeQuietly(clientId)
            } ?: Log.d(TAG, "No active client found for $clientId during disconnect")

            updateConnectionState(clientId, ConnectionState.DISCONNECTED)
        }
    }

    override suspend fun disconnectClient(clientId: ClientId) {
        withContext(ioDispatcher) {
            disconnectClientInternal(clientId)
        }
    }

    private fun updateConnectionState(clientId: ClientId, newState: ConnectionState) {
        Log.d(TAG, "Updating connection state for $clientId to $newState")
        _connectionStates.update { currentMap ->
            val prevState = currentMap[clientId]
            if (prevState == ConnectionState.CONNECTED && newState == ConnectionState.DISCONNECTED && activeClients.containsKey(
                    clientId
                )
            ) {
                Log.v(TAG, "Client $clientId is still active but marked as DISCONNECTED")
                currentMap
            } else {
                currentMap + (clientId to newState)
            }
        }
    }

    private suspend fun Client.closeQuietly(clientId: ClientId) {
        try {
            withTimeoutOrNull(CLOSE_TIMEOUT) { this@closeQuietly.close() }
                ?: Log.w(TAG, "MCP client close timed out for $clientId.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception closing client for $clientId", e)
        }
    }

    private fun Client.isConnected(): Boolean {
        val clientId = activeClients.entries.find { it.value == this }?.key
        return clientId != null && _connectionStates.value[clientId] == ConnectionState.CONNECTED
    }
}