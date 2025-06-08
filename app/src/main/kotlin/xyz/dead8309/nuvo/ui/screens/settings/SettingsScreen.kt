package xyz.dead8309.nuvo.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.dead8309.nuvo.ui.components.ApiKeyInputField
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.userMessage) {
        if (state.userMessage != null) {
            snackbarHostState.showSnackbar(state.userMessage!!)
            viewModel.userMessageShown()
        }
    }

    SettingsScreen(
        modifier = modifier,
        state = state,
        onOpenAIApiKeyChange = viewModel::updateOpenAiApiKey,
    )
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsUiState = SettingsUiState(),
    onOpenAIApiKeyChange: (String) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "API Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                ApiKeyInputField(
                    apiKey = state.openAiApiKey,
                    onApiKeyChange = onOpenAIApiKeyChange
                )
            }
        }
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

