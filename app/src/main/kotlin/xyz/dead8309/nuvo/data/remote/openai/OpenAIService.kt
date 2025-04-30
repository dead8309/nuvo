package xyz.dead8309.nuvo.data.remote.openai

import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.model.ChatMessage

interface OpenAIService {
    /**
     * Sends a list of messages representing the conversation history to the OpenAI API
     * and returns a Flow of response text chunks as they stream back.
     *
     * The implementation is responsible for fetching the current API key and configuration
     * from the SettingsRepository before making the request.
     *
     * @param messages The history of messages in the current chat session.
     * @return A Flow emitting string chunks of the AI's response. It can emit multiple times
     *         as the response streams in. The Flow will complete when the response is finished,
     *         or throw an exception on error.
     * @throws IllegalStateException if the API key is not configured.
     * @throws Exception for network errors or API errors from OpenAI.
     */
    suspend fun getChatCompletionStream(messages: List<ChatMessage>): Flow<ChatMessage>
}
