package xyz.dead8309.nuvo.data.remote.mcp.client

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import xyz.dead8309.nuvo.BuildConfig
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import xyz.dead8309.nuvo.di.ApplicationScope
import xyz.dead8309.nuvo.di.IoDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private const val TAG = "McpConnectionManagerImpl"
private val CONNECT_TIMEOUT = 20.seconds
private val CLOSE_TIMEOUT = 5.seconds

// TODO: use exponential backoff
private const val MAX_RETRIES = 3
private const val RETRY_DELAY = 1L

class McpConnectionManagerImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val httpClient: HttpClient,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : McpConnectionManager {
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
        val clientId = config.id
        var retryCount = 0

        while (retryCount < MAX_RETRIES) {
            val currentState = _connectionStates.value[clientId]
            if (currentState == ConnectionState.CONNECTED) {
                activeClients[clientId]?.let {
                    Log.v(TAG, "Returning existing client for $clientId")
                    return it
                }

                Log.w(TAG, "State is CONNECTED but client is null for $clientId")
                updateConnectionState(clientId, ConnectionState.DISCONNECTED)
            }

            connectionJobs[clientId]?.let { existingJob ->
                if (existingJob.isActive) {
                    Log.d(TAG, "Connection job already exists for $clientId")
                    existingJob.join()
                    return activeClients[clientId]?.takeIf { _connectionStates.value[clientId] == ConnectionState.CONNECTED }
                } else {
                    connectionJobs.remove(clientId)
                    Log.d(
                        TAG,
                        "Previous connection job for $clientId was finished (possibly failed), allowing new request"
                    )
                }
            }
            Log.i(TAG, "Starting new connection job for $clientId")
            updateConnectionState(clientId, ConnectionState.CONNECTING)

            val connectJob = appScope.launch(ioDispatcher + SupervisorJob()) {
                var client: Client? = null
                try {
                    client = Client(clientInfo = _implementation)

                    val transport = CustomSSEClientTransport(
                        client = httpClient,
                        urlString = config.url
                    ) {
                        config.headers.forEach { (k, v) -> header(k, v) }
                    }

                    withTimeout(CONNECT_TIMEOUT) {
                        client.connect(transport = transport)
                    }

                    activeClients[clientId] = client
                    updateConnectionState(clientId, ConnectionState.CONNECTED)
                    Log.i(
                        TAG,
                        "Successfully connected client for ${config.id}. Server ${client.serverVersion}"
                    )

                    clientEventJobs[clientId] = launch { handleClientEvents(client, clientId) }
                } catch (e: CancellationException) {
                    Log.w(TAG, "Connection attempt cancelled for $clientId", e)
                    updateConnectionState(clientId, ConnectionState.DISCONNECTED)
                    client?.closeQuietly(clientId)
                } catch (e: Exception) {
                    Log.e(TAG, "Connection attempt failed for $clientId", e)
                    updateConnectionState(clientId, ConnectionState.FAILED)
                    client?.closeQuietly(clientId)
                } finally {
                    connectionJobs.remove(clientId)
                    Log.d(TAG, "Connection job finished for $clientId")
                }
            }
            connectionJobs[clientId] = connectJob
            connectJob.join()

            val client = activeClients[clientId]
            if (client != null && _connectionStates.value[clientId] == ConnectionState.CONNECTED) {
                return client
            }

            retryCount++
            if (retryCount < MAX_RETRIES) {
                Log.w(TAG, "Retrying connection for $clientId, attempt $retryCount")
                delay(RETRY_DELAY.seconds)
            }
        }
        Log.e(TAG, "Failed to connect client for $clientId after $MAX_RETRIES attempts")
        updateConnectionState(clientId, ConnectionState.FAILED)
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
                } catch (e: CancellationException) {
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