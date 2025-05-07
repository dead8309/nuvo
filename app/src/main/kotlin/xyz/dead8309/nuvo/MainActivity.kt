package xyz.dead8309.nuvo

import android.app.ComponentCaller
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.dead8309.nuvo.navigation.ChatRoute
import xyz.dead8309.nuvo.ui.Nuvo
import xyz.dead8309.nuvo.ui.rememberNuvoAppState
import xyz.dead8309.nuvo.ui.screens.chat.PaymentEvent
import xyz.dead8309.nuvo.ui.screens.chat.PaymentEventBus
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navControllerForDeepLink: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = rememberNuvoAppState()
            navControllerForDeepLink = appState.navController

            NuvoTheme {
                Nuvo(appState)
            }

            LaunchedEffect(key1 = intent) {
                intent?.let { currentIntent ->
                    if (currentIntent.action == Intent.ACTION_VIEW &&
                        currentIntent.data?.scheme == "nuvo" &&
                        currentIntent.data?.host == "payment-callback"
                    ) {
                        Log.d(
                            TAG,
                            "onCreate - LaunchedEffect processing deep link: ${currentIntent.data}"
                        )
                        handlePaymentDeepLink(currentIntent.data!!)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        Log.d(TAG, "onNewIntent received: ${intent.action}, Data: ${intent.data}")
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW &&
            intent.data?.scheme == "nuvo" &&
            intent.data?.host == "payment-callback"
        ) {
            Log.d(TAG, "onNewIntent processing deep link: ${intent.data}")
            handlePaymentDeepLink(intent.data!!)
        }
    }

    private fun handlePaymentDeepLink(uri: Uri) {
        Log.i(TAG, "Handling payment deep link (SharedPreferences strategy): $uri")
        val status = uri.getQueryParameter("status") ?: "unknown"
        val stripeSessionId = uri.getQueryParameter("stripe_session_id")
        val originalToolCallIdFromUri = uri.getQueryParameter("original_tool_call_id")

        Log.i(
            TAG,
            "Parsed from deep link: status=$status, originalToolCallIdFromUri=$originalToolCallIdFromUri, stripeSessionId=$stripeSessionId"
        )

        lifecycleScope.launch {
            val pendingPaymentInfo =
                PaymentContextPrefs.getAndClearPendingPayment(applicationContext)

            val relaunchIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(relaunchIntent)


            if (pendingPaymentInfo != null) {
                val (sessionIdFromPrefs, toolCallIdFromPrefs) = pendingPaymentInfo
                Log.d(
                    TAG,
                    "Retrieved from Prefs: sessionId=$sessionIdFromPrefs, toolCallId=$toolCallIdFromPrefs"
                )

                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    navControllerForDeepLink?.let { navController ->
                        val targetRoute =
                            ChatRoute(chatSessionId = sessionIdFromPrefs, prompt = null)
                        val currentNavRoute =
                            navController.currentBackStackEntry?.destination?.route

                        var needsNavigation = true
                        if (currentNavRoute != null) {
                            try {
                                if (currentNavRoute.contains("chat/$sessionIdFromPrefs")) {
                                    needsNavigation = false
                                    Log.d(
                                        TAG,
                                        "Already on or navigating to target chat: $currentNavRoute"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not parse current route to ChatRoute: $e")
                            }
                        }


                        if (needsNavigation) {
                            Log.d(TAG, "Navigating to ChatRoute object: $targetRoute")
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            Log.d(
                                TAG,
                                "Navigation to ChatRoute for session $sessionIdFromPrefs initiated."
                            )
                        }

                        delay(100)
                        PaymentEventBus.post(
                            PaymentEvent(status, toolCallIdFromPrefs, stripeSessionId)
                        )
                        Log.d(
                            TAG,
                            "PaymentResultEvent posted with context from SharedPreferences: $toolCallIdFromPrefs"
                        )

                    } ?: Log.e(
                        TAG,
                        "navControllerForDeepLink is null during repeatOnLifecycle. Cannot navigate or post event properly."
                    )
                }
            } else {
                lifecycleScope.launch {
                    PaymentEventBus.post(
                        PaymentEvent(status, originalToolCallIdFromUri ?: "", stripeSessionId)
                    )
                }
            }
        }
    }

}