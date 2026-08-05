package com.reals.app.ui.profile

import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileQuestionPresentationTest {
    @Test
    fun `overview counts answered and selected answers`() {
        val catalog = TestDtos.profileQuestionCatalog().toDomain()
        val answers = listOf(
            TestDtos.profileQuestionAnswer("PERFECT_SUNDAY_001", selectedPosition = 1).toDomain(),
            TestDtos.profileQuestionAnswer("LIFE_SOUNDTRACK_001").toDomain(),
            TestDtos.profileQuestionAnswer("REMOVED_001").toDomain(),
        )

        val overview = catalog.overview(answers)

        assertEquals(2, overview.answeredCount)
        assertEquals(4, overview.totalQuestionCount)
        assertEquals(1, overview.selectedCount)
    }

    @Test
    fun `selection rows exclude stale answers`() {
        val catalog = TestDtos.profileQuestionCatalog().toDomain()
        val rows = catalog.selectionRows(
            listOf(
                TestDtos.profileQuestionAnswer("PERFECT_SUNDAY_001", current = true).toDomain(),
                TestDtos.profileQuestionAnswer("LIFE_SOUNDTRACK_001", current = false).toDomain(),
            ),
        )

        assertEquals(listOf("PERFECT_SUNDAY_001"), rows.map { it.question.id })
    }

    @Test
    fun `selection draft validates maximum three unique selectable ids`() {
        val catalog = TestDtos.profileQuestionCatalog().toDomain()
        val rows = catalog.selectionRows(
            listOf(
                TestDtos.profileQuestionAnswer("PERFECT_SUNDAY_001").toDomain(),
                TestDtos.profileQuestionAnswer("LIFE_SOUNDTRACK_001").toDomain(),
                TestDtos.profileQuestionAnswer("SMALL_JOY_001").toDomain(),
                TestDtos.profileQuestionAnswer("BEST_PLAN_001").toDomain(),
            ),
        )

        assertTrue(profileQuestionSelectionDraftIsValid(rows.take(3).map { it.question.id }, rows))
        assertFalse(profileQuestionSelectionDraftIsValid(rows.map { it.question.id }, rows))
        assertFalse(
            profileQuestionSelectionDraftIsValid(
                listOf(
                    "PERFECT_SUNDAY_001",
                    "PERFECT_SUNDAY_001"
                ), rows
            )
        )
        assertFalse(profileQuestionSelectionDraftIsValid(listOf("STALE_001"), rows))
    }

    @Test
    fun `answer validation trims and enforces nonblank maximum length`() {
        assertEquals("Café", validateProfileQuestionAnswer("  Café  ").normalizedAnswer)
        assertTrue(validateProfileQuestionAnswer("  Café  ").valid)
        assertFalse(validateProfileQuestionAnswer("   ").valid)
        assertFalse(validateProfileQuestionAnswer("a".repeat(ProfileQuestionAnswerMaxLength + 1)).valid)
    }

    @Test
    fun `stale answer can be saved without changing its text`() {
        val staleAnswer = TestDtos.profileQuestionAnswer(
            questionId = "PERFECT_SUNDAY_001",
            answer = "Café y caminata",
            current = false,
        ).toDomain()

        assertTrue(
            canSaveProfileQuestionAnswer(
                input = "Café y caminata",
                savedAnswer = staleAnswer,
                mutationActive = false,
            )
        )
    }

    @Test
    fun `current answer with unchanged text cannot be saved again`() {
        val currentAnswer = TestDtos.profileQuestionAnswer(
            questionId = "PERFECT_SUNDAY_001",
            answer = "Café y caminata",
            current = true,
        ).toDomain()

        assertFalse(
            canSaveProfileQuestionAnswer(
                input = "Café y caminata",
                savedAnswer = currentAnswer,
                mutationActive = false,
            )
        )
    }

    @Test
    fun `answer validation rejects multiline text`() {
        val validation = validateProfileQuestionAnswer("Primera línea\nSegunda línea")

        assertFalse(validation.valid)
        assertEquals(
            "La respuesta debe escribirse en una sola línea.",
            validation.error,
        )
    }
}
