package com.reals.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5B4C),
    onPrimary = Color.White,
    secondary = Color(0xFF7A4A18),
    background = Color(0xFFFFFBF4),
    surface = Color(0xFFFFFBF4),
    surfaceContainerHigh = Color(0xFFF2E5D4),
    secondaryContainer = Color(0xFFFFE1B8),
    onSecondaryContainer = Color(0xFF33200A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF71D8C1),
    onPrimary = Color(0xFF00382E),
    secondary = Color(0xFFE8B06E),
    background = Color(0xFF15120E),
    surface = Color(0xFF15120E),
    surfaceContainerHigh = Color(0xFF282119),
    secondaryContainer = Color(0xFF583614),
    onSecondaryContainer = Color(0xFFFFDDB4),
)

@Composable
fun RealsAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
