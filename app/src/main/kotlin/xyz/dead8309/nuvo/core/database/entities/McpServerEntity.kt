package xyz.dead8309.nuvo.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import xyz.dead8309.nuvo.core.model.AuthStatus

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

    @ColumnInfo("requires_auth", defaultValue = "0")
    val requiresAuth: Boolean = false,
    @ColumnInfo("auth_status")
    val authStatus: AuthStatus = AuthStatus.NOT_CHECKED,
    @ColumnInfo("auth_server_metadata_url", defaultValue = "NULL")
    val authorizationServerMetadataUrl: String? = null,
    @ColumnInfo("oauth_client_id", defaultValue = "NULL")
    val oauthClientId: String? = null,

    @ColumnInfo("version", defaultValue = "NULL")
    val version: String? = null,
)