package com.reals.app.ui.profile

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.AffinityAnswerType
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.root.AffinityAnswerMutationUiState
import com.reals.app.ui.root.AffinityQuestionSource
import com.reals.app.ui.root.AffinityQuestionnaireDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AffinityQuestionnairePresentationTest {
    @Test
    fun `overview action policy for zero answered shows start and no review button`() {
        val policy = AffinityQuestionnaireProgress(
            answeredCount = 0,
            totalQuestionCount = 2,
        ).overviewActionPolicy(hasReviewRows = false)

        assertEquals(AffinityOverviewPrimaryAction.Start, policy.primaryAction)
        assertTrue(policy.showExploreCategories)
        assertFalse(policy.showSecondaryReview)
        assertTrue(policy.showEmptyReviewText)
    }

    @Test
    fun `overview action policy for partially answered shows continue explore and review`() {
        val policy = AffinityQuestionnaireProgress(
            answeredCount = 1,
            totalQuestionCount = 2,
        ).overviewActionPolicy(hasReviewRows = true)

        assertEquals(AffinityOverviewPrimaryAction.Continue, policy.primaryAction)
        assertTrue(policy.showExploreCategories)
        assertTrue(policy.showSecondaryReview)
        assertFalse(policy.showEmptyReviewText)
    }

    @Test
    fun `overview action policy for all answered has one review action`() {
        val policy = AffinityQuestionnaireProgress(
            answeredCount = 2,
            totalQuestionCount = 2,
        ).overviewActionPolicy(hasReviewRows = true)

        assertEquals(AffinityOverviewPrimaryAction.Review, policy.primaryAction)
        assertTrue(policy.showExploreCategories)
        assertFalse(policy.showSecondaryReview)
        assertFalse(policy.showEmptyReviewText)
    }

    @Test
    fun `overview action policy for no answerable questions has no unusable actions`() {
        val policy = AffinityQuestionnaireProgress(
            answeredCount = 0,
            totalQuestionCount = 0,
        ).overviewActionPolicy(hasReviewRows = false)

        assertNull(policy.primaryAction)
        assertFalse(policy.showExploreCategories)
        assertFalse(policy.showSecondaryReview)
        assertTrue(policy.showEmptyReviewText)
    }

    @Test
    fun `parent mutation status policy is disabled on single question and enabled on parent surfaces`() {
        val mutation = AffinityAnswerMutationUiState(
            questionId = "MUSIC_DISCOVERY_001",
            pendingAnswerCode = "LOW",
            requestId = 1L,
        )

        assertFalse(shouldShowAffinityParentMutationStatus(showMutationStatus = false, mutation = mutation))
        assertTrue(shouldShowAffinityParentMutationStatus(showMutationStatus = true, mutation = mutation))
        assertFalse(shouldShowAffinityParentMutationStatus(showMutationStatus = true, mutation = null))
    }

    @Test
    fun `answerable filtering excludes unsupported types and malformed option lists`() {
        val catalog = catalogWithExtraQuestions(
            TestDtos.affinityQuestion("UNKNOWN_001", categoryId = "MUSIC", answerType = "FUTURE_TYPE"),
            TestDtos.affinityQuestion(
                id = "ONE_OPTION_001",
                categoryId = "MUSIC",
                options = listOf(TestDtos.affinityAnswerOption("ONLY", "Única", 1)),
            ),
        )

        val groups = catalog.groupQuestionsForPresentation(emptyList())

        assertEquals(listOf("MUSIC_DISCOVERY_001"), groups.first { it.category.id == "MUSIC" }.questions.map { it.id })
        assertFalse(catalog.questions.first { it.id == "UNKNOWN_001" }.isAnswerable())
        assertFalse(catalog.questions.first { it.id == "ONE_OPTION_001" }.isAnswerable())
    }

    @Test
    fun `progress counts only valid answers to answerable visible questions`() {
        val catalog = catalogWithExtraQuestions(
            TestDtos.affinityQuestion("UNKNOWN_001", categoryId = "MUSIC", answerType = "FUTURE_TYPE"),
            TestDtos.affinityQuestion(
                id = "ONE_OPTION_001",
                categoryId = "MUSIC",
                options = listOf(TestDtos.affinityAnswerOption("ONLY", "Única", 1)),
            ),
        )
        val answers = listOf(
            TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain(),
            TestDtos.affinityAnswer("PLANS_WEEKEND_001", 2, "QUIET").toDomain(),
            TestDtos.affinityAnswer("UNKNOWN_001", 1, "LOW").toDomain(),
            TestDtos.affinityAnswer("REMOVED_001", 1, "YES").toDomain(),
        )

        val progress = catalog.progress(answers)

        assertEquals(1, progress.answeredCount)
        assertEquals(2, progress.totalQuestionCount)
    }

    @Test
    fun `backend category question and option order is preserved`() {
        val catalog = TestDtos.affinityQuestionCatalog(
            categories = listOf(
                TestDtos.affinityQuestionCategory("EMPTY", "Vacía", displayOrder = 1),
                TestDtos.affinityQuestionCategory("MUSIC", "Música", displayOrder = 2),
                TestDtos.affinityQuestionCategory("PLANS", "Planes", displayOrder = 3),
            ),
            questions = listOf(
                TestDtos.affinityQuestion(
                    "MUSIC_2",
                    categoryId = "MUSIC",
                    options = listOf(
                        TestDtos.affinityAnswerOption("B", "Bastante", 2),
                        TestDtos.affinityAnswerOption("A", "Nada", 1),
                    ),
                ),
                TestDtos.affinityQuestion("PLANS_1", categoryId = "PLANS"),
                TestDtos.affinityQuestion("MUSIC_1", categoryId = "MUSIC"),
            ),
        ).toDomain()

        val groups = catalog.groupQuestionsForPresentation(emptyList())

        assertEquals(listOf("MUSIC", "PLANS"), groups.map { it.category.id })
        assertEquals(listOf("MUSIC_2", "MUSIC_1"), groups.first().questions.map { it.id })
        assertEquals(listOf("B", "A"), groups.first().questions.first().options.map { it.code })
    }

    @Test
    fun `first unanswered and Continue sequence do not wrap`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val firstAnswered = listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain())
        val allAnswered = firstAnswered + TestDtos.affinityAnswer("PLANS_WEEKEND_001", 1, "LOW").toDomain()

        assertEquals("MUSIC_DISCOVERY_001", catalog.firstUnansweredQuestion(emptyList())?.id)
        assertEquals("PLANS_WEEKEND_001", catalog.firstUnansweredQuestion(firstAnswered)?.id)
        assertEquals("PLANS_WEEKEND_001", catalog.nextContinueQuestion("MUSIC_DISCOVERY_001", firstAnswered)?.id)
        assertNull(catalog.nextContinueQuestion("PLANS_WEEKEND_001", emptyList()))
        assertNull(catalog.firstUnansweredQuestion(allAnswered))
    }

    @Test
    fun `category start and advance policies distinguish incomplete and complete categories`() {
        val catalog = TestDtos.affinityQuestionCatalog(
            questions = listOf(
                TestDtos.affinityQuestion("MUSIC_1", categoryId = "MUSIC"),
                TestDtos.affinityQuestion("MUSIC_2", categoryId = "MUSIC"),
                TestDtos.affinityQuestion("MUSIC_3", categoryId = "MUSIC"),
                TestDtos.affinityQuestion("PLANS_1", categoryId = "PLANS"),
            ),
        ).toDomain()
        val partialAnswers = listOf(TestDtos.affinityAnswer("MUSIC_1", 1, "VERY_HIGH").toDomain())
        val completeAnswers = listOf(
            TestDtos.affinityAnswer("MUSIC_1", 1, "VERY_HIGH").toDomain(),
            TestDtos.affinityAnswer("MUSIC_2", 1, "VERY_HIGH").toDomain(),
            TestDtos.affinityAnswer("MUSIC_3", 1, "VERY_HIGH").toDomain(),
        )

        assertEquals("MUSIC_2", catalog.firstCategoryQuestion("MUSIC", partialAnswers, reviewAll = false)?.id)
        assertEquals("MUSIC_3", catalog.nextCategoryQuestion("MUSIC", "MUSIC_2", partialAnswers, reviewAll = false)?.id)
        assertNull(catalog.nextCategoryQuestion("MUSIC", "MUSIC_3", partialAnswers, reviewAll = false))
        assertEquals("MUSIC_1", catalog.firstCategoryQuestion("MUSIC", completeAnswers, reviewAll = true)?.id)
        assertEquals("MUSIC_2", catalog.nextCategoryQuestion("MUSIC", "MUSIC_1", completeAnswers, reviewAll = true)?.id)
    }

    @Test
    fun `review rows resolve backend option labels and omit stale answers`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val answers = listOf(
            TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain(),
            TestDtos.affinityAnswer("PLANS_WEEKEND_001", 2, "QUIET").toDomain(),
            TestDtos.affinityAnswer("PLANS_WEEKEND_001", 1, "MISSING").toDomain(),
            TestDtos.affinityAnswer("REMOVED_001", 1, "YES").toDomain(),
        )

        val reviewRows = catalog.reviewRows(answers)

        assertEquals(1, reviewRows.size)
        assertEquals("Música", reviewRows.single().category.title)
        assertEquals("Mucho", reviewRows.single().rows.single().selectedOptionLabel)
    }

    @Test
    fun `valid answer pending presentation and selectable no-op policy`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val question = catalog.questions.first()
        val answers = listOf(TestDtos.affinityAnswer(question.id, 1, "LOW").toDomain())
        val unknown = question.copy(answerType = AffinityAnswerType.Unknown)

        assertEquals("LOW", question.currentValidAnswer(answers)?.answerCode)
        assertEquals("VERY_HIGH", question.presentedAnswerCode(answers, AffinityAnswerMutationUiState(question.id, "VERY_HIGH")))
        assertNull(question.presentedAnswerCode(answers, AffinityAnswerMutationUiState(question.id, null)))
        assertTrue(question.canSelectAnswerCode("LOW"))
        assertFalse(question.canSelectAnswerCode("MISSING"))
        assertNull(unknown.currentValidAnswer(answers))
    }

    @Test
    fun `invalid current question falls back to nearest parent`() {
        val catalog = TestDtos.affinityQuestionCatalog().toDomain()
        val answers = listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain())

        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            catalog.reconciledDestination(
                AffinityQuestionnaireDestination.Question("MISSING", AffinityQuestionSource.Continue),
                answers,
            ),
        )
        assertEquals(
            AffinityQuestionnaireDestination.Categories,
            catalog.reconciledDestination(
                AffinityQuestionnaireDestination.Question(
                    questionId = "MISSING",
                    source = AffinityQuestionSource.Category("MISSING_CATEGORY", reviewAll = false),
                ),
                answers,
            ),
        )
        assertEquals(
            AffinityQuestionnaireDestination.Review,
            catalog.reconciledDestination(
                AffinityQuestionnaireDestination.Question("PLANS_WEEKEND_001", AffinityQuestionSource.Review),
                answers,
            ),
        )
    }

    private fun catalogWithExtraQuestions(
        vararg extraQuestions: com.reals.app.data.dto.AffinityQuestionResponseDto,
    ) = TestDtos.affinityQuestionCatalog(
        questions = TestDtos.affinityQuestionCatalog().questions + extraQuestions,
    ).toDomain()
}
