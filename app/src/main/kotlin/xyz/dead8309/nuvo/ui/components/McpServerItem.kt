package xyz.dead8309.nuvo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

// TODO: This looks shit, comeback to it
@Composable
fun McpServerItem(
    config: McpServer,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    authStatus: AuthStatus,
    onAuthClick: () -> Unit,
    onRevokeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = null,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name.ifBlank { stringResource(R.string.settings_mcp_unnamed_server) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = config.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (config.requiresAuth) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuthStatusIndicator(authStatus)
                    when (authStatus) {
                        AuthStatus.NOT_CHECKED -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Checking...")
                            }
                        }

                        AuthStatus.REQUIRED_DISCOVERY,
                        AuthStatus.REQUIRED_REGISTRATION,
                        AuthStatus.REQUIRED_AWAITING_CALLBACK,
                        AuthStatus.REQUIRED_TOKEN_EXCHANGE -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("Authorizing...") }
                        }

                        AuthStatus.REQUIRED_USER_ACTION,
                        AuthStatus.REQUIRED_NOT_AUTHORIZED -> {
                            Button(
                                onClick = onAuthClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "Authorize",
                                )
                            }
                        }

                        AuthStatus.AUTHORIZED -> {
                            Button(
                                onClick = onRevokeClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Revoke")
                            }
                        }

                        AuthStatus.ERROR -> {
                            Button(
                                onClick = onAuthClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Retry")
                            }
                        }

                        AuthStatus.NOT_REQUIRED -> {}
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEditClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                )
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                )
            }
        }
    }
}


@Composable
fun AuthStatusIndicator(status: AuthStatus, modifier: Modifier = Modifier) {
    val (icon, color, _) = when (status) {
        AuthStatus.NOT_CHECKED -> {
            Triple(
                Icons.Default.CloudOff,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Not Checked"
            )
        }

        AuthStatus.NOT_REQUIRED -> {
            Triple(
                null,
                Color.Transparent,
                ""
            )
        }

        AuthStatus.REQUIRED_DISCOVERY,
        AuthStatus.REQUIRED_REGISTRATION,
        AuthStatus.REQUIRED_AWAITING_CALLBACK,
        AuthStatus.REQUIRED_TOKEN_EXCHANGE -> Triple(
            Icons.Default.CloudQueue,
            MaterialTheme.colorScheme.secondary,
            "Pending"
        )

        AuthStatus.REQUIRED_USER_ACTION,
        AuthStatus.REQUIRED_NOT_AUTHORIZED -> Triple(
            Icons.Default.LockOpen,
            MaterialTheme.colorScheme.secondary,
            "Needs Auth"
        )

        AuthStatus.AUTHORIZED -> Triple(
            Icons.Default.VerifiedUser,
            MaterialTheme.colorScheme.primary,
            "Authorized"
        )

        AuthStatus.ERROR -> Triple(
            Icons.Default.ErrorOutline,
            MaterialTheme.colorScheme.error,
            "Error"
        )
    }

    if (icon != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "status",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun McpServerItem_Enabled_Preview() {
    NuvoTheme {
        McpServerItem(
            config = McpServer(
                0,
                "Local Test Server",
                "http://localhost:8080/sse",
                emptyMap(),
                true
            ),
            isChecked = true,
            onCheckedChange = {},
            onEditClick = {},
            onDeleteClick = {},
            onAuthClick = {},
            onRevokeClick = {},
            authStatus = AuthStatus.NOT_REQUIRED
        )
    }
}