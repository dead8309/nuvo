package xyz.dead8309.nuvo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity

@Dao
interface McpToolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<McpToolEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: McpToolEntity)

    @Query("SELECT * FROM mcp_tools WHERE server_id = :serverId ORDER by original_tool_name ASC")
    fun getToolsByServerId(serverId: Long): Flow<List<McpToolEntity>>

    @Query(
        "SELECT T.* FROM mcp_tools AS T " +
                "INNER JOIN mcp_servers AS S ON T.server_id = S.id " +
                "WHERE S.enabled = 1 AND T.enabled = 1"
    )
    fun getAllEnabledToolsFromEnabledServers(): Flow<List<McpToolEntity>>

    @Query("UPDATE mcp_tools SET enabled = :enabled WHERE id = :toolId")
    fun setToolEnabled(toolId: Long, enabled: Boolean)

    @Query("SELECT * FROM mcp_tools WHERE id = :toolId")
    suspend fun getToolById(toolId: Long): McpToolEntity?

    @Query("DELETE FROM mcp_tools WHERE server_id = :serverId")
    suspend fun deleteToolsForServer(serverId: Long)
}