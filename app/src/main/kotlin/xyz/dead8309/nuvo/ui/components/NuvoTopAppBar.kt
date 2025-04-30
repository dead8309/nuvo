package xyz.dead8309.nuvo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.ui.theme.NuvoTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuvoTopAppBar(
    modifier: Modifier = Modifier,
    isNewChatEnabled: Boolean,
    currentModel: String,
    onModelChangeClick: () -> Unit,
    onMenuIconClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            ModelSelectionRow(
                currentModel = currentModel,
                onModelChangeClick = onModelChangeClick
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuIconClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.open_drawer_content_desc),
                )
            }
        },
        actions = {
            AnimatedVisibility(
                visible = isNewChatEnabled,
                enter = fadeIn(
                    initialAlpha = 0.3f,
                    animationSpec = androidx.compose.animation.core.tween(300)
                ),
                exit = fadeOut(
                    targetAlpha = 0.3f,
                    animationSpec = androidx.compose.animation.core.tween(300)
                )
            ) {
                IconButton(
                    enabled = isNewChatEnabled,
                    onClick = onNewChatClick
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = stringResource(R.string.new_chat_content_desc),
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_content_desc),
                )
            }
        },
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
    )
}

@Composable
private fun ModelSelectionRow(
    modifier: Modifier = Modifier,
    currentModel: String,
    onModelChangeClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onModelChangeClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text = currentModel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.select_ai_model_button),
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Preview
@Composable
private fun NuvoTopAppBarPreview() {
    NuvoTheme {
        NuvoTopAppBar(
            isNewChatEnabled = true,
            currentModel = "GPT-4.1 Mini",
            onMenuIconClick = {},
            onModelChangeClick = {},
            onNewChatClick = {},
            onSettingsClick = {}
        )
    }
}
