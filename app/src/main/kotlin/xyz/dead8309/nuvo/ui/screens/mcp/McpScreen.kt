package xyz.dead8309.nuvo.ui.screens.mcp

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.remote.mcp.client.ConnectionState
import xyz.dead8309.nuvo.ui.components.mpc.AddEditMcpServerDialog
import xyz.dead8309.nuvo.ui.components.mpc.McpServerItem
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun McpScreen(
    modifier: Modifier = Modifier,
    viewModel: McpViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var serverIdForAuthFlow by remember { mutableStateOf<Long?>(null) }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultIntent = result.data
        val currentSeverId = serverIdForAuthFlow
        serverIdForAuthFlow = null // reset active

        if (currentSeverId == null) {
            Log.e("McpScreen", "Auth Result Received but no active serverId")
            return@rememberLauncherForActivityResult
        }

        if (result.resultCode == Activity.RESULT_OK && resultIntent != null) {
            val response = AuthorizationResponse.fromIntent(resultIntent)
            val error = AuthorizationException.fromIntent(resultIntent)
            Log.d("McpScreen", "Auth Result Received: $response, $error")
            viewModel.handleAuthorizationResponse(currentSeverId, response, error)
        } else {
            val error = AuthorizationException.fromIntent(resultIntent)
            Log.e("McpScreen", "Auth Result Failed: $error")
            if (error != AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW) {
                viewModel.handleAuthorizationResponse(currentSeverId, null, error)
            } else {
                viewModel.updateStatusTo(
                    currentSeverId,
                    AuthStatus.REQUIRED_USER_ACTION
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.authIntentChannel.collect { (id, intent) ->
            serverIdForAuthFlow = id
            try {
                authorizationLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("McpScreen", "No Activity found to handle auth intent", e)
                viewModel.updateStatusTo(
                    id,
                    AuthStatus.ERROR
                )
            }
        }
    }

    LaunchedEffect(state.userMessage) {
        if (state.userMessage != null) {
            snackbarHostState.showSnackbar(state.userMessage!!)
            viewModel.userMessageShown()
        }
    }

    // trigger initial discovery
    LaunchedEffect(state.mcpServers) {
        state.mcpServers.filter { it.authStatus == AuthStatus.NOT_CHECKED && it.enabled }
            .forEach { server ->
                Log.d("McpScreen", "Triggering initial discovery for server: ${server.id}")
                viewModel.checkAndTriggerDiscovery(server.id)
            }
    }

    McpScreenContent(
        modifier = modifier,
        state = state,
        onSaveMcpConfig = viewModel::addOrUpdateMcpServer,
        onDeleteMcpConfig = viewModel::deleteMcpServer,
        onToggleMcpConfig = { id, enabled ->
            viewModel.setMcpServerEnabled(id, enabled)
            // re-trigger discovery if enabled
            if (enabled) viewModel.checkAndTriggerDiscovery(id)
        },
        onInitiateAuth = viewModel::prepareAuthorizationIntent,
    )
}

@Composable
private fun McpScreenContent(
    modifier: Modifier = Modifier,
    state: McpUiState = McpUiState(),
    onSaveMcpConfig: (McpServer) -> Unit = {},
    onDeleteMcpConfig: (Long) -> Unit = {},
    onToggleMcpConfig: (Long, Boolean) -> Unit = { _, _ -> },
    onInitiateAuth: (Long) -> Unit = {},
) {
    var showAddEditMcpDialog by rememberSaveable { mutableStateOf(false) }
    var mcpConfigToEdit by remember { mutableStateOf<McpServer?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = (100 + 16).dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.mcpServers.isEmpty()) {
                item {
                    Text(
                        text = "No MCP servers configured",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            items(state.mcpServers, key = { it.id }) { serverConfig ->
                val serverTools = state.serverTools[serverConfig.id] ?: emptyList()
                McpServerItem(
                    config = serverConfig,
                    isChecked = serverConfig.enabled,
                    onCheckedChange = { isEnabled ->
                        onToggleMcpConfig(serverConfig.id, isEnabled)
                    },
                    onEditClick = {
                        mcpConfigToEdit = serverConfig
                        showAddEditMcpDialog = true
                    },
                    onDeleteClick = { onDeleteMcpConfig(serverConfig.id) },
                    onAuthClick = { onInitiateAuth(serverConfig.id) },
                    connectionState = state.connectionStates[serverConfig.id]
                        ?: ConnectionState.DISCONNECTED,
                    tools = serverTools
                )
            }
        }

        FloatingActionButton(
            onClick = {
                mcpConfigToEdit = null
                showAddEditMcpDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(90.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add MCP Server",
                modifier = Modifier.size(26.dp)
            )
        }
    }

    if (showAddEditMcpDialog) {
        AddEditMcpServerDialog(
            existingConfig = mcpConfigToEdit,
            onDismiss = { showAddEditMcpDialog = false },
            onSave = { config ->
                val config = if (mcpConfigToEdit != null) {
                    config.copy(
                        id = mcpConfigToEdit!!.id,
                        enabled = mcpConfigToEdit!!.enabled,
                        // NOTE: if url changes, reset auth status
                        authStatus = if (config.url != mcpConfigToEdit!!.url) {
                            AuthStatus.NOT_CHECKED
                        } else {
                            mcpConfigToEdit!!.authStatus
                        }
                    )
                } else {
                    // set defaults
                    config.copy(enabled = true, authStatus = AuthStatus.NOT_CHECKED)
                }
                onSaveMcpConfig(config)
                showAddEditMcpDialog = false
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun McpScreenPreview() {
    NuvoTheme {
        McpScreenContent(
            state = McpUiState(),
        )
    }
}
