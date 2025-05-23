package xyz.dead8309.nuvo.ui.screens.settings

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.ui.components.AddEditMcpServerDialog
import xyz.dead8309.nuvo.ui.components.McpServerItem
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LocalContext.current
    var serverIdForAuthFlow by remember { mutableStateOf<Long?>(null) }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultIntent = result.data
        val currentSeverId = serverIdForAuthFlow
        serverIdForAuthFlow = null // reset active

        if (currentSeverId == null) {
            Log.e("SettingsScreen", "Auth Result Received but no active serverId")
            return@rememberLauncherForActivityResult
        }

        if (result.resultCode == Activity.RESULT_OK && resultIntent != null) {
            val response = AuthorizationResponse.fromIntent(resultIntent)
            val error = AuthorizationException.fromIntent(resultIntent)
            Log.d("SettingsScreen", "Auth Result Received: $response, $error")
            viewModel.handleAuthorizationResponse(currentSeverId, response, error)
        } else {
            val error = AuthorizationException.fromIntent(resultIntent)
            Log.e("SettingsScreen", "Auth Result Failed: $error")
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
                Log.e("SettingsScreen", "No Activity found to handle auth intent", e)
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
                Log.d("SettingsScreen", "Triggering initial discovery for server: ${server.id}")
                viewModel.checkAndTriggerDiscovery(server.id)
            }
    }

    SettingsScreen(
        modifier = modifier,
        state = state,
        onOpenAIApiKeyChange = viewModel::updateOpenAiApiKey,
        onSaveMcpConfig = viewModel::addOrUpdateMcpServer,
        onDeleteMcpConfig = viewModel::deleteMcpServer,
        onToggleMcpConfig = { id, enabled ->
            viewModel.setMcpServerEnabled(id, enabled)
            // re-trigger discovery if enabled
            if (enabled) viewModel.checkAndTriggerDiscovery(id)
        },
        onInitiateAuth = viewModel::prepareAuthorizationIntent,
        onRevokeAuth = viewModel::clearOAuthDetails
    )
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsUiState = SettingsUiState(),
    onOpenAIApiKeyChange: (String) -> Unit = {},
    onSaveMcpConfig: (McpServer) -> Unit = {},
    onDeleteMcpConfig: (Long) -> Unit = {},
    onToggleMcpConfig: (Long, Boolean) -> Unit = { _, _ -> },
    onInitiateAuth: (Long) -> Unit = {},
    onRevokeAuth: (Long) -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showAddEditMcpDialog by rememberSaveable { mutableStateOf(false) }
    var mcpConfigToEdit by rememberSaveable { mutableStateOf<McpServer?>(null) }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.settings_openai_api_key_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            OutlinedTextField(
                value = state.openAiApiKey,
                onValueChange = onOpenAIApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key_hint)) },
                placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() }
                ),
                trailingIcon = {
                    val image =
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    val description =
                        if (passwordVisible) stringResource(R.string.settings_hide_api_key) else stringResource(
                            R.string.settings_show_api_key
                        )
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                }
            )
            HorizontalDivider()
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MCP Servers",
                    style = MaterialTheme.typography.titleLarge
                )
                Button(onClick = {
                    mcpConfigToEdit = null
                    showAddEditMcpDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add MCP Server",
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Add Server")
                }
            }
        }

        if (state.mcpServers.isEmpty()) {
            item {
                Text(
                    text = "No MCP servers configured",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            items(state.mcpServers, key = { it.id }) { serverConfig ->
                McpServerItem(
                    config = serverConfig,
                    isChecked = serverConfig.enabled,
                    onCheckedChange = { isEnabled ->
                        onToggleMcpConfig(
                            serverConfig.id,
                            isEnabled
                        )
                    },
                    onEditClick = {
                        mcpConfigToEdit = serverConfig
                        showAddEditMcpDialog = true
                    },
                    onDeleteClick = { onDeleteMcpConfig(serverConfig.id) },
                    authStatus = serverConfig.authStatus,
                    onAuthClick = { onInitiateAuth(serverConfig.id) },
                    onRevokeClick = { onRevokeAuth(serverConfig.id) },
                )
                HorizontalDivider()
            }
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
private fun SettingsScreenPreview() {
    NuvoTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onOpenAIApiKeyChange = {},
        )
    }
}