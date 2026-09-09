package com.reals.app.ui.scheduling

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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
class SchedulingResponsiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun proposalSelectorUsesSideBySideLayoutAtNormalWidth() {
        setProposalSelector(width = 327.dp, fontScale = 1f)

        composeRule.onNodeWithText("Día").assertIsDisplayed()
        composeRule.onNodeWithText("Hora").assertIsDisplayed()
        composeRule.onNodeWithText("Min").assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulingHourMinuteSideBySideTag).assertIsDisplayed()
        assertDayAboveHourMinuteAndAddBelow()
        assertPickerControlsMeetMinimums()
    }

    @Test
    fun proposalSelectorStacksHourAndMinuteWhenConstrained() {
        setProposalSelector(width = 236.dp, fontScale = 2f)

        composeRule.onNodeWithTag(SchedulingHourMinuteStackedTag).assertIsDisplayed()
        assertDayAboveHourMinuteAndAddBelow()
        assertPickerControlsMeetMinimums()
    }

    @Test
    fun proposalSelectorStacksAtModerateWidthAndFontScale() {
        setProposalSelector(width = 276.dp, fontScale = 1.3f)

        composeRule.onNodeWithTag(SchedulingHourMinuteStackedTag).assertIsDisplayed()
        assertDayAboveHourMinuteAndAddBelow()
    }

    @Test
    fun proposalSelectorStacksAtNarrowLargeFont() {
        setProposalSelector(width = 236.dp, fontScale = 1.5f)

        composeRule.onNodeWithTag(SchedulingHourMinuteStackedTag).assertIsDisplayed()
        assertDayAboveHourMinuteAndAddBelow()
    }

    @Test
    fun proposalSelectorStacksAtTypicalVeryLargeFont() {
        setProposalSelector(width = 276.dp, fontScale = 2f)

        composeRule.onNodeWithTag(SchedulingHourMinuteStackedTag).assertIsDisplayed()
        assertDayAboveHourMinuteAndAddBelow()
    }

    @Test
    fun minutePickerShowsUnavailableWithoutClipping() {
        setMinutePicker(width = 236.dp, fontScale = 2f)

        val root = composeRule.onNodeWithTag(SchedulingMinuteTestRootTag).getUnclippedBoundsInRoot()
        val unavailable = composeRule.onNodeWithTag(SchedulingMinuteUnavailableTag)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val zero = composeRule.onNodeWithTag(schedulingMinuteOptionTag(0)).assertIsDisplayed()
            .assertHasClickAction()
            .assertIsNotEnabled()
            .getUnclippedBoundsInRoot()
        val thirty = composeRule.onNodeWithTag(schedulingMinuteOptionTag(30)).assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()

        assertWithin(root, unavailable)
        assertWithin(root, zero)
        assertWithin(root, thirty)
        assertTrue(zero.height >= 48.dp)
        assertTrue(thirty.height >= 48.dp)
        assertFalse(zero.overlaps(thirty))
    }

    @Test
    fun pickerPreviousAndNextCallbacksFireOnce() {
        val events = mutableListOf<String>()
        setWheelPicker(
            width = 276.dp,
            fontScale = 1.5f,
            selected = 2,
            onSelected = { events += "select-$it" },
        )

        composeRule.onNodeWithTag(schedulingPickerPreviousTag(SchedulingHourPickerTag)).performClick()
        composeRule.onNodeWithTag(schedulingPickerNextTag(SchedulingHourPickerTag)).performClick()

        assertEquals(listOf("select-1", "select-3"), events)
    }

    @Test
    fun disabledPickerControlsRemainAccessibleButDisabled() {
        setWheelPicker(width = 276.dp, fontScale = 1.5f, selected = 2, enabled = false)

        composeRule.onNodeWithTag(schedulingPickerPreviousTag(SchedulingHourPickerTag)).assertIsNotEnabled()
        composeRule.onNodeWithTag(schedulingPickerNextTag(SchedulingHourPickerTag)).assertIsNotEnabled()
    }

    private fun setProposalSelector(width: Dp, fontScale: Float) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .testTag(SchedulingSelectorRootTag),
                    ) {
                        ProposalSelectorCard(
                            submitting = false,
                            actionsDisabled = false,
                            submittingLabel = null,
                            proposalError = null,
                            nowMillis = 1_787_976_660_000L,
                            availability = null,
                            selected = emptyList(),
                            onSelectedChange = {},
                            onSubmitProposals = {},
                        )
                    }
                }
            }
        }
    }

    private fun setMinutePicker(width: Dp, fontScale: Float) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .testTag(SchedulingMinuteTestRootTag),
                    ) {
                        MinutePickerColumn(
                            minutes = listOf(0, 30),
                            selected = 30,
                            enabled = true,
                            enabledMinutes = listOf(30),
                            conflictingMinutes = setOf(0),
                            onSelected = {},
                            minOptionsHeight = 224.dp,
                            controlMinHeight = 48.dp,
                            modifier = Modifier.requiredWidth(width),
                        )
                    }
                }
            }
        }
    }

    private fun setWheelPicker(
        width: Dp,
        fontScale: Float,
        selected: Int,
        enabled: Boolean = true,
        onSelected: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(modifier = Modifier.requiredWidth(width)) {
                        WheelPickerColumn(
                            title = "Hora",
                            options = listOf(1, 2, 3),
                            selected = selected,
                            enabled = enabled,
                            optionLabel = { it.toString() },
                            onSelected = onSelected,
                            pickerHeight = 156.dp,
                            controlMinHeight = 48.dp,
                            tagPrefix = SchedulingHourPickerTag,
                            modifier = Modifier.requiredWidth(width),
                        )
                    }
                }
            }
        }
    }

    private fun assertPickerControlsMeetMinimums() {
        listOf(
            schedulingPickerPreviousTag(SchedulingDayPickerTag),
            schedulingPickerNextTag(SchedulingDayPickerTag),
            schedulingPickerPreviousTag(SchedulingHourPickerTag),
            schedulingPickerNextTag(SchedulingHourPickerTag),
        ).forEach { tag ->
            assertTrue(composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().height >= 48.dp)
        }
    }

    private fun assertDayAboveHourMinuteAndAddBelow() {
        val root = composeRule.onNodeWithTag(SchedulingSelectorRootTag).getUnclippedBoundsInRoot()
        val day = composeRule.onNodeWithTag(SchedulingDayPickerTag).getUnclippedBoundsInRoot()
        val hour = composeRule.onNodeWithTag(SchedulingHourPickerTag).getUnclippedBoundsInRoot()
        val minute = composeRule.onNodeWithTag(SchedulingMinutePickerTag).getUnclippedBoundsInRoot()
        val add = composeRule.onNodeWithTag(SchedulingAddOptionTag).getUnclippedBoundsInRoot()

        listOf(day, hour, minute, add).forEach { assertWithin(root, it) }
        assertTrue(day.bottom <= hour.top)
        assertTrue(day.bottom <= minute.top)
        assertTrue(add.top >= hour.bottom)
        assertTrue(add.top >= minute.bottom)
    }

    private fun assertWithin(outer: DpRect, inner: DpRect) {
        assertTrue(inner.left >= outer.left)
        assertTrue(inner.top >= outer.top)
        assertTrue(inner.right <= outer.right)
        assertTrue(inner.bottom <= outer.bottom)
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    private val DpRect.height: Dp get() = bottom - top
}

private const val SchedulingSelectorRootTag = "scheduling_selector_test_root"
private const val SchedulingMinuteTestRootTag = "scheduling_minute_test_root"
