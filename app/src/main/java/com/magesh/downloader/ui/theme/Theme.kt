package com.magesh.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandCrimson = Color(0xFFE11D48)
val BrandPinkGlow = Color(0xFFF43F5E)
val BrandAmber = Color(0xFFFACC15)
val BrandViolet = Color(0xFF8B5CF6)
val BrandEmerald = Color(0xFF10B981)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPinkGlow,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4C0519),
    onPrimaryContainer = Color(0xFFFFE4E6),
    secondary = BrandViolet,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = BrandAmber,
    onTertiary = Color(0xFF1E1B0A),
    background = Color(0xFF090710),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF13101E),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1D182C),
    onSurfaceVariant = Color(0xFFA8A2C0),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    outline = Color(0xFF322B4A)
)

@Composable
fun MageshDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
