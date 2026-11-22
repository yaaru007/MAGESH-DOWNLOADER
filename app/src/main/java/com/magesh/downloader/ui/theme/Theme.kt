package com.magesh.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF04291D),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    background = Color(0xFF0B1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF121A22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B2632),
    onSurfaceVariant = Color(0xFF9FB0C0),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    outline = Color(0xFF2D3B49)
)

@Composable
fun MageshDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
