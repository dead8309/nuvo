package xyz.dead8309.nuvo.ui.components.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun ChatDrawer(
    onChatSessionClick: (chatSessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DrawerViewModel = hiltViewModel(),
){
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatDrawer(
        onChatSessionClick = onChatSessionClick,
        modifier = modifier,
        state = uiState
    )
}

@Composable
private fun ChatDrawer(
    onChatSessionClick: (chatSessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    state: DrawerUiState
) {

    ModalDrawerSheet(modifier = modifier) {
        Text(
            text = stringResource(R.string.recent_chats),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        when (state) {
            is DrawerUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DrawerUiState.Success -> {
                if (state.sessions.isEmpty()) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_recent_chats),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.sessions, key = { it.id }) { session ->
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = session.title ?: "Untitled",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = false,
                                onClick = { onChatSessionClick(session.id) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            is DrawerUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChatDrawerPreview() {
    NuvoTheme {
        ChatDrawer(onChatSessionClick = {})
    }
}

@Preview
@Composable
private fun ChatDrawerWithItemsPreview() {
    val previewSessions = listOf(
        "Chat about Kotlin",
        "Compose Preview Test",
    )
    NuvoTheme {
        ModalDrawerSheet {
            Text(
                text = "Recent Chats",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(previewSessions, key = { it }) { session ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = session,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = false,
                        onClick = { /*onChatSessionClick(session.id)*/ },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}