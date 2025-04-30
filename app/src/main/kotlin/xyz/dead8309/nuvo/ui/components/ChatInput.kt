package xyz.dead8309.nuvo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    isMessageStreaming: Boolean = false,
    isToolCallExecuting: Boolean = false,
    onSendMessage: (String) -> Unit,
    onCancelStreaming: () -> Unit = {},
    enabled: Boolean = true,
    resetScroll: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }

    fun sendMessage() {
        val messageToSend = text.trim()
        if (messageToSend.isNotBlank()) {
            onSendMessage(messageToSend)
            text = ""
            resetScroll()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            width = 3.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO: Add attachment button
            // TODO: Add voice input button

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .weight(1f),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                maxLines = 5,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(
                        // Align at the bottom when the text field is large maybe 2 lines
                        // TODO: Maybe there is a better to do this?
                        if (text.lines().size > 1) Alignment.Bottom
                        else Alignment.CenterVertically
                    ),
                contentAlignment = Alignment.Center
            ) {

                IconButton(
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        if (isMessageStreaming || isToolCallExecuting) {
                            onCancelStreaming()
                        } else {
                            sendMessage()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                ) {
                    if (isMessageStreaming || isToolCallExecuting) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.chat_stop_button_content_desc),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send_button_content_desc),
                        )
                    }
                }
                if (isMessageStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}


@Preview
@Composable
private fun ChatInputPreview() {
    NuvoTheme {
        ChatInput(onSendMessage = {})
    }
}