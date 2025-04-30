package xyz.dead8309.nuvo.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import xyz.dead8309.nuvo.core.database.dao.ChatMessageDao
import xyz.dead8309.nuvo.core.database.dao.ChatSessionDao
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.core.model.ChatSession
import xyz.dead8309.nuvo.data.model.asDomainModel
import xyz.dead8309.nuvo.data.model.asEntity
import xyz.dead8309.nuvo.data.remote.openai.OpenAIService
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatRepositoryImpl"

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val openAIService: OpenAIService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ChatRepository {

    override fun getRecentChatSessions(): Flow<List<ChatSession>> {
        Log.d(TAG, "Getting recent chat sessions flow")
        return chatSessionDao.getAllSessionsFlow()
            .map { entities ->
                entities.map { it.asDomainModel() }
            }
            .distinctUntilChanged()
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        Log.d(TAG, "Getting messages for session: $sessionId")
        return chatMessageDao.getMessagesBySessionId(sessionId)
            .map { entities ->
                entities.map { it.asDomainModel() }
            }
            .distinctUntilChanged()
    }

    override suspend fun createChatSession(session: ChatSession): String {
        return withContext(ioDispatcher) {
            Log.d(TAG, "Creating chat session: $session")
            val entity = session.asEntity()
            chatSessionDao.upsertChatSession(entity)
            Log.d(TAG, "Session ${session.id} created")
            session.id
        }
    }

    override suspend fun saveMessage(message: ChatMessage) {
        withContext(ioDispatcher) {
            Log.d(TAG, "Saving message: ${message.id} to session: ${message.sessionId}")
            val entity = message.asEntity()
            chatMessageDao.upsertMessage(entity)
            Log.d(TAG, "Message ${message.id} saved")

            // update last modified time
            try {
                val sessionEntity = chatSessionDao.getSessionFlow(message.sessionId).first()
                if (sessionEntity != null) {
                    chatSessionDao.updateSession(sessionEntity.copy(lastModifiedTime = message.timestamp))
                    Log.d(TAG, "Updated lastModifiedTime for session: ${message.sessionId}")
                } else {
                    Log.w(
                        TAG,
                        "Could not find session: ${message.sessionId} to update lastModifiedTime"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update session lastModifiedTime for ${message.sessionId}", e)
            }
        }
    }

    override suspend fun deleteChatSession(sessionId: String) {
        withContext(ioDispatcher) {
            Log.d(TAG, "Deleting chat session: $sessionId")
            chatSessionDao.deleteSessionById(sessionId)
            Log.d(TAG, "Session $sessionId deleted")
            // messages cascaded by room
        }
    }

    override suspend fun sendMessageAndGetResponseFlow(messages: List<ChatMessage>): Flow<ChatMessage> {
        Log.d(TAG, "Sending message to AI for session: ${messages.firstOrNull()?.sessionId}")
        return openAIService.getChatCompletionStream(messages)
            .catch { e ->
                emit(
                    ChatMessage(
                        sessionId = messages.firstOrNull()?.sessionId
                            ?: throw IllegalArgumentException("Session ID not found"),
                        role = ChatMessage.Role.ERROR,
                        content = "AI Error: ${e.message ?: "Unknown"}",
                        timestamp = Clock.System.now()
                    )
                )
            }
    }

    override suspend fun updateChatSessionTitle(sessionId: String, newTitle: String) {
        withContext(ioDispatcher) {
            try {
                Log.d(TAG, "Updating chat session title: $sessionId to $newTitle")
                chatSessionDao.updateSessionTitle(sessionId, newTitle)
                Log.d(TAG, "Session title updated for $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update session title for $sessionId", e)
            }
        }
    }
}