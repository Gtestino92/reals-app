package com.reals.app.ui.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualDecisionActionsLayoutTest {
    @Test
    fun `visual decisions use row at normal width and font`() {
        assertEquals(
            VisualDecisionActionsLayout.Row,
            visualDecisionActionsLayout(maxWidth = 327.dp, fontScale = 1f),
        )
    }

    @Test
    fun `visual decisions stack below narrow width boundary`() {
        assertEquals(
            VisualDecisionActionsLayout.Stacked,
            visualDecisionActionsLayout(maxWidth = 299.dp, fontScale = 1f),
        )
    }

    @Test
    fun `visual decisions stack for moderate width with large text`() {
        assertEquals(
            VisualDecisionActionsLayout.Stacked,
            visualDecisionActionsLayout(maxWidth = 327.dp, fontScale = 1.3f),
        )
    }

    @Test
    fun `visual decisions stack for very large text`() {
        assertEquals(
            VisualDecisionActionsLayout.Stacked,
            visualDecisionActionsLayout(maxWidth = 360.dp, fontScale = 1.8f),
        )
    }
}
