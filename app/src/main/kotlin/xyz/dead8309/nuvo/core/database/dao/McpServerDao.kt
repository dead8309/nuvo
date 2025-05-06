package xyz.dead8309.nuvo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.database.model.McpServerEntity
import xyz.dead8309.nuvo.core.model.AuthStatus

@Dao
interface McpServerDao {
    @Upsert
    suspend fun upsertServer(config: McpServerEntity): Long

    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :configId")
    suspend fun getServerById(configId: Long): McpServerEntity?

    @Query("DELETE FROM mcp_servers where id = :configId")
    suspend fun deleteServer(configId: Long)

    @Query("UPDATE mcp_servers SET enabled = :enabled WHERE id = :configId")
    suspend fun setServerEnabled(configId: Long, enabled: Boolean)

    @Query("UPDATE mcp_servers SET requires_auth = :requiresAuth, auth_status = :authStatus, auth_server_metadata_url = :metadataUrl WHERE id = :configId")
    suspend fun updateServerAuthDetails(
        configId: Long,
        requiresAuth: Boolean,
        authStatus: AuthStatus,
        metadataUrl: String?
    )

    @Query("UPDATE mcp_servers SET oauth_client_id = :clientId WHERE id = :configId")
    suspend fun updateServerClientId(configId: Long, clientId: String?)

    @Query("UPDATE mcp_servers SET auth_status = :authStatus WHERE id = :configId")
    suspend fun updateServerAuthStatus(configId: Long, authStatus: AuthStatus)
}