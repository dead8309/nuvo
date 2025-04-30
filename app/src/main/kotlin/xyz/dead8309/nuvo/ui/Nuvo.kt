package xyz.dead8309.nuvo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import xyz.dead8309.nuvo.R
import xyz.dead8309.nuvo.navigation.NuvoNavHost
import xyz.dead8309.nuvo.ui.components.AIModelSelectorSheet
import xyz.dead8309.nuvo.ui.components.NuvoTopAppBar
import xyz.dead8309.nuvo.ui.components.drawer.ChatDrawer
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Nuvo(
    appState: NuvoAppState,
    modifier: Modifier = Modifier,
    // TODO: Inject or get current model name from a ViewModel later
    currentAiModelName: String = "GPT-4o",
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAiModelSheet by remember { mutableStateOf(false) }

    // Close bottom sheet when drawer opens
    LaunchedEffect(appState.drawerState.isOpen) {
        if (appState.drawerState.isOpen) {
            showAiModelSheet = false
        }
    }

    BackHandler(appState.drawerState.isOpen) {
        if (appState.drawerState.isOpen) {
            appState.closeDrawer()
        }
    }

    ModalNavigationDrawer(
        drawerContent = {
            ChatDrawer(
                onChatSessionClick = appState::navigateToChatSession,
            )
        },
        drawerState = appState.drawerState,
        modifier = modifier.imePadding()
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(appState.snackbarHostState) },
            topBar = {
                // TODO: a better way to handle this
                if (appState.isAppBarVisible) {
                    NuvoTopAppBar(
                        isNewChatEnabled = appState.canNavigateToNewChat,
                        currentModel = currentAiModelName,
                        onModelChangeClick = { showAiModelSheet = true },
                        onMenuIconClick = { appState.openDrawer() },
                        onNewChatClick = appState::navigateToHome,
                        onSettingsClick = appState::navigateToSettings
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.settings_title)) },
                        navigationIcon = {
                            IconButton(onClick = appState::popBackStack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    )
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NuvoNavHost(
                    appState = appState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showAiModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiModelSheet = false },
            sheetState = bottomSheetState,
        ) {
            AIModelSelectorSheet(
                onDismiss = { showAiModelSheet = false }
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun NuvoPreview() {
    val previewAppState = rememberNuvoAppState()
    NuvoTheme {
        Nuvo(previewAppState)
    }
}