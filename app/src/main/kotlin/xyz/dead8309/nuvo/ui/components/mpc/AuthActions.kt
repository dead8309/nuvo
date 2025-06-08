package xyz.dead8309.nuvo.ui.components.mpc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer

@Composable
fun AuthActions(
    config: McpServer,
    onAuthClick: () -> Unit,
) {
    if (!config.requiresAuth) {
        return
    }
    // NOTE: not showing components for states other than error and not authorized
    return when (config.authStatus) {
        AuthStatus.AUTHORIZED -> {}
        // Initial
        AuthStatus.NOT_CHECKED -> {}

        // Discovered
        AuthStatus.REQUIRED_DISCOVERY,
        AuthStatus.REQUIRED_REGISTRATION,
        AuthStatus.REQUIRED_AWAITING_CALLBACK,
        AuthStatus.REQUIRED_TOKEN_EXCHANGE -> {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }


        AuthStatus.ERROR,
        AuthStatus.REQUIRED_USER_ACTION,
        AuthStatus.REQUIRED_NOT_AUTHORIZED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Authorization required",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Authorization required",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAuthClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Authorize")
                }
            }
        }

        else -> {}
    }
}