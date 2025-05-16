package xyz.dead8309.nuvo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.database.entities.ChatSessionEntity

@Dao
interface ChatSessionDao {
    @Upsert
    suspend fun upsertChatSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    fun getSessionFlow(sessionId: String): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions ORDER BY last_modified_time DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSessionEntity>>

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String)
}
