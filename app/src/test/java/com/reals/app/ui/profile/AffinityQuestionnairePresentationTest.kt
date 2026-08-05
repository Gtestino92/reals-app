package com.reals.app.ui.profile

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.AffinityAnswerType
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.root.AffinityAnswerMutationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffinityQuestionnairePresentationTest {
    @Test
    fun `groups questions under categories preserving backend order and omitting empty categories`() {
        val catalog = TestDtos.affinityQuestionCatalog(
            categories = listOf(
                TestDtos.affinityQuestionCategory("EMPTY", "Vacía", displayOrder = 1),
                TestDtos.affinityQuestionCategory("MUSIC", "Música", displayOrder = 2),
                TestDtos.affinityQuestionCategory("PLANS", "Planes", displayOrder = 3),
            ),
            questions = listOf(
                TestDtos.affinityQuestion("MUSIC_2", categoryId = "MUSIC"),
                TestDtos.affinityQuestion("PLANS_1", categoryId = "PLANS"),
                TestDtos.affinityQuestion("MUSIC_1", categoryId = "MUSIC"),
            ),
        ).toDomain()

        val groups = catalog.groupQuestionsForPresentation(emptyList())

        assertEquals(listOf("MUSIC", "PLANS"), groups.map { it.category.id })
        assertEquals(listOf("MUSIC_2", "MUSIC_1"), groups.first().questions.map { it.id })
    }

    @Test
    fun `valid answer selection and progress count only current visible answers`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val answers = listOf(
            TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain(),
            TestDtos.affinityAnswer("PLANS_WEEKEND_001", 2, "QUIET").toDomain(),
            TestDtos.affinityAnswer("REMOVED_001", 1, "YES").toDomain(),
        )

        assertEquals("VERY_HIGH", catalog.questions.first().currentValidAnswer(answers)?.answerCode)
        assertEquals(1, catalog.progress(answers).answeredCount)
        assertEquals(2, catalog.progress(answers).totalQuestionCount)
    }

    @Test
    fun `semantic mismatch and unknown option are treated as unanswered`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val question = catalog.questions.first()

        assertEquals(null, question.currentValidAnswer(listOf(TestDtos.affinityAnswer(question.id, 2, "VERY_HIGH").toDomain())))
        assertEquals(null, question.currentValidAnswer(listOf(TestDtos.affinityAnswer(question.id, 1, "MISSING").toDomain())))
    }

    @Test
    fun `pending PATCH appears selected and pending DELETE appears unanswered`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val question = catalog.questions.first()
        val answers = listOf(TestDtos.affinityAnswer(question.id, 1, "LOW").toDomain())

        assertEquals(
            "VERY_HIGH",
            question.presentedAnswerCode(
                answers,
                AffinityAnswerMutationUiState(question.id, "VERY_HIGH"),
            ),
        )
        assertEquals(
            null,
            question.presentedAnswerCode(
                answers,
                AffinityAnswerMutationUiState(question.id, null),
            ),
        )
    }

    @Test
    fun `same answer selection is detectable as no-op and unknown type is unsupported`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val question = catalog.questions.first()
        val answers = listOf(TestDtos.affinityAnswer(question.id, 1, "LOW").toDomain())
        val unknown = question.copy(answerType = AffinityAnswerType.Unknown)

        assertEquals("LOW", question.currentValidAnswer(answers)?.answerCode)
        assertTrue(question.canSelectAnswerCode("LOW"))
        assertFalse(question.canSelectAnswerCode("MISSING"))
        assertFalse(unknown.answerType.isSupported())
        assertEquals(null, unknown.currentValidAnswer(answers))
    }

    @Test
    fun `expanded category helpers support initial expand collapse and invalid ids`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val groups = catalog.groupQuestionsForPresentation(emptyList())

        val initial = initialExpandedAffinityCategoryId(groups)
        assertEquals("MUSIC", initial)

        val collapsedFirst = toggledExpandedAffinityCategoryId(initial, "MUSIC")
        assertEquals(null, collapsedFirst)
        assertEquals(null, resolvedExpandedAffinityCategoryId(collapsedFirst, groups))

        val expandedSecond = toggledExpandedAffinityCategoryId(collapsedFirst, "PLANS")
        assertEquals("PLANS", expandedSecond)
        assertEquals("PLANS", resolvedExpandedAffinityCategoryId(expandedSecond, groups))

        val collapsedSecond = toggledExpandedAffinityCategoryId(expandedSecond, "PLANS")
        assertEquals(null, collapsedSecond)
        assertEquals(null, resolvedExpandedAffinityCategoryId("MISSING", groups))
    }
}
