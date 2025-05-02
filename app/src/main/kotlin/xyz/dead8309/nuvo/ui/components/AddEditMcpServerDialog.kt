package xyz.dead8309.nuvo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun AddEditMcpServerDialog(
    existingConfig: McpServer?,
    onDismiss: () -> Unit,
    onSave: (McpServer) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = existingConfig != null

    var name by rememberSaveable(existingConfig?.id) { mutableStateOf(existingConfig?.name ?: "") }
    var url by rememberSaveable(existingConfig?.id) { mutableStateOf(existingConfig?.url ?: "") }
    // TODO: comeback to this
//    var headers by rememberSaveable(existingConfig?.id) {
//        mutableStateOf(
//            existingConfig?.headers ?: emptyMap()
//        )
//    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
//    var headersError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        nameError = if (name.isBlank()) "Name cannot be empty" else null
        urlError = if (url.isBlank()) "URL cannot be empty" else null
        return nameError == null && urlError == null
    }


    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (isEditing) R.string.settings_edit_mcp_server_title else R.string.settings_add_mcp_server_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.settings_mcp_server_name_label)) },
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let {
                            Text(
                                it, color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = null },
                    label = { Text(stringResource(R.string.settings_mcp_server_url_label)) },
                    isError = urlError != null,
                    supportingText = {
                        urlError?.let {
                            Text(
                                it, color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) {
                        val config = McpServer(
                            name = name.trim(),
                            url = url.trim(),
                            headers = emptyMap(),
                            enabled = existingConfig?.enabled ?: true
                        )
                        onSave(config)
                    }
                }) {
                Text(stringResource(R.string.settings_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        })
}

@Preview
@Composable
private fun AddEditMcpServerDialogPreview() {
    NuvoTheme { AddEditMcpServerDialog(existingConfig = null, onDismiss = {}, onSave = {}) }
}