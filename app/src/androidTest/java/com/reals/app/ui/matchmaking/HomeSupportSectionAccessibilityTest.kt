package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSupportSectionAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun supportSectionShowsCopyAtLargeFontScale() {
        setSupportSection(width = 276.dp, fontScale = 1.8f)

        composeRule.onNodeWithText(SupportRealsTitle).assertIsDisplayed()
        composeRule.onNodeWithText(SupportRealsBody).assertIsDisplayed()
        composeRule.onNodeWithText(SupportRealsCta).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun supportCtaInvokesExternalNavigationCallback() {
        var clickCount = 0
        setSupportSection(width = 327.dp, fontScale = 1f, onSupportReals = { clickCount++ })

        composeRule.onNodeWithText(SupportRealsCta).performClick()

        assertEquals(1, clickCount)
    }

    private fun setSupportSection(
        width: Dp,
        fontScale: Float,
        onSupportReals: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(modifier = Modifier.requiredWidth(width)) {
                        SupportRealsSection(
                            enabled = true,
                            onSupportReals = onSupportReals,
                        )
                    }
                }
            }
        }
    }
}
