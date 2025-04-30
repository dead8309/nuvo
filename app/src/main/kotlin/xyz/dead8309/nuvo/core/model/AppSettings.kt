package xyz.dead8309.nuvo.core.model

import androidx.compose.runtime.Stable

@Stable
data class AppSettings(
    val openaiApiKey: String? = null
)