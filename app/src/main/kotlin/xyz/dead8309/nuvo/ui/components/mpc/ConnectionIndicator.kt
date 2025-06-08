package xyz.dead8309.nuvo.ui.components.mpc

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.data.remote.mcp.client.ConnectionState

@Composable
fun ConnectionIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    // NOTE: not showing anything for FAILED state as AuthActions handles it
    if (state == ConnectionState.FAILED) {
        Spacer(Modifier.height(1.dp))
        return
    }

    if (state == ConnectionState.CONNECTING) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        return
    }

    val color = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.secondary
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        else -> return
    }

    val resId = when (state) {
        ConnectionState.CONNECTED -> R.string.connected
        ConnectionState.DISCONNECTED -> R.string.disconnected
        else -> return
    }

    Text(
        modifier = modifier,
        text = stringResource(resId),
        style = MaterialTheme.typography.titleSmall,
        color = color,
    )
}

