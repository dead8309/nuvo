package xyz.dead8309.nuvo.data.remote.mcp.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.flow.StateFlow
import xyz.dead8309.nuvo.core.database.entities.McpServerEntity

/** ClientId is the [McpServerEntity.id] */
typealias ClientId = Long

interface McpConnectionManager {
    /**
     * Gets the active MCP client for a given client ID, attempting to connect if not already.
     * Returns null if connection fails or client config is invalid/disabled.
     */
    suspend fun getOrConnectClient(clientId: ClientId): Client?

    /**
     * Explicitly closes the connection for a specific server ID.
     */
    suspend fun disconnectClient(clientId: ClientId)

    /**
     * Returns the currently connected and presumably ready client without attempting connection.
     */
    suspend fun getExistingClient(clientId: ClientId): Client?

    /**
     * Returns a flow of connection states for all clients.
     */
    val connectionState: StateFlow<Map<ClientId, ConnectionState>>
}

enum class ConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    FAILED
}