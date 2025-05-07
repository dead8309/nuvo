package xyz.dead8309.nuvo.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.core.model.ChatSession
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor
import xyz.dead8309.nuvo.data.repository.ChatRepository
import xyz.dead8309.nuvo.navigation.ChatRoute
import javax.inject.Inject

// internal state
private sealed interface AIResponseState {
    data object Idle : AIResponseState
    data object Loading : AIResponseState
    data class Streaming(val partialMessage: ChatMessage?) : AIResponseState
    data class Error(val message: String?) : AIResponseState
    data class ExecutingToolCall(val toolCalls: List<ChatMessage.ToolCall>) : AIResponseState
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val mcpToolExecutor: McpToolExecutor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val json: Json = Json {
        isLenient = true
        prettyPrint = true
    }

    private val chatArgs: ChatRoute = savedStateHandle.toRoute()
    private val initialChatSessionId: String? = chatArgs.chatSessionId
    private val initialPrompt: String? = chatArgs.prompt

    private val _activeSessionId = MutableStateFlow<String?>(null)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _aiResponseState = MutableStateFlow<AIResponseState>(AIResponseState.Idle)

    val uiState: StateFlow<ChatUiState> = combine(
        _messages,
        _aiResponseState
    ) { messages, aiState ->
        Log.d("ChatViewModel", "Messages: $messages, AI State: $aiState")
        when (aiState) {
            is AIResponseState.Idle -> ChatUiState.Idle(messages)
            is AIResponseState.Loading -> ChatUiState.WaitingForResponse(messages)
            is AIResponseState.Streaming -> ChatUiState.StreamingResponse(
                messages,
                aiState.partialMessage
            )

            is AIResponseState.Error -> ChatUiState.Error(aiState.message, messages)
            is AIResponseState.ExecutingToolCall -> ChatUiState.ExecutingToolCall(
                messages,
                aiState.toolCalls
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState.LoadingHistory
    )

    private var messageCollectionJob: Job? = null
    private var aiResponseJob: Job? = null

    init {
        when {
            // Navigated from the drawer
            initialChatSessionId != null -> {
                Log.d("ChatViewModel", "Loading existing session: $initialChatSessionId")
                _activeSessionId.value = initialChatSessionId
                listenForChatUpdates(initialChatSessionId)
            }
            // Navigated from a new chat action
            initialPrompt != null -> {
                Log.d("ChatViewModel", "Starting new session with prompt: $initialPrompt")
                startNewSessionAndSendMessage(initialPrompt)
            }

            else -> {
                Log.wtf(
                    "ChatViewModel",
                    "No session ID or prompt provided, loading recent sessions."
                )
                _messages.value = emptyList()
                _aiResponseState.value =
                    AIResponseState.Error("Cannot load chat. Invalid navigation state.")
            }
        }

        PaymentEventBus.events
            .onEach { event ->
                if (_activeSessionId.value == null) {
                    Log.w(
                        "ChatViewModel",
                        "Ignoring payment event, no active session in this ViewModel instance."
                    )
                    return@onEach
                }
                if (event.originalToolCallId == null) return@onEach

                if (event.originalToolCallId.isBlank()) {
                    _aiResponseState.value =
                        AIResponseState.Error("Payment processed, but linking to original request failed.")
                    return@onEach
                }

                val messagesForCurrentSession = _messages.value
                delay(100)
                val isEventRelevantToThisSession = messagesForCurrentSession.any { msg ->
                    msg.toolCalls?.any { tc -> tc.id == event.originalToolCallId } == true
                }

                if (!isEventRelevantToThisSession) {
                    Log.w(
                        "ChatViewModel",
                        "Payment event for toolCallId ${event.originalToolCallId} does not seem to belong to current session ${_activeSessionId.value}. If this is unexpected, check MainActivity navigation and event posting."
                    )
                }


                if (event.status == "success") {
                    handleSuccessfulPayment(event.originalToolCallId, event.stripeSessionId)
                } else {
                    handleFailedPayment(
                        event.originalToolCallId,
                        event.status,
                        event.stripeSessionId
                    )
                }
            }
            .catch { e -> Log.e("ChatViewModel", "Error in PaymentEventBus flow", e) }
            .launchIn(viewModelScope)
    }

    private fun handleSuccessfulPayment(originalToolCallId: String, stripeSessionId: String?) {
        val currentSessionId = _activeSessionId.value ?: return
        Log.i(
            "ChatViewModel",
            "Handling successful payment for originalToolCallId: $originalToolCallId in session: $currentSessionId"
        )

        val originalAssistantMessage =
            _messages.value.find { msg -> msg.toolCalls?.any { it.id == originalToolCallId } == true }
        val originalToolName =
            originalAssistantMessage?.toolCalls?.find { it.id == originalToolCallId }?.function?.name
                ?: throw IllegalStateException(
                    "Original assistant message not found for tool call ID: $originalToolCallId"
                )

        val paymentToolSuccessMessage = ChatMessage(
            sessionId = currentSessionId,
            role = ChatMessage.Role.ASSISTANT,
            content = buildJsonObject {
                put("payment_status", "successful")
                stripeSessionId?.let { put("stripe_session_id", it) }
                put("message", "Payment confirmed by client. Please verify.")
            }.toString(),
            timestamp = Clock.System.now(),
            toolCallId = originalToolCallId,
            name = originalToolName,
            toolResult = ChatMessage.ToolResult(
                isSuccess = true,
                resultDataJson = buildJsonObject {
                    stripeSessionId?.let {
                        put(
                            "stripe_session_id",
                            it
                        )
                    }
                }.toString()
            )
        )

        viewModelScope.launch {
            try {
                chatRepository.saveMessage(paymentToolSuccessMessage)
                Log.d("ChatViewModel", "Saved 'payment successful' TOOL message. Triggering AI.")
                delay(150)
                triggerAiResponse(
                    _messages.value,
                    isContinuationAfterPaymentSetup = true
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun handleFailedPayment(
        originalToolCallId: String,
        paymentStatus: String,
        stripeSessionId: String?
    ) {
        val currentSessionId = _activeSessionId.value ?: return

        val originalAssistantMessage =
            _messages.value.find { msg -> msg.toolCalls?.any { it.id == originalToolCallId } == true }
        val originalToolName =
            originalAssistantMessage?.toolCalls?.find { it.id == originalToolCallId }?.function?.name
                ?: throw IllegalStateException(
                    "Original assistant message not found for tool call ID: $originalToolCallId"
                )

        val paymentToolFailureMessage = ChatMessage(
            sessionId = currentSessionId,
            role = ChatMessage.Role.TOOL,
            content = buildJsonObject {
                put("payment_status", paymentStatus)
                stripeSessionId?.let { put("stripe_session_id", it) }
                put("message", "Payment was not completed ($paymentStatus).")
            }.toString(),
            timestamp = Clock.System.now(),
            toolCallId = originalToolCallId,
            name = originalToolName,
            toolResult = ChatMessage.ToolResult(
                isSuccess = false,
                resultDataJson = buildJsonObject {
                    put(
                        "reason",
                        "Payment $paymentStatus by user."
                    )
                }.toString()
            )
        )
        viewModelScope.launch {
            chatRepository.saveMessage(paymentToolFailureMessage)
            _aiResponseState.value =
                AIResponseState.Error("Payment was $paymentStatus.")
            delay(150)
            triggerAiResponse(_messages.value, isContinuationAfterPaymentSetup = true)
        }
    }

    private fun listenForChatUpdates(sessionId: String) {
        Log.d("ChatViewModel", "Starting to listen for updates on session: $sessionId")
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId)
                .map { messages -> messages.sortedBy { it.timestamp } }
                .catch { e ->
                    Log.e("ChatViewModel", "Error in message stream for session $sessionId", e)
                    _aiResponseState.value = AIResponseState.Error("Failed to load messages.")
                }
                .collectLatest { messages ->
                    Log.v(
                        "ChatViewModel",
                        "Received ${messages.size} messages for session $sessionId from DB stream"
                    )
                    _messages.value = messages
                    if (_aiResponseState.value !is AIResponseState.Loading && _aiResponseState.value !is AIResponseState.Streaming) {
                        _aiResponseState.value = AIResponseState.Idle
                    }
                }
        }
    }


    private fun startNewSessionAndSendMessage(prompt: String) {
        aiResponseJob?.cancel()
        viewModelScope.launch {
            _aiResponseState.value = AIResponseState.Loading
            try {
                val newSession = ChatSession(
                    title = prompt.take(40).let { if (it.length == 40) "$it..." else it },
                    createdTime = Clock.System.now(),
                    lastModifiedTime = Clock.System.now()
                )
                _activeSessionId.value = newSession.id
                val userMessage = ChatMessage(
                    sessionId = newSession.id,
                    role = ChatMessage.Role.USER,
                    content = prompt,
                    timestamp = Clock.System.now()
                )

                chatRepository.createChatSession(newSession)
                Log.d("ChatViewModel", "Created new session: ${newSession.id}")
                chatRepository.saveMessage(userMessage)
                Log.d("ChatViewModel", "Saved first message: ${userMessage.id}")

                _messages.value = listOf(userMessage)
                // Start listening for updates for the new session
                listenForChatUpdates(newSession.id)
                triggerAiResponse(listOf(userMessage))
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error starting new session with prompt", e)
                _aiResponseState.value = AIResponseState.Error("Could not start chat.")
            }
        }
    }

    fun sendMessage(message: String) {
        val sessionId = _activeSessionId.value ?: run {
            Log.e("ChatViewModel", "sendMessage called but activeSessionId is null")
            if (_aiResponseState.value !is AIResponseState.Error) {
                _aiResponseState.value =
                    AIResponseState.Error("Cannot send message, no active session.")
            }
            return
        }

        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = ChatMessage.Role.USER,
            content = message,
            timestamp = Clock.System.now()
        )

        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Saving user message: ${userMessage.id}")
                chatRepository.saveMessage(userMessage)

                _aiResponseState.value = AIResponseState.Loading
                delay(50)
                val updatedMessages = _messages.value

                if (updatedMessages.isNotEmpty()) {
                    triggerAiResponse(updatedMessages)
                } else {
                    // This should not happen if saving worked
                    Log.e("ChatViewModel", "Message list empty after saving, cannot trigger AI.")
                    _aiResponseState.value = AIResponseState.Error("Failed to send message.")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving user message or triggering AI", e)
                _aiResponseState.value = AIResponseState.Error("Failed to send message.")
            }
        }
    }

    private fun triggerAiResponse(
        messages: List<ChatMessage>,
        isContinuationAfterPaymentSetup: Boolean = false
    ) {
        val sessionId = _activeSessionId.value ?: return
        Log.d(
            "ChatViewModel",
            "Triggering AI response for session $sessionId with ${messages.size} messages."
        )

        if (_aiResponseState.value !is AIResponseState.Error || isContinuationAfterPaymentSetup) {
            _aiResponseState.value = AIResponseState.Loading
        }

        aiResponseJob?.cancel()
        aiResponseJob = viewModelScope.launch {
            try {
                var streamingMessage: ChatMessage? = null
                chatRepository.sendMessageAndGetResponseFlow(messages).collect { message ->
                    Log.w(
                        "ChatViewModel",
                        "Received message from AI: ${message.id} (${message.role})"
                    )
                    streamingMessage = if (streamingMessage == null || !message.isStreaming) {
                        message
                    } else {
                        streamingMessage!!.copy(
                            content = message.content,
                            toolCalls = message.toolCalls,
                            timestamp = Clock.System.now()
                        )
                    }
                    _aiResponseState.value = AIResponseState.Streaming(streamingMessage)

                    if (!message.isStreaming) {
                        Log.d("ChatViewModel", "AI stream completed for $sessionId.")
                        Log.d("ChatViewModel", "Saving final AI message: ${message.id}")
                        chatRepository.saveMessage(message)

                        if (!message.toolCalls.isNullOrEmpty()) {
                            Log.i(
                                "ChatViewModel",
                                "Ai requested tool calls:  ${message.toolCalls}"
                            )
                            _aiResponseState.value = AIResponseState.ExecutingToolCall(
                                message.toolCalls
                            )
                            val toolResultsMessages =
                                executeToolCalls(message.toolCalls, sessionId)
                            toolResultsMessages.forEach { chatRepository.saveMessage(it) }
                            delay(50)
                            val updatedMessages = _messages.value

                            if (updatedMessages.isNotEmpty()) {
                                triggerAiResponse(updatedMessages)
                            } else {
                                Log.e(
                                    "ChatViewModel",
                                    "Message list empty after saving tool results, cannot trigger AI."
                                )
                                _aiResponseState.value =
                                    AIResponseState.Error("Failed to send message.")
                            }
                        } else {
                            _aiResponseState.value = AIResponseState.Idle
                        }
                    }
                }
            } catch (_: CancellationException) {
                Log.d("ChatViewModel", "AI response job cancelled for $sessionId")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error collecting AI response stream for $sessionId", e)
                _aiResponseState.value =
                    AIResponseState.Error(e.message ?: "Failed to get AI response.")
            }
        }
    }

    fun cancelStreaming() {
        aiResponseJob?.cancel()
        aiResponseJob = null
        _aiResponseState.value = AIResponseState.Idle
    }

    private suspend fun executeToolCalls(
        toolCalls: List<ChatMessage.ToolCall>,
        sessionId: String
    ): List<ChatMessage> {
        Log.d("ChatViewModel", "Executing ${toolCalls.size} tool calls...")
        return coroutineScope {
            val deferredResults = toolCalls.map { toolCall ->
                async {
                    var result: String
                    var toolResult = ChatMessage.ToolResult(
                        isSuccess = false,
                        resultDataJson = null
                    )
                    try {
                        result = mcpToolExecutor.executeTool(toolCall)
                        toolResult = toolResult.copy(
                            isSuccess = true,
                            resultDataJson = result
                        )
                        Log.d(
                            "ChatViewModel",
                            "Tool call ${toolCall.function.name} executed with result: $result"
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "ChatViewModel",
                            "Error executing tool call ${toolCall.function.name}",
                            e
                        )
                        result = e.message ?: "Unknown error"
                        toolResult = toolResult.copy(
                            isSuccess = false,
                            resultDataJson = e.message
                        )
                    }
                    ChatMessage(
                        sessionId = sessionId,
                        role = ChatMessage.Role.TOOL,
                        content = json.encodeToString(result),
                        timestamp = Clock.System.now(),
                        toolCallId = toolCall.id,
                        name = toolCall.function.name,
                        toolResult = toolResult
                    )
                }
            }
            val resultMessages = deferredResults.awaitAll()
            Log.d("ChatViewModel", "Finished executing tool calls")
            resultMessages
        }
    }

    override fun onCleared() {
        Log.d("ChatViewModel", "ViewModel cleared, cancelling jobs.")
        messageCollectionJob?.cancel()
        aiResponseJob?.cancel()
        super.onCleared()
    }
}
