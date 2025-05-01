package xyz.dead8309.nuvo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.database.model.McpServerEntity

@Dao
interface McpServerDao {
    @Upsert
    suspend fun upsertServer(config: McpServerEntity)

    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("DELETE FROM mcp_servers where id = :configId")
    suspend fun deleteServer(configId: Long)

    @Query("UPDATE mcp_servers SET enabled = :enabled WHERE id = :configId")
    suspend fun setServerEnabled(configId: Long, enabled: Boolean)
}