package xyz.dead8309.nuvo.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("url")
    val url: String,
    @ColumnInfo("headers")
    val headers: Map<String, String>,
    @ColumnInfo("enabled", defaultValue = "0")
    val enabled: Boolean,
)