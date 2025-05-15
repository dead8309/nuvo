package xyz.dead8309.nuvo.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.modelcontextprotocol.kotlin.sdk.Tool

@Entity(
    tableName = "mcp_tools",
    foreignKeys = [
        ForeignKey(
            entity = McpServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["server_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["server_id"])]
)
data class McpToolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "server_id")
    val serverId: Long,

    @ColumnInfo(name = "original_tool_name")
    val originalToolName: String,

    @ColumnInfo(name = "description", defaultValue = "NULL")
    val description: String? = null,

    @ColumnInfo(name = "input_schema", defaultValue = "NULL")
    val inputSchemaJson: Tool.Input? = null,

    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true
)