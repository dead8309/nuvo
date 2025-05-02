package xyz.dead8309.nuvo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = null
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.name.ifBlank { stringResource(R.string.settings_mcp_unnamed_server) },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = config.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(
                    R.string.settings_edit_mcp_server_desc,
                    config.name
                )
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(
                    R.string.settings_delete_mcp_server_desc,
                    config.name
                ),
                tint = MaterialTheme.colorScheme.error
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
            onDeleteClick = {}
        )
    }
}