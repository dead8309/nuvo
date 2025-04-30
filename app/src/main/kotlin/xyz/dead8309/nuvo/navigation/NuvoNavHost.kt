package xyz.dead8309.nuvo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import xyz.dead8309.nuvo.ui.NuvoAppState
import xyz.dead8309.nuvo.ui.screens.chat.ChatScreen
import xyz.dead8309.nuvo.ui.screens.home.HomeScreen
import xyz.dead8309.nuvo.ui.screens.settings.SettingsScreen

@Composable
fun NuvoNavHost(
    appState: NuvoAppState,
    modifier: Modifier = Modifier,
    startDestination: Any = HomeRoute,
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                navigateToChat = { prompt ->
                    appState.navigateToNewChat(prompt)
                }
            )
        }
        composable<ChatRoute> {
            ChatScreen()
        }

        composable<SettingsRoute> {
            SettingsScreen()
        }
    }
}