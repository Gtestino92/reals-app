package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.AuthFailureReason
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.AffinityQuestionRepository
import com.reals.app.di.AffinityFeatureDependencies
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.usecase.DeleteMyAffinityAnswerUseCase
import com.reals.app.domain.usecase.GetAffinityQuestionCatalogUseCase
import com.reals.app.domain.usecase.GetMyAffinityAnswersUseCase
import com.reals.app.domain.usecase.PatchMyAffinityAnswerUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AffinityQuestionnaireOperationHandlerTest {
    @Test
    fun `opening without a profile does nothing while draft and active profiles can open`() = runTest {
        val missing = harness(
            initialState = RealsRootUiState.Ready(
                TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Missing),
            ),
        )
        missing.handler.open()
        advanceUntilIdle()
        assertFalse(missing.ready().affinityQuestionnaire.open)

        val draft = harness(initialState = ready(profileStatus = "DRAFT"))
        draft.handler.open()
        advanceUntilIdle()
        assertTrue(draft.ready().affinityQuestionnaire.open)

        val active = harness(initialState = ready(profileStatus = "ACTIVE"))
        active.handler.open()
        advanceUntilIdle()
        assertTrue(active.ready().affinityQuestionnaire.open)
        assertEquals(AffinityQuestionnaireDestination.Overview, active.ready().affinityQuestionnaire.destination)
    }

    @Test
    fun `overview opens Continue Categories and Review destinations`() = runTest {
        val continueHarness = harness(initialState = ready(questionnaire = loadedQuestionnaire(answers = emptyList())))
        continueHarness.handler.openContinue()
        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "MUSIC_DISCOVERY_001",
                source = AffinityQuestionSource.Continue,
            ),
            continueHarness.ready().affinityQuestionnaire.destination,
        )

        val categoriesHarness = loadedHarness()
        categoriesHarness.handler.openCategories()
        assertEquals(AffinityQuestionnaireDestination.Categories, categoriesHarness.ready().affinityQuestionnaire.destination)

        val reviewHarness = loadedHarness()
        reviewHarness.handler.openReview()
        assertEquals(AffinityQuestionnaireDestination.Review, reviewHarness.ready().affinityQuestionnaire.destination)
    }

    @Test
    fun `category and review entries open one question destinations`() = runTest {
        val incompleteCategory = harness(initialState = ready(questionnaire = loadedQuestionnaire(answers = emptyList())))
        incompleteCategory.handler.openCategory("MUSIC")
        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "MUSIC_DISCOVERY_001",
                source = AffinityQuestionSource.Category(categoryId = "MUSIC", reviewAll = false),
            ),
            incompleteCategory.ready().affinityQuestionnaire.destination,
        )

        val completeCategory = loadedHarness()
        completeCategory.handler.openCategory("MUSIC")
        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "MUSIC_DISCOVERY_001",
                source = AffinityQuestionSource.Category(categoryId = "MUSIC", reviewAll = true),
            ),
            completeCategory.ready().affinityQuestionnaire.destination,
        )

        val review = loadedHarness()
        review.handler.openReviewedAnswer("MUSIC_DISCOVERY_001")
        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "MUSIC_DISCOVERY_001",
                source = AffinityQuestionSource.Review,
            ),
            review.ready().affinityQuestionnaire.destination,
        )
    }

    @Test
    fun `internal Back follows questionnaire hierarchy and closes from Overview`() = runTest {
        val harness = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Category(categoryId = "MUSIC", reviewAll = true),
                    ),
                ),
            ),
        )

        harness.handler.navigateBack()
        assertEquals(AffinityQuestionnaireDestination.Categories, harness.ready().affinityQuestionnaire.destination)
        assertTrue(harness.ready().affinityQuestionnaire.open)

        harness.handler.navigateBack()
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
        assertTrue(harness.ready().affinityQuestionnaire.open)

        harness.handler.navigateBack()
        assertFalse(harness.ready().affinityQuestionnaire.open)
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
    }

    @Test
    fun `skip and next advance through local source sequences`() = runTest {
        val harness = harness(initialState = ready(questionnaire = loadedQuestionnaire(answers = emptyList())))
        harness.handler.openContinue()
        harness.state.value = harness.ready().copy(
            affinityQuestionnaire = harness.ready().affinityQuestionnaire.copy(
                message = "Respuesta guardada",
                mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
            ),
        )
        harness.handler.skipQuestion()
        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "PLANS_WEEKEND_001",
                source = AffinityQuestionSource.Continue,
            ),
            harness.ready().affinityQuestionnaire.destination,
        )
        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)

        harness.state.value = harness.ready().copy(
            affinityQuestionnaire = harness.ready().affinityQuestionnaire.copy(
                answers = listOf(TestDtos.affinityAnswer("PLANS_WEEKEND_001", 1, "VERY_HIGH").toDomain()),
                message = "Respuesta guardada",
                mutationFeedbackQuestionId = "PLANS_WEEKEND_001",
            ),
        )
        harness.handler.nextQuestion()
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `closing resets destination while preserving retained content`() = runTest {
        val harness = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Review,
                ).copy(
                    message = "Respuesta guardada",
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                ),
            ),
        )

        harness.handler.close()

        assertFalse(harness.ready().affinityQuestionnaire.open)
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
        assertEquals("catalog-1", harness.ready().affinityQuestionnaire.catalog?.catalogVersion)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), harness.ready().affinityQuestionnaire.answers.map { it.questionId })
        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `mutation result preserves destination changed while request is active`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePatchMyAffinityAnswersResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ),
            ),
        )

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        requestStarted.await()
        harness.state.value = harness.ready().copy(
            affinityQuestionnaire = harness.ready().affinityQuestionnaire.copy(
                destination = AffinityQuestionnaireDestination.Review,
            ),
        )

        releaseResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals(AffinityQuestionnaireDestination.Review, harness.ready().affinityQuestionnaire.destination)
        assertEquals(listOf("LOW"), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `successful mutation on same question owns feedback by question`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ),
            ),
        )

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        advanceUntilIdle()

        assertEquals("Respuesta guardada", harness.ready().affinityQuestionnaire.message)
        assertEquals("MUSIC_DISCOVERY_001", harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `navigating to another question clears previous success feedback`() = runTest {
        val harness = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                    answers = listOf(
                        TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain(),
                    ),
                ).copy(
                    message = "Respuesta guardada",
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                ),
            ),
        )

        harness.handler.nextQuestion()

        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "PLANS_WEEKEND_001",
                source = AffinityQuestionSource.Continue,
            ),
            harness.ready().affinityQuestionnaire.destination,
        )
        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationError)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `navigating after ordinary mutation failure clears scoped feedback`() = runTest {
        val ordinaryError = ApiError.Backend(
            statusCode = 500,
            code = "SERVER_ERROR",
            error = "Server",
            message = "server error",
        )
        val harness = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ).copy(
                    mutationError = ordinaryError,
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                    message = "Respuesta guardada",
                ),
            ),
        )

        harness.handler.navigateBack()

        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
        assertNull(harness.ready().affinityQuestionnaire.mutationError)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
        assertNull(harness.ready().affinityQuestionnaire.message)
    }

    @Test
    fun `navigating after legal mutation failure preserves routing error and clears scoped feedback`() = runTest {
        val legalError = ApiError.Backend(
            statusCode = 409,
            code = "LEGAL_ACTION_REQUIRED",
            error = "Conflict",
            message = "legal action required",
        )
        val harness = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ).copy(
                    mutationError = legalError,
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                    message = "Respuesta guardada",
                ),
            ),
        )

        harness.handler.navigateBack()

        assertEquals(legalError, harness.ready().affinityQuestionnaire.mutationError)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
        assertNull(harness.ready().affinityQuestionnaire.message)
    }

    @Test
    fun `navigating after terminal auth mutation failure preserves routing error and clears scoped feedback`() = runTest {
        val terminalError = ApiError.Auth(
            reason = AuthFailureReason.NOT_SIGNED_IN,
            message = "signed out",
        )
        val harness = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ).copy(
                    mutationError = terminalError,
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                    message = "Respuesta guardada",
                ),
            ),
        )

        harness.handler.navigateBack()

        assertEquals(terminalError, harness.ready().affinityQuestionnaire.mutationError)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
        assertNull(harness.ready().affinityQuestionnaire.message)
    }

    @Test
    fun `late success after navigating Back updates answers without parent feedback`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePatchMyAffinityAnswersResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ),
            ),
        )

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        requestStarted.await()
        harness.handler.navigateBack()
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)

        releaseResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("LOW"), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
        assertNull(harness.ready().affinityQuestionnaire.mutation)
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `late ordinary failure after navigating Back preserves answers without parent feedback ownership`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePatchMyAffinityAnswersResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }
            affinityAnswersResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ),
            ),
        )
        val confirmedAnswers = harness.ready().affinityQuestionnaire.answers

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        requestStarted.await()
        harness.handler.navigateBack()
        releaseResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals(confirmedAnswers, harness.ready().affinityQuestionnaire.answers)
        assertNull(harness.ready().affinityQuestionnaire.mutation)
        assertEquals(AffinityQuestionnaireDestination.Overview, harness.ready().affinityQuestionnaire.destination)
        assertTrue(harness.ready().affinityQuestionnaire.mutationError is ApiError.Backend)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
    }

    @Test
    fun `starting new mutation clears old feedback`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePatchMyAffinityAnswersResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ).copy(
                    message = "Respuesta guardada",
                    mutationError = ApiError.Unexpected("old"),
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                ),
            ),
        )

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        requestStarted.await()

        assertNull(harness.ready().affinityQuestionnaire.message)
        assertNull(harness.ready().affinityQuestionnaire.mutationError)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)

        releaseResponse.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `refresh preserves valid destination and invalidates removed Continue question`() = runTest {
        val valid = harness(
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Categories,
                ),
            ),
        )
        valid.handler.refresh()
        advanceUntilIdle()
        assertEquals(AffinityQuestionnaireDestination.Categories, valid.ready().affinityQuestionnaire.destination)

        val invalidApi = FakeRealsApi().apply {
            affinityQuestionCatalogResponse = Response.success(
                TestDtos.affinityQuestionCatalog(
                    questions = listOf(TestDtos.affinityQuestion("PLANS_WEEKEND_001", categoryId = "PLANS")),
                )
            )
        }
        val invalid = harness(
            api = invalidApi,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ),
            ),
        )

        invalid.handler.refresh()
        advanceUntilIdle()

        assertEquals(AffinityQuestionnaireDestination.Overview, invalid.ready().affinityQuestionnaire.destination)
        assertEquals(listOf("PLANS_WEEKEND_001"), invalid.ready().affinityQuestionnaire.catalog?.questions?.map { it.id })
    }

    @Test
    fun `initial catalog and answers install atomically and failures do not install partial data`() = runTest {
        val success = harness()
        success.handler.open()
        advanceUntilIdle()

        assertEquals("catalog-1", success.ready().affinityQuestionnaire.catalog?.catalogVersion)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), success.ready().affinityQuestionnaire.answers.map { it.questionId })
        assertFalse(success.ready().affinityQuestionnaire.loading)

        val catalogFailure = harness(
            api = FakeRealsApi().apply {
                affinityQuestionCatalogResponse = backendErrorResponse(500, "SERVER_ERROR")
            },
        )
        catalogFailure.handler.open()
        advanceUntilIdle()
        assertNull(catalogFailure.ready().affinityQuestionnaire.catalog)
        assertTrue(catalogFailure.ready().affinityQuestionnaire.error is ApiError.Backend)

        val answersFailure = harness(
            api = FakeRealsApi().apply {
                affinityAnswersResponse = backendErrorResponse(500, "SERVER_ERROR")
            },
        )
        answersFailure.handler.open()
        advanceUntilIdle()
        assertNull(answersFailure.ready().affinityQuestionnaire.catalog)
        assertTrue(answersFailure.ready().affinityQuestionnaire.error is ApiError.Backend)
    }

    @Test
    fun `refresh retains content and refresh failure preserves snapshot`() = runTest {
        val retainedCatalog = TestDtos.affinityQuestionCatalog().toDomain()
        val retainedAnswers = listOf(TestDtos.affinityAnswer(answerCode = "LOW").toDomain())
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetAffinityQuestionCatalogResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }
            affinityAnswersResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = AffinityQuestionnaireUiState(
                    open = true,
                    profileId = "profile-1",
                    catalog = retainedCatalog,
                    answers = retainedAnswers,
                ),
            ),
        )

        harness.handler.refresh()
        runCurrent()
        requestStarted.await()
        assertTrue(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(retainedCatalog, harness.ready().affinityQuestionnaire.catalog)

        releaseResponse.complete(Unit)
        advanceUntilIdle()
        assertEquals(retainedCatalog, harness.ready().affinityQuestionnaire.catalog)
        assertEquals(retainedAnswers, harness.ready().affinityQuestionnaire.answers)
        assertTrue(harness.ready().affinityQuestionnaire.error is ApiError.Backend)
        assertFalse(harness.ready().affinityQuestionnaire.refreshing)
    }

    @Test
    fun `mutations are blocked while refresh is in flight and refresh completes normally`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetAffinityQuestionCatalogResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = loadedHarness(api)

        harness.handler.refresh()
        runCurrent()
        requestStarted.await()

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        harness.handler.deleteAnswer("MUSIC_DISCOVERY_001")

        assertEquals(0, api.calls.count { it == "patchMyAffinityAnswers" })
        assertEquals(0, api.calls.count { it == "deleteMyAffinityAnswer" })

        releaseResponse.complete(Unit)
        advanceUntilIdle()

        assertFalse(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(listOf("LOW"), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
    }

    @Test
    fun `successful DELETE replaces answers and clears previous saved feedback`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse =
                Response.success(TestDtos.affinityAnswers(emptyList()))
        }
        val harness = harness(
            api = api,
            initialState = ready(
                questionnaire = loadedQuestionnaire().copy(
                    message = "Respuesta guardada",
                    mutationFeedbackQuestionId = "MUSIC_DISCOVERY_001",
                ),
            ),
        )

        harness.handler.deleteAnswer("MUSIC_DISCOVERY_001")
        advanceUntilIdle()

        val state = harness.ready().affinityQuestionnaire

        assertEquals(listOf("deleteMyAffinityAnswer"), api.calls)
        assertEquals(emptyList<String>(), state.answers.map { it.questionId })
        assertNull(state.mutation)
        assertNull(state.message)
        assertNull(state.mutationFeedbackQuestionId)
    }

    @Test
    fun `same answer invalid question and invalid option are ignored`() = runTest {
        val harness = loadedHarness()

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "VERY_HIGH")
        harness.handler.selectAnswer("MISSING", "LOW")
        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "MISSING")
        advanceUntilIdle()

        assertTrue(harness.api.calls.isEmpty())
        assertNull(harness.ready().affinityQuestionnaire.mutation)
    }

    @Test
    fun `failed PATCH and DELETE clear mutation and preserve confirmed answers`() = runTest {
        val patchApi = FakeRealsApi().apply {
            affinityAnswersResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val patchHarness = loadedHarness(patchApi)
        val originalAnswers = patchHarness.ready().affinityQuestionnaire.answers

        patchHarness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        advanceUntilIdle()

        assertEquals(originalAnswers, patchHarness.ready().affinityQuestionnaire.answers)
        assertNull(patchHarness.ready().affinityQuestionnaire.mutation)
        assertTrue(patchHarness.ready().affinityQuestionnaire.mutationError is ApiError.Backend)

        val deleteApi = FakeRealsApi().apply {
            affinityAnswersResponse = backendErrorResponse(500, "SERVER_ERROR")
        }
        val deleteHarness = loadedHarness(deleteApi)

        deleteHarness.handler.deleteAnswer("MUSIC_DISCOVERY_001")
        advanceUntilIdle()

        assertEquals(originalAnswers, deleteHarness.ready().affinityQuestionnaire.answers)
        assertNull(deleteHarness.ready().affinityQuestionnaire.mutation)
        assertTrue(deleteHarness.ready().affinityQuestionnaire.mutationError is ApiError.Backend)
    }

    @Test
    fun `successful DELETE replaces complete answer list`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(TestDtos.affinityAnswers(emptyList()))
        }
        val harness = loadedHarness(api)

        harness.handler.deleteAnswer("MUSIC_DISCOVERY_001")
        advanceUntilIdle()

        assertEquals(listOf("deleteMyAffinityAnswer"), api.calls)
        assertEquals(emptyList<String>(), harness.ready().affinityQuestionnaire.answers.map { it.questionId })
        assertNull(harness.ready().affinityQuestionnaire.mutation)
    }

    @Test
    fun `closing during load or mutation does not reopen or install late response`() = runTest {
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        val loadingHarness = harness(
            api = FakeRealsApi().apply {
                beforeGetAffinityQuestionCatalogResponse = {
                    loadStarted.complete(Unit)
                    releaseLoad.await()
                }
            },
        )
        loadingHarness.handler.open()
        runCurrent()
        loadStarted.await()
        loadingHarness.handler.close()
        releaseLoad.complete(Unit)
        advanceUntilIdle()
        assertFalse(loadingHarness.ready().affinityQuestionnaire.open)
        assertNull(loadingHarness.ready().affinityQuestionnaire.catalog)

        val mutationStarted = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        val mutationHarness = loadedHarness(
            FakeRealsApi().apply {
                beforeDeleteMyAffinityAnswerResponse = {
                    mutationStarted.complete(Unit)
                    releaseMutation.await()
                }
                affinityAnswersResponse = Response.success(TestDtos.affinityAnswers(emptyList()))
            }
        )
        mutationHarness.handler.deleteAnswer("MUSIC_DISCOVERY_001")
        runCurrent()
        mutationStarted.await()
        mutationHarness.handler.close()
        releaseMutation.complete(Unit)
        advanceUntilIdle()
        assertFalse(mutationHarness.ready().affinityQuestionnaire.open)
        assertEquals(emptyList<String>(), mutationHarness.ready().affinityQuestionnaire.answers.map { it.questionId })
    }

    @Test
    fun `response for different profile is ignored and unrelated Ready fields are preserved`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetAffinityQuestionCatalogResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val harness = harness(api, ready(editingActiveProfile = true))
        harness.handler.open()
        runCurrent()
        started.await()
        harness.state.value = ready(
            profileId = "profile-2",
            editingActiveProfile = true,
            questionnaire = AffinityQuestionnaireUiState(open = true, profileId = "profile-2"),
        )

        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(harness.ready().editingActiveProfile)
        assertEquals("profile-2", (harness.ready().session.profileSnapshot as ProfileSnapshot.Found).profile.id)
        assertNull(harness.ready().affinityQuestionnaire.catalog)
    }

    @Test
    fun `active profile remains active after answer change`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = loadedHarness(api, ready(profileStatus = "ACTIVE"))

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        advanceUntilIdle()

        val profile = (harness.ready().session.profileSnapshot as ProfileSnapshot.Found).profile
        assertEquals(com.reals.app.domain.model.ProfileStatus.Active, profile.status)
    }

    @Test
    fun `close reopen during delayed PATCH waits and reloads authoritative answers`() = runTest {
        val patchStarted = CompletableDeferred<Unit>()
        val releasePatch = CompletableDeferred<Unit>()
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        var patchReleased = false
        val api = FakeRealsApi().apply {
            beforePatchMyAffinityAnswersResponse = {
                patchStarted.complete(Unit)
                releasePatch.await()
                patchReleased = true
            }
            beforeGetAffinityQuestionCatalogResponse = {
                loadStarted.complete(Unit)
                releaseLoad.await()
            }
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = loadedHarness(api)

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        patchStarted.await()
        harness.handler.close()
        harness.handler.open()
        runCurrent()

        assertTrue(harness.ready().affinityQuestionnaire.open)
        assertTrue(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(0, api.calls.count { it == "getAffinityQuestionCatalog" })
        assertFalse(patchReleased)

        releasePatch.complete(Unit)
        runCurrent()
        loadStarted.await()
        assertEquals(listOf("LOW"), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })

        releaseLoad.complete(Unit)
        advanceUntilIdle()

        assertTrue(harness.ready().affinityQuestionnaire.open)
        assertFalse(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(listOf("LOW"), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
    }

    @Test
    fun `close reopen during delayed DELETE waits and reloads authoritative answers`() = runTest {
        val deleteStarted = CompletableDeferred<Unit>()
        val releaseDelete = CompletableDeferred<Unit>()
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        var deleteReleased = false
        val api = FakeRealsApi().apply {
            beforeDeleteMyAffinityAnswerResponse = {
                deleteStarted.complete(Unit)
                releaseDelete.await()
                deleteReleased = true
            }
            beforeGetAffinityQuestionCatalogResponse = {
                loadStarted.complete(Unit)
                releaseLoad.await()
            }
            affinityAnswersResponse = Response.success(TestDtos.affinityAnswers(emptyList()))
        }
        val harness = loadedHarness(api)

        harness.handler.deleteAnswer("MUSIC_DISCOVERY_001")
        runCurrent()
        deleteStarted.await()
        harness.handler.close()
        harness.handler.open()
        runCurrent()

        assertTrue(harness.ready().affinityQuestionnaire.open)
        assertTrue(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(0, api.calls.count { it == "getAffinityQuestionCatalog" })
        assertFalse(deleteReleased)

        releaseDelete.complete(Unit)
        runCurrent()
        loadStarted.await()
        assertEquals(emptyList<String>(), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })

        releaseLoad.complete(Unit)
        advanceUntilIdle()

        assertTrue(harness.ready().affinityQuestionnaire.open)
        assertFalse(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(emptyList<String>(), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
    }

    @Test
    fun `old identical mutation response cannot clear newer mutation generation`() = runTest {
        val patchStarted = CompletableDeferred<Unit>()
        val releasePatch = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforePatchMyAffinityAnswersResponse = {
                patchStarted.complete(Unit)
                releasePatch.await()
            }
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(listOf(TestDtos.affinityAnswer(answerCode = "LOW")))
            )
        }
        val harness = loadedHarness(api)

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        patchStarted.await()
        harness.state.value = harness.ready().copy(
            affinityQuestionnaire = harness.ready().affinityQuestionnaire.copy(
                mutation = AffinityAnswerMutationUiState(
                    questionId = "MUSIC_DISCOVERY_001",
                    pendingAnswerCode = "LOW",
                    requestId = 2L,
                ),
            ),
        )

        releasePatch.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            AffinityAnswerMutationUiState(
                questionId = "MUSIC_DISCOVERY_001",
                pendingAnswerCode = "LOW",
                requestId = 2L,
            ),
            harness.ready().affinityQuestionnaire.mutation,
        )
        assertEquals(listOf("VERY_HIGH"), harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
    }

    private fun TestScope.loadedHarness(
        api: FakeRealsApi = FakeRealsApi(),
        initialState: RealsRootUiState.Ready = ready(),
    ): Harness {
        return harness(
            api = api,
            initialState = initialState.copy(
                affinityQuestionnaire = AffinityQuestionnaireUiState(
                    open = true,
                    profileId = (initialState.session.profileSnapshot as ProfileSnapshot.Found).profile.id,
                    catalog = TestDtos.affinityQuestionCatalog().toDomain(),
                    answers = listOf(TestDtos.affinityAnswer().toDomain()),
                ),
            ),
        )
    }

    private fun loadedQuestionnaire(
        destination: AffinityQuestionnaireDestination = AffinityQuestionnaireDestination.Overview,
        answers: List<com.reals.app.domain.model.AffinityAnswer> =
            listOf(TestDtos.affinityAnswer().toDomain()),
    ): AffinityQuestionnaireUiState = AffinityQuestionnaireUiState(
        open = true,
        profileId = "profile-1",
        destination = destination,
        catalog = TestDtos.affinityQuestionCatalog().toDomain(),
        answers = answers,
    )

    private fun TestScope.harness(
        api: FakeRealsApi = FakeRealsApi(),
        initialState: RealsRootUiState.Ready = ready(),
    ): Harness {
        val repository = AffinityQuestionRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        val state = MutableStateFlow<RealsRootUiState>(initialState)
        val handler = AffinityQuestionnaireOperationHandler(
            uiState = state,
            dependencies = AffinityFeatureDependencies(
                getCatalog = GetAffinityQuestionCatalogUseCase(repository),
                getMyAnswers = GetMyAffinityAnswersUseCase(repository),
                patchAnswer = PatchMyAffinityAnswerUseCase(repository),
                deleteAnswer = DeleteMyAffinityAnswerUseCase(repository),
            ),
            scope = this,
        )
        return Harness(api, state, handler)
    }

    private fun ready(
        profileId: String = "profile-1",
        profileStatus: String = "ACTIVE",
        editingActiveProfile: Boolean = false,
        questionnaire: AffinityQuestionnaireUiState = AffinityQuestionnaireUiState(),
    ): RealsRootUiState.Ready = RealsRootUiState.Ready(
        session = TestDomain.session().copy(
            profileSnapshot = ProfileSnapshot.Found(
                TestDtos.profile(status = profileStatus).copy(id = profileId).toDomain(),
            ),
        ),
        editingActiveProfile = editingActiveProfile,
        affinityQuestionnaire = questionnaire,
    )

    private data class Harness(
        val api: FakeRealsApi,
        val state: MutableStateFlow<RealsRootUiState>,
        val handler: AffinityQuestionnaireOperationHandler,
    ) {
        fun ready(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }
}
