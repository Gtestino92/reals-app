package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAccountSectionAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalHeaderKeepsTrailingToggleReadable() {
        setHeader(width = 327.dp, fontScale = 1f, expanded = false)

        composeRule.onNodeWithTag(AccountSectionHeaderNormalTag).assertIsDisplayed()
        composeRule.onNodeWithText("Cuenta").assertIsDisplayed()
        composeRule.onNodeWithText(AccountSectionSubtitle).assertIsDisplayed()
        composeRule.onNodeWithText("Abrir").assertIsDisplayed()
        assertToggleAccessibleAndSeparate()
    }

    @Test
    fun constrainedHeaderPlacesToggleOnOwnLine() {
        setHeader(width = 236.dp, fontScale = 2f, expanded = true)

        composeRule.onNodeWithTag(AccountSectionHeaderConstrainedTag).assertIsDisplayed()
        composeRule.onNodeWithText("Ocultar").assertIsDisplayed()
        assertToggleAccessibleAndSeparate()
    }

    @Test
    fun moderateWidthAndFontScaleUseConstrainedHeader() {
        setHeader(width = 276.dp, fontScale = 1.3f, expanded = false)

        composeRule.onNodeWithTag(AccountSectionHeaderConstrainedTag).assertIsDisplayed()
        composeRule.onNodeWithText("Abrir").assertIsDisplayed()
        assertToggleAccessibleAndSeparate()
    }

    @Test
    fun narrowLargeFontUsesConstrainedHeader() {
        setHeader(width = 236.dp, fontScale = 1.5f, expanded = false)

        composeRule.onNodeWithTag(AccountSectionHeaderConstrainedTag).assertIsDisplayed()
        assertToggleAccessibleAndSeparate()
    }

    @Test
    fun toggleClickFiresOnce() {
        var toggleCount = 0
        setHeader(width = 276.dp, fontScale = 1.5f, expanded = false, onToggle = { toggleCount++ })

        composeRule.onNodeWithTag(AccountSectionToggleTag).performClick()

        assertEquals(1, toggleCount)
    }

    @Test
    fun busyHeaderDisablesToggle() {
        setHeader(width = 276.dp, fontScale = 1.5f, enabled = false)

        composeRule.onNodeWithTag(AccountSectionToggleTag).assertIsNotEnabled()
    }

    @Test
    fun expandedAccountActionsRemainReachable() {
        setAccountSection(width = 276.dp, fontScale = 1.5f, expanded = true)

        composeRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
        composeRule.onNodeWithText("Notificaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Cambiar contraseña de Reals").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar cuenta").assertIsDisplayed()
    }

    @Test
    fun collapsedAccountDoesNotShowSupport() {
        setAccountSection(width = 327.dp, fontScale = 1f, expanded = false, showSupportReals = true)

        composeRule.onAllNodesWithText(SupportRealsTitle).assertCountEquals(0)
        composeRule.onAllNodesWithText(SupportRealsCta).assertCountEquals(0)
    }

    @Test
    fun expandedAccountShowsSupportWhenEnabled() {
        setAccountSection(width = 276.dp, fontScale = 1.8f, expanded = true, showSupportReals = true)

        composeRule.onNodeWithText(SupportRealsTitle).assertIsDisplayed()
        composeRule.onNodeWithText(SupportRealsBody).assertIsDisplayed()
        composeRule.onNodeWithText(SupportRealsCta).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun expandedAccountHidesSupportWhenDisabled() {
        setAccountSection(width = 327.dp, fontScale = 1f, expanded = true, showSupportReals = false)

        composeRule.onAllNodesWithText(SupportRealsTitle).assertCountEquals(0)
        composeRule.onAllNodesWithText(SupportRealsCta).assertCountEquals(0)
    }

    @Test
    fun supportCtaClickFiresOnce() {
        var supportCount = 0
        setAccountSection(
            width = 327.dp,
            fontScale = 1f,
            expanded = true,
            showSupportReals = true,
            onSupportReals = { supportCount++ },
        )

        composeRule.onNodeWithText(SupportRealsCta).performClick()

        assertEquals(1, supportCount)
    }

    @Test
    fun supportDoesNotChangePasswordCapability() {
        setAccountSection(
            width = 327.dp,
            fontScale = 1f,
            expanded = true,
            canChangePassword = false,
            showSupportReals = true,
        )

        composeRule.onAllNodesWithText("Cambiar contraseña de Reals").assertCountEquals(0)
        composeRule.onNodeWithText(SupportRealsCta).assertIsDisplayed()
    }

    private fun setHeader(
        width: Dp,
        fontScale: Float,
        expanded: Boolean = false,
        enabled: Boolean = true,
        onToggle: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .testTag(AccountSectionRootTag),
                    ) {
                        AccountSectionHeader(
                            expanded = expanded,
                            enabled = enabled,
                            onToggle = onToggle,
                        )
                    }
                }
            }
        }
    }

    private fun setAccountSection(
        width: Dp,
        fontScale: Float,
        expanded: Boolean,
        canChangePassword: Boolean = true,
        showSupportReals: Boolean = false,
        onSupportReals: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .testTag(AccountSectionRootTag),
                    ) {
                        AccountSection(
                            busy = false,
                            accountDeleteLoading = false,
                            accountDeleteError = null,
                            changePasswordLoading = false,
                            changePasswordError = null,
                            changePasswordMessage = null,
                            canChangePassword = canChangePassword,
                            showSupportReals = showSupportReals,
                            expanded = expanded,
                            onExpandedChange = {},
                            onSignOut = {},
                            onOpenNotifications = {},
                            onChangePassword = { _, _ -> },
                            onDeleteAccount = {},
                            onSupportReals = onSupportReals,
                        )
                    }
                }
            }
        }
    }

    private fun assertToggleAccessibleAndSeparate() {
        val root = composeRule.onNodeWithTag(AccountSectionRootTag).getUnclippedBoundsInRoot()
        val text = composeRule.onNodeWithTag(AccountSectionHeaderTextTag).getUnclippedBoundsInRoot()
        val toggle = composeRule.onNodeWithTag(AccountSectionToggleTag)
            .assertHasClickAction()
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()

        assertWithin(root, toggle)
        assertTrue(toggle.height >= AccountSectionToggleMinHeight)
        assertTrue(toggle.width >= 48.dp)
        assertFalse(text.overlaps(toggle))
    }

    private fun assertWithin(outer: DpRect, inner: DpRect) {
        assertTrue(inner.left >= outer.left)
        assertTrue(inner.top >= outer.top)
        assertTrue(inner.right <= outer.right)
        assertTrue(inner.bottom <= outer.bottom)
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    private val DpRect.width: Dp get() = right - left
    private val DpRect.height: Dp get() = bottom - top
}

private const val AccountSectionRootTag = "account_section_test_root"
