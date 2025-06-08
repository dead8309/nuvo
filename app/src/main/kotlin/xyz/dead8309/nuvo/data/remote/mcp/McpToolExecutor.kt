package xyz.dead8309.nuvo.data.remote.mcp

import io.modelcontextprotocol.kotlin.sdk.Tool
import xyz.dead8309.nuvo.core.model.ChatMessage

/**
 * Interface for executing tools defined by the MCP protocol via connected servers.
 */
interface McpToolExecutor {
    /**
     * Executes a specific tool call identified by the AI model.
     *
     * @param toolCall The tool call details received from the AI model.
     * @return A String representing the result of the tool execution, typically formatted
     *         as a JSON string expected by the requesting AI.
     * @throws McpToolExecutionException if the execution fails, cannot find the server,
     *         or the connection is not available.
     */
    suspend fun executeTool(toolCall: ChatMessage.ToolCall): String

    /**
     * list of all tools available across all *currently enabled and connected*
     * MCP servers.
     *
     * @return A list of namespaced [Tool] definitions ready to be sent to the primary AI.
     */
    suspend fun getAvailableTools(): List<Tool>

    /**
     * Forces a refresh of the internal tool-to-server mapping. Should be called
     * when server configurations change.
     *
     * If [serverIds] is empty, it will refresh all servers.
     */
    suspend fun refreshToolMapping(serverIds: List<Long> = emptyList())

    companion object {
        fun createNamespacedToolName(
            serverId: Long,
            toolName: String
        ): String {
            return "$serverId$NAMESPACE_DELIMITER$toolName"
        }

        fun extractOriginalToolName(namespacedToolName: String): String {
            val parts = namespacedToolName.split(NAMESPACE_DELIMITER, limit = 2)
            if (parts.size != 2) {
                throwMcpToolExecutionException("Invalid namespaced tool name format: $namespacedToolName")
            }
            val toolName = parts[1]
            return toolName
        }
    }
}

/**
 * Exception thrown when a tool execution fails.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception, if any.
 * @param errorResponseJson Optional JSON response from the server, if available.
 */
internal class McpToolExecutionException(
    message: String,
    cause: Throwable? = null,
    val errorResponseJson: String? = null
) : RuntimeException(message, cause)

fun throwMcpToolExecutionException(
    message: String,
    cause: Throwable? = null,
    errorResponseJson: String? = null
): Nothing {
    throw McpToolExecutionException(message, cause, errorResponseJson)
}