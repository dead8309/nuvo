package xyz.dead8309.nuvo.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.ui.components.AddEditMcpServerDialog
import xyz.dead8309.nuvo.ui.components.McpServerItem
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Log.w("SettingsScreen", "AppSettings: $state")

    SettingsScreen(
        modifier = modifier,
        state = state,
        onOpenAIApiKeyChange = viewModel::updateOpenAiApiKey,
        onSaveMcpConfig = viewModel::addOrUpdateMcpServer,
        onDeleteMcpConfig = viewModel::deleteMcpServer,
        onToggleMcpConfig = viewModel::setMcpServerEnabled
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
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showAddEditMcpDialog by remember { mutableStateOf(true) }
    var mcpConfigToEdit by remember { mutableStateOf<McpServer?>(null) }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
    ) {
        item {
            Text(
                stringResource(R.string.settings_openai_api_key_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
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
                        onToggleMcpConfig(serverConfig.id, isEnabled)
                    },
                    onEditClick = {
                        mcpConfigToEdit = serverConfig
                        showAddEditMcpDialog = true
                    },
                    onDeleteClick = { onDeleteMcpConfig(serverConfig.id) }
                )
            }
        }
    }
    if (showAddEditMcpDialog) {
        AddEditMcpServerDialog(
            existingConfig = mcpConfigToEdit,
            onDismiss = { showAddEditMcpDialog = false },
            onSave = { config ->
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