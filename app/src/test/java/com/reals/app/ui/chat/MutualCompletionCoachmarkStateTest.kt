package com.reals.app.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutualCompletionCoachmarkStateTest {
    @Test
    fun `initial baseline false does not show coachmark`() {
        val update = MutualCompletionCoachmarkState().next(eligible = false)

        assertFalse(update.showCoachmark)
        assertTrue(update.state.baselineEstablished)
        assertFalse(update.state.previouslyEligible)
    }

    @Test
    fun `false to true transition shows coachmark`() {
        val baseline = MutualCompletionCoachmarkState().next(eligible = false).state

        val update = baseline.next(eligible = true)

        assertTrue(update.showCoachmark)
        assertTrue(update.state.alreadyShown)
        assertTrue(update.state.previouslyEligible)
    }

    @Test
    fun `initial baseline true does not show coachmark`() {
        val update = MutualCompletionCoachmarkState().next(eligible = true)

        assertFalse(update.showCoachmark)
        assertTrue(update.state.baselineEstablished)
        assertTrue(update.state.previouslyEligible)
    }

    @Test
    fun `later false to true does not show again after first display`() {
        val shown = MutualCompletionCoachmarkState()
            .next(eligible = false).state
            .next(eligible = true).state
        val disappeared = shown.next(eligible = false).state

        val update = disappeared.next(eligible = true)

        assertFalse(update.showCoachmark)
        assertTrue(update.state.alreadyShown)
    }

    @Test
    fun `new state for another chat can show on later transition`() {
        val newChatBaseline = MutualCompletionCoachmarkState().next(eligible = false).state

        val update = newChatBaseline.next(eligible = true)

        assertTrue(update.showCoachmark)
    }
}
