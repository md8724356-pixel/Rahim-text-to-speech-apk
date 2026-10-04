package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SonicCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = SonicCyanSoft,
    secondary = SonicIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E3178),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = SonicCoral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF68182C),
    onTertiaryContainer = Color(0xFFFFD9E0),
    background = StudioNavyDark,
    onBackground = Color(0xFFEFF6FF),
    surface = StudioSurfaceDark,
    onSurface = Color(0xFFEFF6FF),
    surfaceVariant = StudioCardDark,
    onSurfaceVariant = Color(0xFFB8C7E0),
    outline = Color(0xFF3B4D71)
)

private val LightColorScheme = lightColorScheme(
    primary = SonicPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F6FF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = SonicSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = SonicTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E0),
    onTertiaryContainer = Color(0xFF3F0012),
    background = StudioBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = StudioSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = StudioCardLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
