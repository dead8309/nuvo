package xyz.dead8309.nuvo.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.ui.components.ChatInput
import xyz.dead8309.nuvo.ui.components.ChatMessageItem
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
        onCancelStreaming = viewModel::cancelStreaming
    )
}

@Composable
private fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onCancelStreaming: () -> Unit
) {
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        val messages = when (uiState) {
            is ChatUiState.Idle -> uiState.messages
            is ChatUiState.WaitingForResponse -> uiState.messages
            is ChatUiState.StreamingResponse -> uiState.messages + listOfNotNull(uiState.streamingMessage) // Include streaming for scroll calculation
            is ChatUiState.ExecutingToolCall -> uiState.messages
            else -> emptyList()
        }
        val messageCount = messages.size
        if (messageCount > 0) {
            coroutineScope.launch {
                scrollState.animateScrollToItem(
                    index = messageCount - 1,
                    scrollOffset = Int.MAX_VALUE / 2
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (uiState) {
                is ChatUiState.LoadingHistory -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ChatUiState.Idle,
                is ChatUiState.Error,
                is ChatUiState.WaitingForResponse,
                is ChatUiState.StreamingResponse,
                is ChatUiState.ExecutingToolCall -> {
                    val messagesToShow = when (uiState) {
                        is ChatUiState.Idle -> uiState.messages
                        is ChatUiState.WaitingForResponse -> uiState.messages
                        is ChatUiState.StreamingResponse -> {
                            val streamingMessage = uiState.streamingMessage
                            if (streamingMessage != null) {
                                // Only add streamingMessage if it's not already in messages
                                val existingMessage =
                                    uiState.messages.find { it.id == streamingMessage.id }

                                if (existingMessage != null) {
                                    uiState.messages
                                } else {
                                    uiState.messages + streamingMessage
                                }
                            } else {
                                uiState.messages
                            }
                        }
                        is ChatUiState.Error -> uiState.messagesOnError
                        is ChatUiState.ExecutingToolCall -> uiState.messages
                        else -> emptyList()
                    }
                    ChatMessagesList(
                        modifier = Modifier.fillMaxSize(),
                        messages = messagesToShow,
                        scrollState = scrollState
                    )
                    if (uiState is ChatUiState.Error) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        ChatInput(
            onSendMessage = onSendMessage,
            isMessageStreaming = uiState is ChatUiState.StreamingResponse || uiState is ChatUiState.WaitingForResponse,
            isToolCallExecuting = uiState is ChatUiState.ExecutingToolCall,
            onCancelStreaming = onCancelStreaming,
            resetScroll = {
                coroutineScope.launch {
                    val messages = when (uiState) {
                        is ChatUiState.Idle -> uiState.messages
                        is ChatUiState.WaitingForResponse -> uiState.messages
                        is ChatUiState.StreamingResponse -> uiState.messages + listOfNotNull(uiState.streamingMessage)
                        is ChatUiState.ExecutingToolCall -> uiState.messages
                        else -> emptyList()
                    }
                    val messageCount = messages.size
                    if (messageCount > 0) {
                        scrollState.animateScrollToItem(
                            index = messageCount - 1,
                            scrollOffset = Int.MAX_VALUE / 2
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun ChatMessagesList(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    scrollState: LazyListState,
) {
    messages.groupBy { it.id }.forEach { (id, duplicates) ->
        if (duplicates.size > 1) {
            Log.e(
                "ChatMessagesList",
                "Duplicate message ID detected: $id, count: ${duplicates.size}"
            )
        }
    }
    // we don't pass tool messages to the list, as they are handled separately
    // by looking at the tool call ID in the message
    val filteredMessages = messages.filter { it.role != ChatMessage.Role.TOOL }
    LazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filteredMessages, key = {
            "${it.id}_${it.isStreaming}"
        }) { message ->
            ChatMessageItem(
                currentMessage = message,
                // pass all the messages so we can lookup the tool call message
                allMessages = messages,
            )
        }
    }
}


private val previewMessages = listOf(
    ChatMessage(
        role = ChatMessage.Role.USER,
        content = "Hello",
        sessionId = "session1",
        timestamp = Clock.System.now()
    ),
    ChatMessage(
        role = ChatMessage.Role.ASSISTANT,
        content = "Hi there! How can I help?",
        sessionId = "session1",
        timestamp = Clock.System.now()
    ),
    ChatMessage(
        role = ChatMessage.Role.USER,
        content = "What is Jetpack Compose?",
        sessionId = "session1",
        timestamp = Clock.System.now()
    ),
    ChatMessage(
        role = ChatMessage.Role.ASSISTANT,
        content = "Jetpack Compose is Android's modern toolkit for building native UI. It simplifies and accelerates UI development on Android.",
        sessionId = "session1",
        timestamp = Clock.System.now()
    ),
)

@Preview
@Composable
private fun ChatScreenPreviewSuccess() {
    NuvoTheme {
        Surface {
            ChatScreen(
                uiState = ChatUiState.Idle(previewMessages),
                onSendMessage = {},
                onCancelStreaming = {}
            )
        }
    }
}

@Preview
@Composable
private fun ChatScreenPreviewLoading() {
    NuvoTheme {
        Surface {
            ChatScreen(
                uiState = ChatUiState.LoadingHistory,
                onSendMessage = {},
                onCancelStreaming = {}
            )
        }
    }
}

@Preview
@Composable
private fun ChatScreenPreviewError() {
    NuvoTheme {
        Surface {
            ChatScreen(
                uiState = ChatUiState.Error("Network connection failed"),
                onSendMessage = {},
                onCancelStreaming = {}
            )
        }
    }
}