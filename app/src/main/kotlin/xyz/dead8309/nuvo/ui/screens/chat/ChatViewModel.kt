package xyz.dead8309.nuvo.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.core.model.ChatSession
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

@Serializable
private data class GetWeatherArgs(val latitude: Float, val longitude: Float)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
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

    private fun triggerAiResponse(messages: List<ChatMessage>) {
        val sessionId = _activeSessionId.value ?: return
        Log.d(
            "ChatViewModel",
            "Triggering AI response for session $sessionId with ${messages.size} messages."
        )

        if (_aiResponseState.value !is AIResponseState.Error) {
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
            } catch (e: CancellationException) {
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
                    try {
                        when (toolCall.function.name) {
                            "get_weather" -> executeGetCurrentWeather(toolCall, sessionId)
                            else -> createErrorToolMessage(
                                toolCall,
                                sessionId,
                                "Unknown function: ${toolCall.function.name}"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "ChatViewModel",
                            "Error executing tool call ${toolCall.function.name}",
                            e
                        )
                        createErrorToolMessage(
                            toolCall,
                            sessionId,
                            "Error executing ${toolCall.function.name}: ${e.message}"
                        )
                    }
                }
            }
            val resultMessages = deferredResults.awaitAll()
            Log.d("ChatViewModel", "Finished executing tool calls")
            resultMessages
        }
    }

    private suspend fun executeGetCurrentWeather(
        toolCall: ChatMessage.ToolCall,
        sessionId: String
    ): ChatMessage {
        Log.d("ChatViewModel", "Executing get_weather tool call: ${toolCall.function.name}")
        var lat: Float? = null
        var long: Float? = null
        var resultJson: JsonElement?
        var toolResult: ChatMessage.ToolResult = ChatMessage.ToolResult(
            isSuccess = false,
            resultDataJson = null
        )
        try {
            val args = json.decodeFromString<GetWeatherArgs>(toolCall.function.argumentsJson)
            lat = args.latitude
            long = args.longitude
            val client = HttpClient {
                install(ContentNegotiation)
            }
            val response = client.get("https://api.open-meteo.com/v1/forecast") {
                parameter("latitude", lat)
                parameter("longitude", long)
                parameter("current", "temperature_2m,wind_speed_10m")
                parameter("hourly", "temperature_2m,relative_humidity_2m,wind_speed_10m")
            }
            val jsonBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val temp = jsonBody["current"]?.jsonObject?.get("temperature_2m")?.toString()


            resultJson = buildJsonObject {
                put("latitude", lat)
                put("longitude", long)
                put("temperature", temp)
            }
            toolResult = toolResult.copy(isSuccess = true)
            Log.d("ChatViewModel", "Weather tool call success for $lat, $long")
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error executing or parsing args for get_weather", e)
            resultJson = buildJsonObject {
                put("error", "Failed to get weather data for $lat, $long")
                put("message", e.message)
            }
            toolResult = toolResult.copy(isSuccess = false)
        }

        toolResult = toolResult.copy(resultDataJson = resultJson.toString())
        return ChatMessage(
            sessionId = sessionId,
            role = ChatMessage.Role.TOOL,
            content = json.encodeToString(resultJson),
            timestamp = Clock.System.now(),
            toolCallId = toolCall.id,
            name = toolCall.function.name,
            toolResult = toolResult
        )
    }


    private fun createErrorToolMessage(
        toolCall: ChatMessage.ToolCall,
        sessionId: String,
        error: String
    ): ChatMessage {
        val resultJsonString = """{"error": "$error"}"""
        val toolResult = ChatMessage.ToolResult(
            isSuccess = false,
            resultDataJson = resultJsonString
        )
        return ChatMessage(
            sessionId = sessionId,
            role = ChatMessage.Role.TOOL,
            content = resultJsonString,
            timestamp = Clock.System.now(),
            toolCallId = toolCall.id,
            name = toolCall.function.name,
            toolResult = toolResult
        )
    }

    override fun onCleared() {
        Log.d("ChatViewModel", "ViewModel cleared, cancelling jobs.")
        messageCollectionJob?.cancel()
        aiResponseJob?.cancel()
        super.onCleared()
        super.onCleared()
    }
}
