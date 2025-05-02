package xyz.dead8309.nuvo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.datetime.Clock
import xyz.dead8309.nuvo.core.model.ChatMessage
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor.Companion.extractOriginalToolName
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun AiMessage(
    modifier: Modifier = Modifier,
    message: ChatMessage,
) {
    val state = rememberMarkdownState(message.content.orEmpty())

    SelectionContainer(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            // TODO: We need to find a way to fix this issue or use some other library
            //
            // https://github.com/JetBrains/markdown/issues/172
            // This is an issue with the markdown library, it doesn't support incremental parsing of tokens
            // and instead create a new ast every time the content changes which causes the the composable to
            // recompose during the whole stream.
            //
            // We would just display the content as a Text composable while streaming and then switch to
            // Markdown later.
            //
            // Edit: I tried https://github.com/jeziellago/compose-markdown it works fine with streaming text
            // but we have to define styles by ourselves. I'll come back to this later
            if (message.isStreaming) {
                Text(
                    text = message.content.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Markdown(
                    markdownState = state, modifier = Modifier, components = markdownComponents(
                        codeBlock = highlightedCodeBlock,
                        codeFence = highlightedCodeFence,
                    ), colors = markdownColor(), typography = markdownTypography()
                )
            }
        }
    }
}

@Composable
fun UserMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    val markdownState = rememberMarkdownState(message.content.orEmpty())

    Surface(
        modifier = modifier, shape = RoundedCornerShape(
            topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp
        ), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(
            width = 1.dp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
        ), tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Markdown(
                markdownState = markdownState,
                modifier = Modifier,
                components = markdownComponents(
                    codeBlock = highlightedCodeBlock,
                    codeFence = highlightedCodeFence,
                ),
                colors = markdownColor(),
            )
        }
    }
}

@Composable
fun ChatMessageItem(
    currentMessage: ChatMessage,
    allMessages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {

    val alignment = if (!currentMessage.isFromAi()) Alignment.CenterEnd else Alignment.CenterStart
    val horizontalPadding = if (!currentMessage.isFromAi()) {
        PaddingValues(start = 64.dp, end = 0.dp)
    } else {
        PaddingValues(start = 0.dp, end = 0.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontalPadding)
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.align(alignment)) {
            when {
                currentMessage.role == ChatMessage.Role.ASSISTANT && currentMessage.content == null && !currentMessage.toolCalls.isNullOrEmpty()
                    -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentMessage.toolCalls.forEach { toolCall ->
                            val toolMessage = allMessages
                                .filter { it.role == ChatMessage.Role.TOOL }
                                .lastOrNull { it.toolCallId == toolCall.id }

                            val toolCallState = remember(toolCall.id) {
                                mutableStateOf(
                                    ToolCallState(
                                        toolName = extractOriginalToolName(toolCall.function.name),
                                        argumentsJson = toolCall.function.argumentsJson,
                                        resultJson = "",
                                        durationMs = 0L,
                                        isExecuting = true,
                                        isSuccess = false
                                    )
                                )
                            }
                            if (toolMessage != null) {
                                val durationMs =
                                    (toolMessage.timestamp.toEpochMilliseconds() - currentMessage.timestamp.toEpochMilliseconds()).coerceAtLeast(
                                        0
                                    )
                                toolCallState.value = toolCallState.value.copy(
                                    toolName = extractOriginalToolName(
                                        toolMessage.name ?: toolCall.function.name
                                    ),
                                    argumentsJson = toolCall.function.argumentsJson,
                                    resultJson = toolMessage.content ?: "{}",
                                    durationMs = durationMs,
                                    isExecuting = false,
                                    isSuccess = toolMessage.toolResult?.isSuccess ?: false
                                )
                            }
                            ToolCallCard(
                                state = toolCallState.value,
                                onCardClick = {},
                                modifier = modifier
                            )
                        }
                    }
                }

                currentMessage.role == ChatMessage.Role.ASSISTANT -> {
                    AiMessage(modifier, currentMessage)
                }

                currentMessage.role == ChatMessage.Role.USER -> {
                    UserMessage(currentMessage, modifier)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChatMessageItemUserPreview() {
    NuvoTheme {
        ChatMessageItem(
            currentMessage = ChatMessage(
                role = ChatMessage.Role.USER,
                name = "@dead8309",
                content = "Hello",
                timestamp = Clock.System.now(),
                sessionId = ""
            ), allMessages = emptyList()
        )
    }
}

@Preview
@Composable
private fun ChatMessageItemAiPreview() {
    NuvoTheme {
        ChatMessageItem(
            currentMessage = ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                name = "ai",
                content = "Hello User! How can I help you today?",
                timestamp = Clock.System.now(),
                sessionId = ""
            ), allMessages = emptyList()
        )
    }
}

@Composable
private fun ToolCallCard(
    state: ToolCallState,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isError = !state.isExecuting && !state.isSuccess
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(CardDefaults.shape)
            .clickable {
                expanded = !expanded
                onCardClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.toolName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "${state.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        if (isError) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )

                        }
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Arguments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = state.argumentsJson,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = state.resultJson,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Stable
data class ToolCallState(
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String,
    val durationMs: Long,
    val isExecuting: Boolean,
    val isSuccess: Boolean = false
)