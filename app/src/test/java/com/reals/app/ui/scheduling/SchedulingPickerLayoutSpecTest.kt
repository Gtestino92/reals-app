package com.reals.app.ui.scheduling

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingPickerLayoutSpecTest {
    @Test
    fun `hour and minute remain side by side at normal width and font`() {
        val spec = schedulingPickerLayoutSpec(maxWidth = 327.dp, fontScale = 1f)

        assertEquals(SchedulingHourMinuteArrangement.SideBySide, spec.hourMinuteArrangement)
        assertEquals(132.dp, spec.dayViewportHeight)
        assertEquals(156.dp, spec.optionViewportHeight)
    }

    @Test
    fun `hour and minute stack below narrow width boundary`() {
        val spec = schedulingPickerLayoutSpec(maxWidth = 299.dp, fontScale = 1f)

        assertEquals(SchedulingHourMinuteArrangement.Stacked, spec.hourMinuteArrangement)
    }

    @Test
    fun `hour and minute stack for moderate width with large text`() {
        val spec = schedulingPickerLayoutSpec(maxWidth = 327.dp, fontScale = 1.3f)

        assertEquals(SchedulingHourMinuteArrangement.Stacked, spec.hourMinuteArrangement)
    }

    @Test
    fun `picker viewports grow for large text`() {
        val large = schedulingPickerLayoutSpec(maxWidth = 276.dp, fontScale = 1.5f)
        val veryLarge = schedulingPickerLayoutSpec(maxWidth = 276.dp, fontScale = 2f)

        assertTrue(large.optionViewportHeight > 156.dp)
        assertTrue(veryLarge.optionViewportHeight > large.optionViewportHeight)
        assertEquals(SchedulingHourMinuteArrangement.Stacked, veryLarge.hourMinuteArrangement)
    }
}
