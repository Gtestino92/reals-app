package com.reals.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalRealsDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = RealsColors.Ink,
    onPrimary = RealsColors.Ivory,
    primaryContainer = RealsColors.Paper,
    onPrimaryContainer = RealsColors.Ink,
    secondary = RealsColors.AntiqueGold,
    onSecondary = RealsColors.Ink,
    secondaryContainer = RealsColors.Sand,
    onSecondaryContainer = RealsColors.Ink,
    tertiary = RealsColors.SoftGold,
    background = RealsColors.Ivory,
    onBackground = RealsColors.Ink,
    surface = RealsColors.Paper,
    onSurface = RealsColors.Ink,
    surfaceVariant = RealsColors.Sand,
    onSurfaceVariant = RealsColors.InkSoft,
    surfaceContainerHigh = RealsColors.Sand,
    outline = RealsColors.SoftGold,
    outlineVariant = RealsColors.SoftGold.copy(alpha = 0.65f),
    error = Color(0xFF9C2F28),
    errorContainer = Color(0xFFFFE1D9),
    onErrorContainer = Color(0xFF5C1611),
)

private val DarkColors = darkColorScheme(
    primary = RealsColors.DarkText,
    onPrimary = RealsColors.DarkInk,
    primaryContainer = RealsColors.DarkSurface,
    onPrimaryContainer = RealsColors.DarkText,
    secondary = Color(0xFFC6A15B),
    onSecondary = RealsColors.DarkInk,
    secondaryContainer = RealsColors.DarkSurfaceHigh,
    onSecondaryContainer = RealsColors.DarkText,
    tertiary = Color(0xFF9E824C),
    background = RealsColors.DarkInk,
    onBackground = RealsColors.DarkText,
    surface = RealsColors.DarkSurface,
    onSurface = RealsColors.DarkText,
    surfaceVariant = RealsColors.DarkSurfaceHigh,
    onSurfaceVariant = RealsColors.DarkTextMuted,
    surfaceContainerHigh = RealsColors.DarkSurfaceHigh,
    outline = Color(0xFF7E6C49),
    outlineVariant = Color(0xFF493E2B),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF6E211A),
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
fun RealsAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRealsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = RealsTypography,
            shapes = RealsShapes,
            content = content,
        )
    }
}
