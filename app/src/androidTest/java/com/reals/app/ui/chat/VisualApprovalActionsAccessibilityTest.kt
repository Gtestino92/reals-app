package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
class VisualApprovalActionsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalWidthUsesDecisionRow() {
        setActions(width = 327.dp, fontScale = 1f)

        composeRule.onNodeWithTag(VisualDecisionActionsRowTag).assertIsDisplayed()
        composeRule.onNodeWithText("Aprobar").assertIsDisplayed()
        composeRule.onNodeWithText("Rechazar").assertIsDisplayed()
        assertActionsInsideRootAndSeparate()
    }

    @Test
    fun constrainedWidthStacksFullWidthActions() {
        setActions(width = 236.dp, fontScale = 2f)

        composeRule.onNodeWithTag(VisualDecisionActionsStackedTag).assertIsDisplayed()
        assertActionsInsideRootAndSeparate()
        assertStackedActionsUseFullWidth()
    }

    @Test
    fun moderateWidthAndFontScaleStackDecisionActions() {
        setActions(width = 276.dp, fontScale = 1.3f)

        composeRule.onNodeWithTag(VisualDecisionActionsStackedTag).assertIsDisplayed()
        assertActionsInsideRootAndSeparate()
    }

    @Test
    fun narrowLargeFontStacksDecisionActions() {
        setActions(width = 236.dp, fontScale = 1.5f)

        composeRule.onNodeWithTag(VisualDecisionActionsStackedTag).assertIsDisplayed()
        assertActionsInsideRootAndSeparate()
    }

    @Test
    fun longProcessingLabelRemainsReachableInStackedLayout() {
        setActions(width = 276.dp, fontScale = 2f, deciding = true, decidingLabel = "Procesando...")

        composeRule.onAllNodesWithText("Procesando...").assertCountEquals(2)
        assertActionsInsideRootAndSeparate()
        assertStackedActionsUseFullWidth()
    }

    @Test
    fun disabledActionsRemainVisible() {
        setActions(width = 276.dp, fontScale = 1.5f, enabled = false)

        composeRule.onNodeWithTag(VisualDecisionApproveTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(VisualDecisionRejectTag).assertIsNotEnabled()
        assertActionsInsideRootAndSeparate()
    }

    @Test
    fun decisionClicksInvokeOnlyTheirCallback() {
        val events = mutableListOf<String>()
        setActions(
            width = 327.dp,
            fontScale = 1f,
            onApprove = { events += "approve" },
            onReject = { events += "reject" },
        )

        composeRule.onNodeWithTag(VisualDecisionApproveTag).performClick()
        composeRule.onNodeWithTag(VisualDecisionRejectTag).performClick()

        assertEquals(listOf("approve", "reject"), events)
    }

    private fun setActions(
        width: Dp,
        fontScale: Float,
        deciding: Boolean = false,
        decidingLabel: String? = null,
        enabled: Boolean = true,
        onApprove: () -> Unit = {},
        onReject: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .testTag(VisualDecisionRootTag),
                    ) {
                        VisualDecisionActions(
                            deciding = deciding,
                            decidingLabel = decidingLabel,
                            enabled = enabled,
                            onApprove = onApprove,
                            onReject = onReject,
                        )
                    }
                }
            }
        }
    }

    private fun assertActionsInsideRootAndSeparate() {
        val root = composeRule.onNodeWithTag(VisualDecisionRootTag).getUnclippedBoundsInRoot()
        val approve = composeRule.onNodeWithTag(VisualDecisionApproveTag)
            .assertHasClickAction()
            .getUnclippedBoundsInRoot()
        val reject = composeRule.onNodeWithTag(VisualDecisionRejectTag)
            .assertHasClickAction()
            .getUnclippedBoundsInRoot()

        assertWithin(root, approve)
        assertWithin(root, reject)
        assertTrue(approve.height >= VisualDecisionActionMinHeight)
        assertTrue(reject.height >= VisualDecisionActionMinHeight)
        assertFalse(approve.overlaps(reject))
    }

    private fun assertStackedActionsUseFullWidth() {
        val root = composeRule.onNodeWithTag(VisualDecisionRootTag).getUnclippedBoundsInRoot()
        val approve = composeRule.onNodeWithTag(VisualDecisionApproveTag)
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()
        val reject = composeRule.onNodeWithTag(VisualDecisionRejectTag)
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()

        assertTrue(approve.width >= root.width - 2.dp)
        assertTrue(reject.width >= root.width - 2.dp)
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

private const val VisualDecisionRootTag = "visual_decision_test_root"
