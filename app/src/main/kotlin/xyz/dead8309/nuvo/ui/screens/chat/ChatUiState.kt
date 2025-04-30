package xyz.dead8309.nuvo.ui.screens.chat

import androidx.compose.runtime.Stable
import xyz.dead8309.nuvo.core.model.ChatMessage

@Stable
sealed interface ChatUiState {
    /** State while fetching the initial chat message history from the database. */
    data object LoadingHistory : ChatUiState

    /** State when message history is loaded and the app is ready/idle for user input. */
    data class Idle(val messages: List<ChatMessage>) : ChatUiState

    /** State when the user has sent a message and the app is waiting for the AI response stream to start. */
    data class WaitingForResponse(val messages: List<ChatMessage>) : ChatUiState

    /** State when the AI is actively streaming back a response. */
    data class StreamingResponse(
        val messages: List<ChatMessage>,
        /** The message currently being streamed (updated progressively). */
        val streamingMessage: ChatMessage? = null
    ) : ChatUiState

    /** State when an error occurred either loading history or during AI interaction. */
    data class Error(
        val errorMessage: String?,
        val messagesOnError: List<ChatMessage> = emptyList()
    ) : ChatUiState

    /** State when a tool call is being executed. */
    data class ExecutingToolCall(
        val messages: List<ChatMessage>,
        val toolCalls: List<ChatMessage.ToolCall>
    ) : ChatUiState
}