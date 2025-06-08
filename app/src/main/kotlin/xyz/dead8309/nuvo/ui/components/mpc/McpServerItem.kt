package xyz.dead8309.nuvo.ui.components.mpc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.remote.mcp.client.ConnectionState
import xyz.dead8309.nuvo.ui.components.Switch
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerItem(
    modifier: Modifier = Modifier,
    config: McpServer,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAuthClick: () -> Unit,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    tools: List<McpToolEntity> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .clickable { expanded = !expanded },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "MCP Server",
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.name.ifBlank { stringResource(R.string.settings_mcp_unnamed_server) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (config.enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                                fontWeight = if (config.enabled) FontWeight.Bold else FontWeight.Normal
                            )
                        )

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = config.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Construction,
                    contentDescription = "Tools",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (tools.isEmpty()) {
                        stringResource(R.string.settings_mcp_no_tools)
                    } else {
                        stringResource(R.string.tools) + " (${tools.size})"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = if (tools.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                tools.forEach { tool ->
                    Text(
                        text = tool.originalToolName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionIndicator(
                    state = connectionState,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isChecked,
                    onClick = {
                        onCheckedChange(!isChecked)
                    },
                )
            }

            AuthActions(
                config = config,
                onAuthClick = onAuthClick,
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                McpActions(
                    onEdit = onEditClick,
                    onDelete = onDeleteClick
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun McpServerItemPreviewError() {
    NuvoTheme {
        McpServerItem(
            config = McpServer(
                id = 1L,
                name = "MCP Server",
                url = "https://example.org",
                headers = emptyMap(),
                enabled = true,
                requiresAuth = true,
                authStatus = AuthStatus.REQUIRED_NOT_AUTHORIZED
            ),
            isChecked = true,
            onCheckedChange = {},
            onEditClick = {},
            onDeleteClick = {},
            onAuthClick = {},
            connectionState = ConnectionState.CONNECTED,
            tools = listOf(
                McpToolEntity(
                    id = 1L,
                    serverId = 1L,
                    originalToolName = "search_documents",
                    description = "Search documents",
                    inputSchemaJson = null,
                    enabled = true
                ),
                McpToolEntity(
                    id = 2L,
                    serverId = 1L,
                    originalToolName = "fetch_image",
                    description = "Fetch an image",
                    inputSchemaJson = null,
                    enabled = true
                ),
                McpToolEntity(
                    id = 3L,
                    serverId = 1L,
                    originalToolName = "analyze_text",
                    description = "Analyze text",
                    inputSchemaJson = null,
                    enabled = true
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun McpServerItemPreviewLoading() {
    NuvoTheme {
        McpServerItem(
            config = McpServer(
                id = 1L,
                name = "MCP Server",
                url = "https://example.org",
                headers = emptyMap(),
                enabled = true,
                requiresAuth = true,
                authStatus = AuthStatus.REQUIRED_DISCOVERY
            ),
            isChecked = true,
            onCheckedChange = {},
            onEditClick = {},
            onDeleteClick = {},
            onAuthClick = {},
            connectionState = ConnectionState.FAILED,
            tools = emptyList()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun McpServerItemPreview() {
    NuvoTheme {
        Column {
            McpServerItem(
                config = McpServer(
                    id = 1L,
                    name = "MCP Server",
                    url = "https://example.org",
                    headers = emptyMap(),
                    enabled = true,
                    requiresAuth = true,
                    authStatus = AuthStatus.AUTHORIZED
                ),
                isChecked = true,
                onCheckedChange = {},
                onEditClick = {},
                onDeleteClick = {},
                onAuthClick = {},
                connectionState = ConnectionState.CONNECTED,
                tools = emptyList()
            )

        }
    }
}
