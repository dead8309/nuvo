package xyz.dead8309.nuvo.data.remote.mcp

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor.Companion.createNamespacedToolName
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor.Companion.extractOriginalToolName
import xyz.dead8309.nuvo.data.remote.mcp.client.McpConnectionManager
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import xyz.dead8309.nuvo.di.ApplicationScope
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject
import kotlin.concurrent.Volatile

private const val TAG = "McpToolExecutorImpl"
private const val TOOL_CALL_TIMEOUT_MS = 30_000L
private const val LIST_TOOLS_TIMEOUT_MS = 30_000L
const val NAMESPACE_DELIMITER = "___"
private const val MAX_RETRIES = 3
private const val INITIAL_RETRY_DELAY = 1000L

class McpToolExecutorImpl @Inject constructor(
    private val connectionManager: McpConnectionManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val json: Json
) : McpToolExecutor {

    // namespacedToolName -> serverId
    @Volatile
    private var toolToServerMap: Map<String, Long> = emptyMap()

    @Volatile
    private var availableTools: List<Tool> = emptyList()
    private var toolMappingJob: Job? = null
    private val mappingMutex = Mutex()

    init {
        observeAndRefreshToolMapping()
    }

    private fun observeAndRefreshToolMapping() {
        var refreshJob: Job? = null
        var lastServerIds: Set<Long> = emptySet()
        appScope.launch(ioDispatcher + SupervisorJob()) {
            Log.i(TAG, "Initial tool mapping refresh at app startup")

            settingsRepository.getAllMcpServers()
                .map { configs ->
                    configs
                        .filter { it.enabled && it.url.isNotBlank() }
                        .map { it.id }.toSet()
                }
                .distinctUntilChanged()
                .collectLatest { enabledServerIds ->
                    if (enabledServerIds.toSet() == lastServerIds.toSet()) {
                        Log.d(TAG, "Skipping refresh for same server set: $enabledServerIds")
                        return@collectLatest
                    }

                    Log.d(TAG, "Enabled servers changed: $enabledServerIds")
                    lastServerIds = enabledServerIds.toSet()
                    refreshJob?.cancel()
                    refreshJob = launch {
                        refreshToolMapping()
                    }
                }
        }
    }


    override suspend fun refreshToolMapping(serverIds: List<Long>) {
        if (serverIds.isEmpty()) {
            refreshToolMappingInternal()
        } else {
            refreshToolMappingInternal(serverIds)
        }
    }

    private suspend fun ensureToolMappingIsFresh() {
        mappingMutex.withLock {
            try {
                toolMappingJob?.join()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh tool mapping", e)
            }
            if (toolToServerMap.isEmpty()) {
                Log.w(TAG, "Tool-to-server mapping is empty")
            }
        }
    }


    override suspend fun executeTool(toolCall: ChatMessage.ToolCall): String =
        withContext(ioDispatcher) {
            ensureToolMappingIsFresh()
            Log.d(TAG, "Executing tool: ${toolCall.function.name}, ID=${toolCall.id}")


            val currentMap = toolToServerMap
            // AI sends back the namespaced name
            val namespacedToolName = toolCall.function.name
            val targetServerId = currentMap[namespacedToolName]
                ?: throwMcpToolExecutionException("Tool '$namespacedToolName' not found or mapped")

            val client = connectionManager.getOrConnectClient(targetServerId)
                ?: throwMcpToolExecutionException("MCP server '$targetServerId' not connected or available for tool '${toolCall.function.name}")

            Log.d(
                TAG,
                "Using client for server $targetServerId to call tool '${toolCall.function.name}'"
            )

            val mcpArguments = try {
                val jsonElement = json.parseToJsonElement(toolCall.function.argumentsJson)
                if (jsonElement !is JsonObject) {
                   throw SerializationException("Arguments JSON must be an object")
                }
                jsonElement.toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse arguments JSON: ${toolCall.function.argumentsJson}", e)
                throwMcpToolExecutionException(
                    "Invalid arguments JSON for tool ${toolCall.function.name}",
                    e
                )
            }

            try {
                // call mcp server with ORIGINAL (non-namespaced) function name
                val originalFunctionName = extractOriginalToolName(namespacedToolName)

                val result = withTimeout(TOOL_CALL_TIMEOUT_MS) {
                    client.callTool(
                        name = originalFunctionName,
                        arguments = mcpArguments
                    )
                }

                if (result == null) {
                    Log.w(TAG, "MCP tool call '${toolCall.function.name}' returned null result")
                    return@withContext """{"result": null, "info": "Tool execution returned no data."}"""
                }

                if (result is CallToolResult && result.isError == true) {
                    Log.e(
                        TAG,
                        "MCP tool call '${toolCall.function.name}' returned an error flag: ${result.content}"
                    )
                    val errorContent = try {
                        json.encodeToString(result.content)
                    } catch (_: Exception) {
                        "[Unserializable error content]"
                    }
                    throwMcpToolExecutionException(
                        "Tool execution failed on server for ${toolCall.function.name}",
                        errorResponseJson = """{"error": "Tool failed on server", "details": $errorContent}"""
                    )
                }

                val resultJsonString = try {
                    // quick dirty way
                    // TODO: handler other result content types
                    json.encodeToString(result.content)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to encode successful tool result to JSON", e)
                    throwMcpToolExecutionException("Failed to format tool result", e)
                }

                Log.d(
                    TAG,
                    "MCP tool call '${toolCall.function.name}' successful. Result JSON: $resultJsonString"
                )
                return@withContext resultJsonString
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "MCP tool call '${toolCall.function.name}' timed out.", e)
                val errorJsonString =
                    json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("MCP Tool execution timed out"))))
                throwMcpToolExecutionException(
                    "MCP tool execution timed out for ${toolCall.function.name}",
                    e,
                    errorJsonString
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "MCP tool call '${toolCall.function.name}' failed with unexpected error.",
                    e
                )
                val errorJsonString =
                    json.encodeToString(JsonObject(mapOf("error" to JsonPrimitive("MCP Tool execution failed: ${e.message}"))))
                throwMcpToolExecutionException(
                    "MCP tool execution failed for ${toolCall.function.name}",
                    e,
                    errorJsonString
                )
            }
            // NOTE: Connection Manager handles closing the client on error/disconnect
        }

    override suspend fun getAvailableTools(): List<Tool> = withContext(ioDispatcher) {
        Log.d(TAG, "Getting available tools from enabled MCP servers via Connection Manager...")
        try {
            ensureToolMappingIsFresh()
            Log.d(TAG, "Returning cached available tools: ${availableTools.size}")
            availableTools
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh tools, returning cached tools", e)
            availableTools
        }
    }


    private suspend fun refreshToolMappingInternal(): Map<String, Long> = mappingMutex.withLock {
        Log.i(TAG, "Refreshing tool-to-server mapping")
        toolMappingJob?.cancelAndJoin()

        val refreshCoroutineJob = Job()
        toolMappingJob =
            CoroutineScope(ioDispatcher + refreshCoroutineJob + SupervisorJob()).launch {
                val enabledServers = settingsRepository.getAllMcpServers()
                    .first()
                    .filter { it.enabled && it.url.isNotBlank() }
                val newMap = mutableMapOf<String, Long>()
                val newTools = mutableListOf<Tool>()

                if (enabledServers.isEmpty()) {
                    Log.w(TAG, "No enabled servers found for tool mapping, Reset mapping")
                    toolToServerMap = emptyMap()
                    availableTools = emptyList()
                    return@launch
                }

                Log.d(TAG, "Fetching tools from ${enabledServers.size} enabled servers...")
                coroutineScope {
                    enabledServers.map { serverConfig ->
                        async {
                            fetchToolsForServer(
                                server = serverConfig,
                                toolMap = newMap,
                                toolsList = newTools
                            )
                        }
                    }.awaitAll()
                }
                toolToServerMap = newMap
                availableTools = newTools
                Log.i(TAG, "Tool-to-server mapping refreshed with ${newTools.size} tools")
            }

        try {
            toolMappingJob?.join()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh tool mapping for servers", e)
        }
        return toolToServerMap
    }

    private suspend fun refreshToolMappingInternal(serverIds: List<Long>) {
        Log.i(TAG, "Refreshing tool-to-server mapping for specific servers: $serverIds")
        toolMappingJob?.cancelAndJoin()

        val refreshCoroutineJob = Job()
        toolMappingJob =
            CoroutineScope(ioDispatcher + refreshCoroutineJob + SupervisorJob()).launch {
                val serversToRefresh = settingsRepository.getAllMcpServers()
                    .first()
                    .filter { it.enabled && it.url.isNotBlank() && serverIds.contains(it.id) }
                val newMap = mutableMapOf<String, Long>()
                val newTools = mutableListOf<Tool>()
                if (serversToRefresh.isEmpty()) {
                    Log.w(TAG, "No enabled servers found for tool mapping")
                    return@launch
                }

                Log.d(TAG, "Fetching tools from ${serversToRefresh.size} specified servers...")
                coroutineScope {
                    serversToRefresh.map { serverConfig ->
                        async {
                            fetchToolsForServer(
                                server = serverConfig,
                                toolMap = newMap,
                                toolsList = newTools
                            )
                        }
                    }.awaitAll()
                }

                toolToServerMap = toolToServerMap.toMutableMap().apply { putAll(newMap) }
                availableTools = availableTools.toMutableList().apply { addAll(newTools) }
                Log.i(
                    TAG,
                    "Tool-to-server mapping refreshed for specified servers with ${newTools.size} tools"
                )
                try {
                    toolMappingJob?.join()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh tool mapping for servers", e)
                }
            }
    }

    private suspend fun fetchToolsForServer(
        server: McpServer,
        toolMap: MutableMap<String, Long>,
        toolsList: MutableList<Tool>
    ) {
        Log.d(TAG, "Starting connection for server ${server.id}")
        val client = connectionManager.getOrConnectClient(server.id)
        if (client == null) {
            Log.e(TAG, "Could not connect to server ${server.id} to list tools")
            return
        }

        var retryCount = 0
        var retryDelay = INITIAL_RETRY_DELAY
        while (retryCount < MAX_RETRIES) {
            try {
                Log.d(
                    TAG,
                    "Attempting to list tools for server ${server.id}, attempt $retryCount"
                )
                val toolsResult = withTimeout(LIST_TOOLS_TIMEOUT_MS) {
                    Log.v(TAG, "Calling client.listTools() for server ${server.id}")
                    val result = client.listTools()
                    Log.v(TAG, "client.listTools() returned: $result")
                    result
                }

                if (toolsResult == null) {
                    Log.w(TAG, "No tools found on server ${server.id}, attempt $retryCount")
                    break
                }

                Log.d(TAG, "Found ${toolsResult.tools.size} tools on server ${server.id}")
                toolsResult.tools.forEach { tool ->
                    val namespacedName = createNamespacedToolName(
                        server.id,
                        tool.name
                    )
                    toolMap[namespacedName] = server.id
                    toolsList.add(tool.copy(name = namespacedName))
                }

                settingsRepository.updateToolsForServer(server.id, toolsResult.tools)
                break
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "listTools() timed out for ${server.id}, attempt $retryCount", e)
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    Log.w(TAG, "Retrying listTools for ${server.id}, delay ${retryDelay}s")
                    delay(retryDelay)
                    retryDelay *= 2
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to list tools from server ${server.id}, attempt $retryCount",
                    e
                )
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    Log.w(TAG, "Retrying listTools for ${server.id}, delay ${retryDelay}s")
                    delay(retryDelay)
                    retryDelay *= 2
                }
            }
        }

        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Failed to list tools for ${server.id} after $MAX_RETRIES attempts")
        }
    }
}