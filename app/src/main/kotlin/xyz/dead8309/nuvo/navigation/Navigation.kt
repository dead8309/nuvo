package xyz.dead8309.nuvo.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChatRoute(val chatSessionId: String?, val prompt: String?)

@Serializable
data object SettingsRoute

@Serializable
data object McpRoute

fun NavController.navigateToSettings(navOptionsBuilder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(SettingsRoute) { navOptionsBuilder() }
}

fun NavController.navigateToHome(navOptionsBuilder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(HomeRoute) { navOptionsBuilder() }
}

fun NavController.navigateToChat(
    chatSessionId: String,
    navOptionsBuilder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(
        ChatRoute(
            chatSessionId = chatSessionId,
            prompt = null
        )
    ) {
        navOptionsBuilder()
    }
}

fun NavController.navigateToNewChat(
    prompt: String,
    navOptionsBuilder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(
        ChatRoute(
            chatSessionId = null,
            prompt = prompt
        )
    ) {
        navOptionsBuilder()
    }
}

fun NavController.navigateToMcp(navOptionsBuilder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(McpRoute) { navOptionsBuilder() }
}

