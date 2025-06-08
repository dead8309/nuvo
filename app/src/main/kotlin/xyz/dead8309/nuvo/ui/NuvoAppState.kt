package xyz.dead8309.nuvo.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.dead8309.nuvo.navigation.ChatRoute
import xyz.dead8309.nuvo.navigation.HomeRoute
import xyz.dead8309.nuvo.navigation.McpRoute
import xyz.dead8309.nuvo.navigation.SettingsRoute
import xyz.dead8309.nuvo.navigation.navigateToChat
import xyz.dead8309.nuvo.navigation.navigateToHome
import xyz.dead8309.nuvo.navigation.navigateToMcp
import xyz.dead8309.nuvo.navigation.navigateToNewChat
import xyz.dead8309.nuvo.navigation.navigateToSettings
import xyz.dead8309.nuvo.ui.screens.mcp.McpViewModel

@Composable
fun rememberNuvoAppState(
    navController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    mcpViewModel: McpViewModel = hiltViewModel()
): NuvoAppState {
    val currentNavBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination: NavDestination? = currentNavBackStackEntry?.destination

    val mcpServersCount by mcpViewModel.state
        .map { it.mcpServers.size }
        .collectAsStateWithLifecycle(0)

    return remember(
        navController,
        drawerState,
        snackbarHostState,
        coroutineScope,
        currentDestination,
        mcpServersCount
    ) {
        NuvoAppState(
            navController,
            drawerState,
            snackbarHostState,
            coroutineScope,
            currentDestination,
            mcpServersCount
        )
    }
}

@Stable
class NuvoAppState(
    val navController: NavHostController,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope,
    private val currentDestination: NavDestination?,
    val mcpCount: Int = 0,
) {

    val canNavigateToNewChat: Boolean by derivedStateOf {
        currentDestination?.hasRoute(route = HomeRoute::class) == false
    }

    val isAppBarVisible: Boolean by derivedStateOf {
        when {
            currentDestination?.hasRoute(route = HomeRoute::class) == true -> true
            currentDestination?.hasRoute(route = ChatRoute::class) == true -> true
            currentDestination?.hasRoute(route = McpRoute::class) == true -> false
            currentDestination?.hasRoute(route = SettingsRoute::class) == true -> false
            else -> false
        }
    }

    fun popBackStack() {
        with(navController) {
            if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                popBackStack()
            }
        }
    }

    fun navigateToChatSession(sessionId: String) {
        navController.navigateToChat(sessionId) {
            popUpTo(HomeRoute)
            launchSingleTop = true
        }
        closeDrawer()
    }

    fun navigateToNewChat(prompt: String) {
        navController.navigateToNewChat(prompt)
        closeDrawer()
    }

    fun navigateToHome() {
        navController.navigateToHome {
            popUpTo(HomeRoute) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun navigateToSettings() {
        navController.navigateToSettings {
            launchSingleTop = true
        }
        closeDrawer()
    }

    fun navigateToMcp() {
        navController.navigateToMcp()
        closeDrawer()
    }

    fun openDrawer() {
        coroutineScope.launch {
            drawerState.open()
        }
    }

    fun closeDrawer() {
        coroutineScope.launch {
            drawerState.close()
        }
    }
}

