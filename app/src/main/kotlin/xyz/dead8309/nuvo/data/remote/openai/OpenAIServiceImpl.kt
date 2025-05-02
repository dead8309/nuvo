package xyz.dead8309.nuvo.data.remote.openai

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.TextContent
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolCallChunk
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.chat.systemMessage
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import xyz.dead8309.nuvo.BuildConfig
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject
import io.modelcontextprotocol.kotlin.sdk.Tool as McpTool

private const val TAG = "OpenAIServiceImpl"
private val SYSTEM_PROMPT = """
    You are a helpful assistant with access to a variety of tools to answer user questions.

    ### Tool Usage Guidelines
    - Select the most relevant tool(s) to address the user's question.
    - You can use multiple tools in a single response, including running multiple steps as needed.
    - Always provide a final response after using the tools to ensure a seamless user experience.
    - If no tools are available or suitable, inform the user that you don't know the answer. Suggest they can add a tool from the setting screen.
    - If you don't know the answer, use the tools to find it or admit that you don't know.

    ### Response Format
    - Use Markdown for formatting when appropriate.
    - Base your response on the output of the tools.
    - Ensure your final answer directly addresses the user's question using the tool results.
""".trimIndent()

class OpenAIServiceImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mcpToolExecutor: McpToolExecutor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OpenAIService {

    override suspend fun getChatCompletionStream(messages: List<ChatMessage>): Flow<ChatMessage> =
        withContext(ioDispatcher) {
            val apiKey = settingsRepository.appSettingsFlow.first().openaiApiKey
            if (apiKey.isNullOrBlank()) {
                Log.e(TAG, "OpenAI API key is not configured.")
                throw Exception("Missing Api Key")
            }

            val config = OpenAIConfig(
                token = apiKey,
                logging = LoggingConfig(
                    if (BuildConfig.DEBUG) LogLevel.Body else LogLevel.None,
                    Logger.Simple
                ),
            )
            val openAiClient = OpenAI(config)

            val availableTools = try {
                Log.d(TAG, "Fetching available tools")
                mapMcpToolsToOpenAITools(mcpToolExecutor.getAvailableTools()) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching available tools", e)
                emptyList()
            }
            val systemMessage = systemMessage {
                this.content = SYSTEM_PROMPT
            }
            val sdkMessages = mapToSdkMessages(messages)
            val request = chatCompletionRequest {
                model = ModelId("gpt-4o-mini")
                this.messages = listOf(systemMessage) + sdkMessages
                tools = availableTools
            }

            val responseContentBuilder = StringBuilder()
            val toolCallBuilders = mutableMapOf<Int, ToolCallDeltaBuilder>()
            var completionChunkRole: ChatRole? = null

            return@withContext openAiClient.chatCompletions(request)
                .mapNotNull { chunk: ChatCompletionChunk ->
                    Log.d(TAG, "Received chunk: $chunk")
                    val choice = chunk.choices.firstOrNull() ?: return@mapNotNull null
                    val delta = choice.delta
                    val isStreaming = choice.finishReason == null

                    // The response's first chunk only contains the role and successive chunks
                    // role is null
                    delta?.let {
                        completionChunkRole = it.role ?: completionChunkRole
                    }

                    delta?.content?.let {
                        responseContentBuilder.append(it)
                    }

                    delta?.toolCalls?.forEach { toolCallChunk: ToolCallChunk ->
                        val index = toolCallChunk.index
                        val existingBuilder =
                            toolCallBuilders.getOrPut(index) { ToolCallDeltaBuilder() }

                        toolCallChunk.id?.let { toolId ->
                            existingBuilder.id = toolId.id
                        }
                        toolCallChunk.type?.let { existingBuilder.type = it }
                        toolCallChunk.function?.let { function ->
                            val currentArgs = existingBuilder.functionCall?.argumentsJson ?: ""
                            val newArgs = function.argumentsOrNull ?: ""
                            existingBuilder.functionCall = ToolCallDeltaBuilder.FunctionCallDelta(
                                functionName = existingBuilder.functionCall?.functionName
                                    ?: function.name,
                                argumentsJson = currentArgs + newArgs
                            )
                        }
                    }

                    val currentToolCalls = buildToolCallsFromDelta(toolCallBuilders)
                    ChatMessage(
                        sessionId = messages.firstOrNull()?.sessionId
                            ?: throw IllegalStateException("Session ID not found"),
                        role = completionChunkRole.toChatMessageRole()
                            ?: ChatMessage.Role.ASSISTANT,
                        content = responseContentBuilder.toString().ifEmpty { null },
                        timestamp = Clock.System.now(),
                        toolCalls = currentToolCalls.ifEmpty { null },
                        toolCallId = if (completionChunkRole == ChatRole.Tool) {
                            delta?.toolCallId?.id
                        } else null,
                        isStreaming = isStreaming
                    )
                }.flowOn(ioDispatcher)
                .catch { e ->
                    Log.e(TAG, "Error in OpenAI API call", e)
                    // handled by repository
                    throw e
                }
        }

    private fun buildToolCallsFromDelta(builders: Map<Int, ToolCallDeltaBuilder>): List<ChatMessage.ToolCall> {
        return builders.values.mapNotNull { builder ->
            val functionCall = builder.functionCall ?: return@mapNotNull null
            ChatMessage.ToolCall(
                id = builder.id ?: return@mapNotNull null,
                type = builder.type ?: return@mapNotNull null,
                function = ChatMessage.FunctionCall(
                    name = functionCall.functionName ?: return@mapNotNull null,
                    argumentsJson = functionCall.argumentsJson ?: return@mapNotNull null
                )
            )
        }
    }

    private data class ToolCallDeltaBuilder(
        var id: String? = null,
        var type: String? = null,
        var functionCall: FunctionCallDelta? = null
    ) {
        data class FunctionCallDelta(
            val functionName: String?,
            val argumentsJson: String?
        )
    }
}

private fun ChatRole?.toChatMessageRole(): ChatMessage.Role? = when (this) {
    ChatRole.User -> ChatMessage.Role.USER
    ChatRole.Assistant -> ChatMessage.Role.ASSISTANT
    ChatRole.System -> ChatMessage.Role.SYSTEM
    ChatRole.Tool -> ChatMessage.Role.TOOL
    // TODO: revisit this
    ChatRole.Function -> ChatMessage.Role.TOOL
    else -> null
}

private fun ChatMessage.Role.toSdkRole(): ChatRole? = when (this) {
    ChatMessage.Role.USER -> ChatRole.User
    ChatMessage.Role.ASSISTANT -> ChatRole.Assistant
    ChatMessage.Role.SYSTEM -> ChatRole.System
    ChatMessage.Role.TOOL -> ChatRole.Tool
    ChatMessage.Role.ERROR -> null
}

private fun mapToSdkMessages(messages: List<ChatMessage>): List<com.aallam.openai.api.chat.ChatMessage> {
    return messages.mapNotNull { appMessage ->
        // NOTE: Skip error messages
        val sdkRole = appMessage.role.toSdkRole() ?: return@mapNotNull null

        val toolCalls =
            if (sdkRole == ChatRole.Assistant && !appMessage.toolCalls.isNullOrEmpty()) {
                appMessage.toolCalls.map { toolCall: ChatMessage.ToolCall ->
                    ToolCall.Function(
                        id = ToolId(toolCall.id),
                        function = FunctionCall(
                            nameOrNull = toolCall.function.name,
                            argumentsOrNull = toolCall.function.argumentsJson
                        )
                    )
                }
            } else {
                null
            }

        com.aallam.openai.api.chat.ChatMessage(
            role = sdkRole,
            messageContent = appMessage.content?.let { TextContent(it) },
            toolCallId = if (sdkRole == ChatRole.Tool) {
                appMessage.toolCallId?.let { ToolId(it) }
                    ?: run { Log.e(TAG, "TOOL message missing toolCallId!"); null }
            } else null,
            name = if (sdkRole == ChatRole.Tool) {
                appMessage.name
            } else null,
            toolCalls = toolCalls,
        )
    }
}

private fun mapMcpToolsToOpenAITools(mcpTools: List<McpTool>): List<Tool>? {
    if (mcpTools.isEmpty()) {
        return null
    }
    return mcpTools.map { mcpTool ->
        Tool.function(
            name = mcpTool.name,
            description = mcpTool.description,
            parameters = Parameters.buildJsonObject {
                put("type", mcpTool.inputSchema.type)
                mcpTool.inputSchema.properties
                mcpTool.inputSchema.required
                put("properties", mcpTool.inputSchema.properties)
                putJsonArray("required") {
                    mcpTool.inputSchema.required?.map {
                        add(it)
                    }
                }
            }
        )
    }
}