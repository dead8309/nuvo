package xyz.dead8309.nuvo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import xyz.dead8309.nuvo.ui.Nuvo
import xyz.dead8309.nuvo.ui.rememberNuvoAppState
import xyz.dead8309.nuvo.ui.theme.NuvoTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = rememberNuvoAppState()
            NuvoTheme {
                Nuvo(appState)
            }
        }
    }
}