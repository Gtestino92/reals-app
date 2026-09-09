package com.reals.app.ui.chat

import com.reals.app.domain.model.PublicProfileQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualProfileQuestionsTest {
    @Test
    fun `public profile questions preserve backend position order for display`() {
        val visible = publicProfileQuestionsForDisplay(
            listOf(
                PublicProfileQuestion("q2", "Prompt 2", "Answer 2", 2),
                PublicProfileQuestion("q1", "Prompt 1", "Answer 1", 1),
            ),
        )

        assertEquals(listOf("q1", "q2"), visible.map { it.questionId })
    }

    @Test
    fun `public profile questions do not render empty section inputs`() {
        assertTrue(publicProfileQuestionsForDisplay(emptyList()).isEmpty())
        assertTrue(
            publicProfileQuestionsForDisplay(
                listOf(PublicProfileQuestion("q1", "", "Answer", 1)),
            ).isEmpty(),
        )
    }
}
