package xyz.dead8309.nuvo.core.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@Immutable
data class ChatMessage(
    val id: String = Uuid.random().toHexDashString(),
    val sessionId: String,
    val role: Role,
    /**
     * Textual content.
     * - USER/ASSISTANT/SYSTEM: The message text. (Nullable for ASSISTANT with only tool calls).
     * - TOOL: The **result** of the tool execution, typically serialized (e.g., JSON). This is what's sent back to the AI.
     */
    val content: String?,
    val timestamp: Instant,
    /** List of tool calls requested by the AI assistant. Populated for ASSISTANT role. */
    val toolCalls: List<ToolCall>? = null,
    /** ID of the tool call this message is a response to. Populated for TOOL role. */
    val toolCallId: String? = null,
    /** Optional name (e.g., function name for TOOL role). */
    val name: String? = null,
    /**
     * Structured representation of the tool's result **for UI display purposes**.
     * Populated for TOOL role messages within the app after execution.
     * The serialized version of this (or just the important data) goes into the 'content' field when sending back to the API.
     */
    val toolResult: ToolResult? = null,
    val isStreaming: Boolean = false
) {
    @Serializable
    enum class Role { USER, ASSISTANT, SYSTEM, TOOL, ERROR }

    @Immutable
    @Serializable
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall,
    )

    @Immutable
    @Serializable
    data class FunctionCall(
        val name: String,
        val argumentsJson: String
    )

    /** Represents the result returned by the application after executing a tool/function call. */
    @Immutable
    @Serializable
    data class ToolResult(
        val isSuccess: Boolean,
        val resultDataJson: String?
    )

    fun isFromAi(): Boolean = role == Role.ASSISTANT || role == Role.TOOL
}