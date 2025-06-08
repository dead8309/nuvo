package xyz.dead8309.nuvo.data.remote.mcp.client

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSEClientException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
private val CONNECT_TIMEOUT = 60.seconds
private val CLOSE_TIMEOUT = 5.seconds
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_SECONDS = 1L

class McpConnectionManagerImpl @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsRepository: SettingsRepository,
    private val httpClient: HttpClient,
    private val appAuthService: AuthorizationService,
    private val authStateManager: AuthStateManager
) : McpConnectionManager {

    private val activeClients = ConcurrentHashMap<ClientId, Client>()
    private val connectionJobs = ConcurrentHashMap<ClientId, Job>()
    private val clientEventJobs = ConcurrentHashMap<ClientId, Job>()

    private val authMutex = Mutex()

    private val _connectionStates = MutableStateFlow<Map<ClientId, ConnectionState>>(emptyMap())
    override val connectionState: StateFlow<Map<ClientId, ConnectionState>> =
        _connectionStates.asStateFlow()

    override suspend fun getOrConnectClient(clientId: ClientId): Client? {
        Log.d(TAG, "getOrConnectClient started for $clientId (${System.currentTimeMillis()})")

        activeClients[clientId]?.let { existingClient ->
            if (existingClient.isConnected()) {
                Log.v(TAG, "Returning existing client for $clientId")
                return existingClient
            } else {
                Log.w(
                    TAG,
                    "Existing client found for $clientId but not connected, will attempt reconnect."
                )
                activeClients.remove(clientId)
                updateConnectionState(clientId, ConnectionState.DISCONNECTED)
            }
        }

        val config = settingsRepository.getMcpServer(clientId)
        if (config == null || !config.enabled || config.url.isBlank()) {
            Log.w(TAG, "Client not found, disabled, or URL is blank: $clientId")
            updateConnectionState(clientId, ConnectionState.DISCONNECTED)
            activeClients.remove(clientId)?.closeQuietly(clientId)
            return null
        }

        connectionJobs[clientId]?.let { job ->
            if (job.isActive) {
                Log.d(TAG, "Client $clientId: Connection job already in progress. Waiting...")
                try {
                    job.join()
                    // check if the client is now connected after joining
                    activeClients[clientId]?.let {
                        if (it.isConnected()) {
                            return it
                        }
                    }
                    Log.w(TAG, "Client $clientId: Joined existing job, but not connected.")
                } catch (_: CancellationException) {
                    Log.w(TAG, "Client $clientId: Connection job was cancelled.")
                } catch (e: Exception) {
                    Log.e(TAG, "Client $clientId: Error joining existing connection job", e)
                }
            }
        }

        val client = connectClientInternal(config)
        return client
    }

    private suspend fun connectClientInternal(config: McpServer): Client? {
        val clientServerId = config.id
        var retryCount = 0

        updateConnectionState(clientServerId, ConnectionState.CONNECTING)
        val connectJob = Job()
        connectionJobs[clientServerId] = connectJob

        var attemptClient: Client? = null
        try {
            while (retryCount < MAX_RETRIES) {
                if (!connectJob.isActive) throw CancellationException("Connection job was cancelled")

                var accessTokenForTransport: String? = null
                var authStateToPersist: AuthState? = null

                if (config.requiresAuth) {
                    // authMutex only for auth operations
                    authMutex.withLock {
                        val authState = authStateManager.getAuthState(clientServerId)
                        if (authState == null || !authState.isAuthorized) {
                            Log.w(
                                TAG,
                                "Server $clientServerId requires auth but not authorized. Current: ${config.authStatus}"
                            )

                            if (config.authStatus != AuthStatus.ERROR &&
                                config.authStatus != AuthStatus.REQUIRED_DISCOVERY &&
                                config.authStatus != AuthStatus.REQUIRED_AWAITING_CALLBACK
                            ) {
                                settingsRepository.updateAuthStatus(
                                    clientServerId,
                                    AuthStatus.REQUIRED_USER_ACTION
                                )
                            }
                            updateConnectionState(clientServerId, ConnectionState.FAILED)
                            connectionJobs.remove(clientServerId)
                            return null
                        }

                        Log.d(TAG, "Client $clientServerId: Attempting to get fresh token...")
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
                }

                Log.i(
                    TAG,
                    "Proceeding to connect client for $clientServerId, AuthRequired: ${config.requiresAuth}"
                )
                attemptClient = Client(
                    clientInfo = Implementation(
                        name = BuildConfig.APPLICATION_ID,
                        version = BuildConfig.VERSION_NAME
                    )
                )
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

                try {
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
                    settingsRepository.updateServerInfo(
                        serverId = clientServerId,
                        serverVersion = attemptClient.serverVersion?.version
                    )

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

                    clientEventJobs[clientServerId]?.cancel()
                    clientEventJobs[clientServerId] =
                        appScope.launch(ioDispatcher + SupervisorJob() + connectJob) {
                            handleClientEvents(attemptClient, clientServerId)
                        }
                    connectionJobs.remove(clientServerId, connectJob)
                    return attemptClient

                } catch (e: TimeoutCancellationException) {
                    Log.w(
                        TAG,
                        "Connection attempt timed out for $clientServerId (Attempt ${retryCount + 1})",
                        e
                    )
                    attemptClient.closeQuietly(clientServerId)
                    if (retryCount + 1 >= MAX_RETRIES) throw e
                } catch (e: SSEClientException) {
                    Log.e(TAG, "SSEClientException for $clientServerId", e)
                    attemptClient.closeQuietly(clientServerId)
                    if (e.response?.status == HttpStatusCode.Unauthorized) {
                        Log.w(
                            TAG,
                            "Connection failed with 401 for $clientServerId. Token might be invalid"
                        )
                        handleUnauthorized(clientServerId, config.requiresAuth)
                        updateConnectionState(clientServerId, ConnectionState.FAILED)
                        return null
                    }
                    if (retryCount + 1 >= MAX_RETRIES) throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Connection attempt failed for $clientServerId",
                        e
                    )
                    attemptClient.closeQuietly(clientServerId)
                    if (retryCount + 1 >= MAX_RETRIES) throw e
                }

                retryCount++
                Log.w(TAG, "Retrying connection for $clientServerId, attempt $retryCount")
                delay(RETRY_DELAY_SECONDS.seconds)
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "Connection attempt cancelled for $clientServerId", e)
            attemptClient?.closeQuietly(clientServerId)
            updateConnectionState(clientServerId, ConnectionState.FAILED)
        } catch (e: Exception) {
            Log.e(TAG, "Client: $clientServerId: Failed to connect after $MAX_RETRIES attempts", e)
            attemptClient?.closeQuietly(clientServerId)
            updateConnectionState(clientServerId, ConnectionState.FAILED)
            if (config.requiresAuth && _connectionStates.value[clientServerId] == ConnectionState.FAILED) {
                settingsRepository.updateAuthStatus(clientServerId, AuthStatus.ERROR)
            }
        } finally {
            if (connectionJobs[clientServerId] == connectJob) {
                connectionJobs.remove(clientServerId)
            }
        }
        return null
    }

    private suspend fun handleUnauthorized(clientId: Long, wasRequiringAuth: Boolean) {
        if (!wasRequiringAuth) {
            Log.i(TAG, "$clientId requires auth, but got 401. Setting to requires auth.")
            settingsRepository.setRequiresAuth(clientId, true)
            settingsRepository.performInitialAuthDiscovery(clientId)
        } else {
            Log.w(TAG, "$clientId requires auth, but got 401. Clearing auth details.")
            settingsRepository.clearOAuthDetails(clientId)
            settingsRepository.updateAuthStatus(
                clientId,
                AuthStatus.REQUIRED_USER_ACTION
            )
        }
    }

    override suspend fun getExistingClient(clientId: ClientId): Client? {
        val client = activeClients[clientId]
        return if (client?.isConnected() == true) {
            client
        } else {
            null
        }
    }

    private suspend fun handleClientEvents(client: Client, clientId: ClientId) {
        Log.d(TAG, "Starting event listeners for client $clientId")
        try {
            coroutineScope {
                launch {
                    client.transport?.onError {
                        Log.e(TAG, "Client transport error: $it")
                        updateConnectionState(clientId, ConnectionState.FAILED)

                        // remove client before cancelling job
                        synchronized(activeClients) {
                            activeClients.remove(clientId)
                        }

                        clientEventJobs.remove(clientId)?.cancel()
                        this.coroutineContext[Job]?.cancel()
                    }
                }
                launch {
                    client.transport?.onClose {
                        Log.w(TAG, "Client transport closed: for $clientId")

                        synchronized(activeClients) {
                            if (activeClients.containsKey(clientId)) {
                                activeClients.remove(clientId)
                                updateConnectionState(clientId, ConnectionState.DISCONNECTED)
                            }
                        }

                        clientEventJobs.remove(clientId)?.cancel()
                        this.coroutineContext[Job]?.cancel()
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

            val shouldDisconnect = synchronized(activeClients) {
                activeClients.containsKey(clientId)
            }
            if (shouldDisconnect) {
                disconnectClientInternal(clientId)
            }
        }
    }

    private suspend fun disconnectClientInternal(clientId: ClientId) {
        Log.i(TAG, "Internal disconnect for $clientId")

        if (!activeClients.containsKey(clientId)) {
            Log.d(TAG, "No active client found for $clientId during disconnect, skipping")
            return
        }
        connectionJobs.remove(clientId)?.cancel()
        clientEventJobs.remove(clientId)?.cancel()
        activeClients.remove(clientId)?.let { client ->
            Log.d(TAG, "Closing client instance for $clientId")
            client.closeQuietly(clientId)
        }
        updateConnectionState(clientId, ConnectionState.DISCONNECTED)
    }

    override suspend fun disconnectClient(clientId: ClientId) {
        withContext(ioDispatcher) {
            disconnectClientInternal(clientId)
        }
    }

    private fun updateConnectionState(clientId: ClientId, newState: ConnectionState) {
        Log.d(TAG, "Updating connection state for $clientId to $newState")
        _connectionStates.update { currentMap ->
            val oldState = currentMap[clientId]
            if (oldState == newState)
                return@update currentMap

            currentMap + (clientId to newState)
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