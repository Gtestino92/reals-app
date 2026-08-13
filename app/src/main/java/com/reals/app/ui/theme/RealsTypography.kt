package com.reals.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val RealsTypography = Typography()

object RealsType {
    val DisplayFamily = FontFamily.Serif

    val ScreenTitle = RealsTypography.headlineLarge.copy(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
    )

    val Identity = RealsTypography.displayMedium.copy(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
    )

    val SectionTitle = RealsTypography.titleLarge.copy(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
    )
}
