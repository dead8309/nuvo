package xyz.dead8309.nuvo.data.repository

import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.core.model.ChatSession

/**
 * Interface for accessing and managing chat data, including sessions and messages.
 */
interface ChatRepository {

    /**
     * Gets a flow of all chat sessions, typically ordered by most recent start time.
     * @return A Flow emitting a list of ChatSession domain models.
     */
    fun getRecentChatSessions(): Flow<List<ChatSession>>

    /**
     * Gets a flow of all messages belonging to a specific chat session, ordered chronologically.
     * @param sessionId The ID of the chat session.
     * @return A Flow emitting a list of ChatMessage domain models for the given session.
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Creates a new chat session in the persistent storage.
     * @param session The ChatSession domain model to create (ID should ideally be pre-generated).
     * @return The ID of the created session.
     * @throws Exception if saving fails.
     */
    suspend fun createChatSession(session: ChatSession): String

    /**
     * Saves or updates a single chat message in the persistent storage.
     * @param message The ChatMessage domain model to save.
     * @throws Exception if saving fails.
     */
    suspend fun saveMessage(message: ChatMessage)

    /**
     * Deletes a chat session and all associated messages (due to cascade delete).
     * @param sessionId The ID of the chat session to delete.
     * @throws Exception if deletion fails.
     */
    suspend fun deleteChatSession(sessionId: String)

    /**
     * Updates the title of an existing chat session.
     * @param sessionId The ID of the chat session to update.
     * @param newTitle The new title for the chat session.
     */
    suspend fun updateChatSessionTitle(sessionId: String, newTitle: String)


    /**
     * Sends a list of messages (representing history + new prompt) to the AI service
     * and returns a Flow of the AI's response text chunks as they flow back.
     * Handles fetching the API key internally via the OpenAiService implementation.
     *
     * @param messages The conversation history including the latest user message.
     * @return A Flow emitting string chunks of the AI's response.
     */
    suspend fun sendMessageAndGetResponseFlow(messages: List<ChatMessage>): Flow<ChatMessage>
}
