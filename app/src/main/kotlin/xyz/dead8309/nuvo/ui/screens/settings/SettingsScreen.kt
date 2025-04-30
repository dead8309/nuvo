package xyz.dead8309.nuvo.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Log.w("SettingsScreen", "AppSettings: $state")

    SettingsScreen(
        state = state,
        onOpenAIApiKeyChange = viewModel::updateOpenAiApiKey,
        modifier = modifier,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onOpenAIApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
            .fillMaxSize()
    ) {

        Text(
            stringResource(R.string.settings_openai_api_key_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
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