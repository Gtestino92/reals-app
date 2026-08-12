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
    fun `home summary load installs catalog and answers without opening questionnaire`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(
                    listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH")),
                ),
            )
        }
        val harness = harness(api)

        harness.handler.loadHomeSummaryIfNeeded()
        advanceUntilIdle()

        val summary = harness.ready().affinityHomeSummary
        assertFalse(harness.ready().affinityQuestionnaire.open)
        assertEquals("profile-1", summary.profileId)
        assertEquals("catalog-1", summary.catalog?.catalogVersion)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), summary.answers.map { it.questionId })
        assertFalse(summary.loading)
        assertTrue(summary.loadAttempted)
        assertEquals(listOf("getAffinityQuestionCatalog", "getMyAffinityAnswers"), api.calls)
    }

    @Test
    fun `home summary failure is retained as non blocking unloaded state`() = runTest {
        val api = FakeRealsApi().apply {
            affinityQuestionCatalogResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
        }
        val harness = harness(api)

        harness.handler.loadHomeSummaryIfNeeded()
        advanceUntilIdle()

        val summary = harness.ready().affinityHomeSummary
        assertFalse(harness.ready().affinityQuestionnaire.open)
        assertNull(harness.ready().affinityQuestionnaire.error)
        assertEquals("profile-1", summary.profileId)
        assertNull(summary.catalog)
        assertFalse(summary.loading)
        assertTrue(summary.loadAttempted)
    }

    @Test
    fun `home summary stale response for previous profile is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetAffinityQuestionCatalogResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val harness = harness(api)

        harness.handler.loadHomeSummaryIfNeeded()
        runCurrent()
        started.await()
        harness.state.value = ready(profileId = "profile-2")

        release.complete(Unit)
        advanceUntilIdle()

        assertNull(harness.ready().affinityHomeSummary.catalog)
        assertEquals(null, harness.ready().affinityHomeSummary.profileId)
    }

    @Test
    fun `old home summary load cannot overwrite newer questionnaire answers`() = runTest {
        val oldAnswers = Response.success(
            TestDtos.affinityAnswers(
                listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "LOW")),
            ),
        )
        val newerAnswers = Response.success(
            TestDtos.affinityAnswers(
                listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH")),
            ),
        )
        val oldHomeCatalogStarted = CompletableDeferred<Unit>()
        val releaseOldHomeCatalog = CompletableDeferred<Unit>()
        var catalogCalls = 0
        var answersCalls = 0
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = oldAnswers
            beforeGetAffinityQuestionCatalogResponse = {
                catalogCalls += 1
                if (catalogCalls == 1) {
                    oldHomeCatalogStarted.complete(Unit)
                    releaseOldHomeCatalog.await()
                }
            }
            beforeGetMyAffinityAnswersResponse = {
                answersCalls += 1
                affinityAnswersResponse = if (answersCalls == 1) oldAnswers else newerAnswers
            }
        }
        val harness = harness(api)

        harness.handler.loadHomeSummaryIfNeeded()
        runCurrent()
        oldHomeCatalogStarted.await()

        harness.handler.open()
        runCurrent()

        assertEquals(
            listOf("VERY_HIGH"),
            harness.ready().affinityHomeSummary.answers.map { it.answerCode },
        )

        releaseOldHomeCatalog.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("VERY_HIGH"),
            harness.ready().affinityHomeSummary.answers.map { it.answerCode },
        )
    }

    @Test
    fun `questionnaire open reuses home summary before refreshing`() = runTest {
        val summary = AffinityHomeSummaryUiState(
            profileId = "profile-1",
            catalog = TestDtos.affinityQuestionCatalog().toDomain(),
            answers = listOf(TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", 1, "VERY_HIGH").toDomain()),
            loadAttempted = true,
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetAffinityQuestionCatalogResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val harness = harness(api, ready(homeSummary = summary))

        harness.handler.open()
        runCurrent()

        val opened = harness.ready().affinityQuestionnaire
        assertTrue(opened.open)
        assertEquals("catalog-1", opened.catalog?.catalogVersion)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), opened.answers.map { it.questionId })
        assertTrue(opened.refreshing)

        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `opening without a profile does nothing while draft and active profiles can open`() =
        runTest {
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
            assertEquals(
                AffinityQuestionnaireDestination.Overview,
                active.ready().affinityQuestionnaire.destination
            )
        }

    @Test
    fun `overview opens Continue Categories and Review destinations`() = runTest {
        val continueHarness =
            harness(initialState = ready(questionnaire = loadedQuestionnaire(answers = emptyList())))
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
        assertEquals(
            AffinityQuestionnaireDestination.Categories,
            categoriesHarness.ready().affinityQuestionnaire.destination
        )

        val reviewHarness = loadedHarness()
        reviewHarness.handler.openReview()
        assertEquals(
            AffinityQuestionnaireDestination.Review,
            reviewHarness.ready().affinityQuestionnaire.destination
        )
    }

    @Test
    fun `category and review entries open one question destinations`() = runTest {
        val incompleteCategory =
            harness(initialState = ready(questionnaire = loadedQuestionnaire(answers = emptyList())))
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
                        source = AffinityQuestionSource.Category(
                            categoryId = "MUSIC",
                            reviewAll = true
                        ),
                    ),
                ),
            ),
        )

        harness.handler.navigateBack()
        assertEquals(
            AffinityQuestionnaireDestination.Categories,
            harness.ready().affinityQuestionnaire.destination
        )
        assertTrue(harness.ready().affinityQuestionnaire.open)

        harness.handler.navigateBack()
        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            harness.ready().affinityQuestionnaire.destination
        )
        assertTrue(harness.ready().affinityQuestionnaire.open)

        harness.handler.navigateBack()
        assertFalse(harness.ready().affinityQuestionnaire.open)
        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            harness.ready().affinityQuestionnaire.destination
        )
    }

    @Test
    fun `skip and next advance through local source sequences`() = runTest {
        val harness =
            harness(initialState = ready(questionnaire = loadedQuestionnaire(answers = emptyList())))
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
                answers = listOf(
                    TestDtos.affinityAnswer(
                        "PLANS_WEEKEND_001",
                        1,
                        "VERY_HIGH",
                    ).toDomain(),
                ),
                draftQuestionId = "PLANS_WEEKEND_001",
                draftAnswerCode = "VERY_HIGH",
                message = "Respuesta guardada",
                mutationFeedbackQuestionId = "PLANS_WEEKEND_001",
            ),
        )
        harness.handler.nextQuestion()

        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            harness.ready().affinityQuestionnaire.destination,
        )
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
        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            harness.ready().affinityQuestionnaire.destination
        )
        assertEquals("catalog-1", harness.ready().affinityQuestionnaire.catalog?.catalogVersion)
        assertEquals(
            listOf("MUSIC_DISCOVERY_001"),
            harness.ready().affinityQuestionnaire.answers.map { it.questionId })
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
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer(
                            questionId = "MUSIC_DISCOVERY_001",
                            answerCode = "LOW",
                        ),
                    ),
                ),
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

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        harness.handler.nextQuestion()
        runCurrent()
        requestStarted.await()

        harness.state.value = harness.ready().copy(
            affinityQuestionnaire =
                harness.ready().affinityQuestionnaire.copy(
                    destination = AffinityQuestionnaireDestination.Review,
                ),
        )

        releaseResponse.complete(Unit)
        advanceUntilIdle()

        val state = harness.ready().affinityQuestionnaire

        assertEquals(
            AffinityQuestionnaireDestination.Review,
            state.destination,
        )
        assertEquals(
            listOf("LOW"),
            state.answers.map { it.answerCode },
        )
        assertNull(state.mutation)
        assertNull(state.message)
        assertNull(state.mutationFeedbackQuestionId)
    }

    @Test
    fun `successful Next persists answer advances and clears scoped feedback`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer(
                            questionId = "MUSIC_DISCOVERY_001",
                            answerCode = "LOW",
                        ),
                    ),
                ),
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

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        harness.handler.nextQuestion()
        advanceUntilIdle()

        val state = harness.ready().affinityQuestionnaire

        assertEquals(
            1,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )
        assertEquals(
            listOf("LOW"),
            state.answers.map { it.answerCode },
        )
        assertEquals(
            AffinityQuestionnaireDestination.Question(
                questionId = "PLANS_WEEKEND_001",
                source = AffinityQuestionSource.Continue,
            ),
            state.destination,
        )
        assertNull(state.mutation)
        assertNull(state.message)
        assertNull(state.mutationFeedbackQuestionId)
        assertEquals(
            "PLANS_WEEKEND_001",
            state.draftQuestionId,
        )
        assertNull(state.draftAnswerCode)
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
                        TestDtos.affinityAnswer(
                            "MUSIC_DISCOVERY_001",
                            1,
                            "VERY_HIGH",
                        ).toDomain(),
                    ),
                ).copy(
                    draftQuestionId = "MUSIC_DISCOVERY_001",
                    draftAnswerCode = "VERY_HIGH",
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
        assertEquals(
            "PLANS_WEEKEND_001",
            harness.ready().affinityQuestionnaire.draftQuestionId,
        )
        assertNull(harness.ready().affinityQuestionnaire.draftAnswerCode)
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

        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            harness.ready().affinityQuestionnaire.destination
        )
        assertNull(harness.ready().affinityQuestionnaire.mutationError)
        assertNull(harness.ready().affinityQuestionnaire.mutationFeedbackQuestionId)
        assertNull(harness.ready().affinityQuestionnaire.message)
    }

    @Test
    fun `navigating after legal mutation failure preserves routing error and clears scoped feedback`() =
        runTest {
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
    fun `navigating after terminal auth mutation failure preserves routing error and clears scoped feedback`() =
        runTest {
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
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer(
                            questionId = "MUSIC_DISCOVERY_001",
                            answerCode = "LOW",
                        ),
                    ),
                ),
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

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        assertEquals(
            "LOW",
            harness.ready().affinityQuestionnaire.draftAnswerCode,
        )
        assertEquals(
            0,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )

        harness.handler.nextQuestion()
        runCurrent()
        requestStarted.await()

        assertEquals(
            1,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )
        assertTrue(harness.ready().affinityQuestionnaire.mutation != null)

        harness.handler.navigateBack()

        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            harness.ready().affinityQuestionnaire.destination,
        )

        releaseResponse.complete(Unit)
        advanceUntilIdle()

        val state = harness.ready().affinityQuestionnaire

        assertEquals(
            listOf("LOW"),
            state.answers.map { it.answerCode },
        )
        assertNull(state.mutation)
        assertEquals(
            AffinityQuestionnaireDestination.Overview,
            state.destination,
        )
        assertNull(state.message)
        assertNull(state.mutationFeedbackQuestionId)
    }

    @Test
    fun `late ordinary failure after navigating Back preserves answers without parent feedback ownership`() =
        runTest {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()

            val api = FakeRealsApi().apply {
                beforePatchMyAffinityAnswersResponse = {
                    requestStarted.complete(Unit)
                    releaseResponse.await()
                }
                affinityAnswersResponse = backendErrorResponse(
                    statusCode = 500,
                    code = "SERVER_ERROR",
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

            val confirmedAnswers =
                harness.ready().affinityQuestionnaire.answers

            harness.handler.selectAnswer(
                questionId = "MUSIC_DISCOVERY_001",
                answerCode = "LOW",
            )

            harness.handler.nextQuestion()
            runCurrent()
            requestStarted.await()

            harness.handler.navigateBack()

            assertEquals(
                AffinityQuestionnaireDestination.Overview,
                harness.ready().affinityQuestionnaire.destination,
            )

            releaseResponse.complete(Unit)
            advanceUntilIdle()

            val state = harness.ready().affinityQuestionnaire

            assertEquals(confirmedAnswers, state.answers)
            assertNull(state.mutation)
            assertEquals(
                AffinityQuestionnaireDestination.Overview,
                state.destination,
            )
            assertTrue(state.mutationError is ApiError.Backend)
            assertNull(state.mutationFeedbackQuestionId)
            assertNull(state.message)
        }

    @Test
    fun `selecting a local draft clears old feedback without starting mutation`() = runTest {
        val api = FakeRealsApi()
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

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        val state = harness.ready().affinityQuestionnaire

        assertEquals("MUSIC_DISCOVERY_001", state.draftQuestionId)
        assertEquals("LOW", state.draftAnswerCode)
        assertNull(state.message)
        assertNull(state.mutationError)
        assertNull(state.mutationFeedbackQuestionId)
        assertNull(state.mutation)
        assertEquals(
            0,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )
    }

    @Test
    fun `refresh preserves valid destination and invalidates removed Continue question`() =
        runTest {
            val valid = harness(
                initialState = ready(
                    questionnaire = loadedQuestionnaire(
                        destination = AffinityQuestionnaireDestination.Categories,
                    ),
                ),
            )
            valid.handler.refresh()
            advanceUntilIdle()
            assertEquals(
                AffinityQuestionnaireDestination.Categories,
                valid.ready().affinityQuestionnaire.destination
            )

            val invalidApi = FakeRealsApi().apply {
                affinityQuestionCatalogResponse = Response.success(
                    TestDtos.affinityQuestionCatalog(
                        questions = listOf(
                            TestDtos.affinityQuestion(
                                "PLANS_WEEKEND_001",
                                categoryId = "PLANS"
                            )
                        ),
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

            assertEquals(
                AffinityQuestionnaireDestination.Overview,
                invalid.ready().affinityQuestionnaire.destination
            )
            assertEquals(
                listOf("PLANS_WEEKEND_001"),
                invalid.ready().affinityQuestionnaire.catalog?.questions?.map { it.id })
        }

    @Test
    fun `initial catalog and answers install atomically and failures do not install partial data`() =
        runTest {
            val success = harness()
            success.handler.open()
            advanceUntilIdle()

            assertEquals("catalog-1", success.ready().affinityQuestionnaire.catalog?.catalogVersion)
            assertEquals(
                listOf("MUSIC_DISCOVERY_001"),
                success.ready().affinityQuestionnaire.answers.map { it.questionId })
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
    fun `mutations are blocked while refresh is in flight and refresh completes normally`() =
        runTest {
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
            assertEquals(
                listOf("LOW"),
                harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
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
            affinityAnswersResponse = backendErrorResponse(
                statusCode = 500,
                code = "SERVER_ERROR",
            )
        }

        val patchHarness = harness(
            api = patchApi,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Continue,
                    ),
                ).copy(
                    draftQuestionId = "MUSIC_DISCOVERY_001",
                    draftAnswerCode = "VERY_HIGH",
                ),
            ),
        )

        val originalAnswers =
            patchHarness.ready().affinityQuestionnaire.answers

        patchHarness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        assertEquals(
            "LOW",
            patchHarness.ready().affinityQuestionnaire.draftAnswerCode,
        )
        assertEquals(
            0,
            patchApi.calls.count { it == "patchMyAffinityAnswers" },
        )

        patchHarness.handler.nextQuestion()
        advanceUntilIdle()

        val patchState =
            patchHarness.ready().affinityQuestionnaire

        assertEquals(
            1,
            patchApi.calls.count { it == "patchMyAffinityAnswers" },
        )
        assertEquals(originalAnswers, patchState.answers)
        assertNull(patchState.mutation)
        assertTrue(patchState.mutationError is ApiError.Backend)

        // The failed PATCH preserves the local draft so the user can retry.
        assertEquals(
            "MUSIC_DISCOVERY_001",
            patchState.draftQuestionId,
        )
        assertEquals("LOW", patchState.draftAnswerCode)

        val deleteApi = FakeRealsApi().apply {
            affinityAnswersResponse = backendErrorResponse(
                statusCode = 500,
                code = "SERVER_ERROR",
            )
        }

        val deleteHarness = harness(
            api = deleteApi,
            initialState = ready(
                questionnaire = loadedQuestionnaire(
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Review,
                    ),
                ).copy(
                    draftQuestionId = "MUSIC_DISCOVERY_001",
                    draftAnswerCode = "VERY_HIGH",
                ),
            ),
        )

        val deleteOriginalAnswers =
            deleteHarness.ready().affinityQuestionnaire.answers

        deleteHarness.handler.deleteAnswer("MUSIC_DISCOVERY_001")
        advanceUntilIdle()

        val deleteState =
            deleteHarness.ready().affinityQuestionnaire

        assertEquals(
            1,
            deleteApi.calls.count { it == "deleteMyAffinityAnswer" },
        )
        assertEquals(deleteOriginalAnswers, deleteState.answers)
        assertNull(deleteState.mutation)
        assertTrue(deleteState.mutationError is ApiError.Backend)

        // Failed DELETE must retain the confirmed visible selection.
        assertEquals(
            "MUSIC_DISCOVERY_001",
            deleteState.draftQuestionId,
        )
        assertEquals("VERY_HIGH", deleteState.draftAnswerCode)
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
        assertEquals(
            emptyList<String>(),
            harness.ready().affinityQuestionnaire.answers.map { it.questionId })
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
        assertEquals(
            emptyList<String>(),
            mutationHarness.ready().affinityQuestionnaire.answers.map { it.questionId })
    }

    @Test
    fun `response for different profile is ignored and unrelated Ready fields are preserved`() =
        runTest {
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
            assertEquals(
                "profile-2",
                (harness.ready().session.profileSnapshot as ProfileSnapshot.Found).profile.id
            )
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
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer(
                            questionId = "MUSIC_DISCOVERY_001",
                            answerCode = "LOW",
                        ),
                    ),
                ),
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

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        assertEquals(
            0,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )

        harness.handler.nextQuestion()
        runCurrent()
        patchStarted.await()

        assertEquals(
            1,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )
        assertTrue(
            harness.ready().affinityQuestionnaire.mutation != null,
        )

        harness.handler.close()
        harness.handler.open()
        runCurrent()

        assertTrue(harness.ready().affinityQuestionnaire.open)
        assertTrue(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(
            0,
            api.calls.count { it == "getAffinityQuestionCatalog" },
        )
        assertFalse(patchReleased)

        releasePatch.complete(Unit)
        runCurrent()
        loadStarted.await()

        assertEquals(
            listOf("LOW"),
            harness.ready().affinityQuestionnaire.answers.map { it.answerCode },
        )

        releaseLoad.complete(Unit)
        advanceUntilIdle()

        val state = harness.ready().affinityQuestionnaire

        assertTrue(state.open)
        assertFalse(state.refreshing)
        assertNull(state.mutation)
        assertEquals(
            listOf("LOW"),
            state.answers.map { it.answerCode },
        )
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
        assertEquals(
            emptyList<String>(),
            harness.ready().affinityQuestionnaire.answers.map { it.answerCode })

        releaseLoad.complete(Unit)
        advanceUntilIdle()

        assertTrue(harness.ready().affinityQuestionnaire.open)
        assertFalse(harness.ready().affinityQuestionnaire.refreshing)
        assertEquals(
            emptyList<String>(),
            harness.ready().affinityQuestionnaire.answers.map { it.answerCode })
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
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer(
                            questionId = "MUSIC_DISCOVERY_001",
                            answerCode = "LOW",
                        ),
                    ),
                ),
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
                    draftQuestionId = "MUSIC_DISCOVERY_001",
                    draftAnswerCode = "VERY_HIGH",
                ),
            ),
        )

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        harness.handler.nextQuestion()
        runCurrent()
        patchStarted.await()

        val firstMutation =
            harness.ready().affinityQuestionnaire.mutation

        assertEquals(1L, firstMutation?.requestId)

        val newerMutation = AffinityAnswerMutationUiState(
            questionId = "MUSIC_DISCOVERY_001",
            pendingAnswerCode = "LOW",
            requestId = 2L,
        )

        /*
         * Simulate a newer generation owning the same logical answer before
         * generation 1 completes.
         */
        harness.state.value = harness.ready().copy(
            affinityQuestionnaire =
                harness.ready().affinityQuestionnaire.copy(
                    mutation = newerMutation,
                ),
        )

        releasePatch.complete(Unit)
        advanceUntilIdle()

        val state = harness.ready().affinityQuestionnaire

        assertEquals(newerMutation, state.mutation)

        /*
         * Generation 1 must not install its response because generation 2
         * owns the state, even though questionId and answerCode are identical.
         */
        assertEquals(
            listOf("VERY_HIGH"),
            state.answers.map { it.answerCode },
        )
    }

    @Test
    fun `user can revert local draft to confirmed answer before saving`() = runTest {
        val harness = loadedHarness()

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")

        assertEquals(
            "LOW",
            harness.ready().affinityQuestionnaire.draftAnswerCode,
        )

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "VERY_HIGH")

        assertEquals(
            "VERY_HIGH",
            harness.ready().affinityQuestionnaire.draftAnswerCode,
        )
        assertEquals(
            0,
            harness.api.calls.count { it == "patchMyAffinityAnswers" },
        )
    }

    @Test
    fun `selection remains local until Next persists it`() = runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer(
                            questionId = "MUSIC_DISCOVERY_001",
                            answerCode = "LOW",
                        ),
                    ),
                ),
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

        harness.handler.selectAnswer(
            questionId = "MUSIC_DISCOVERY_001",
            answerCode = "LOW",
        )

        val localState = harness.ready().affinityQuestionnaire

        assertEquals(
            "MUSIC_DISCOVERY_001",
            localState.draftQuestionId,
        )
        assertEquals("LOW", localState.draftAnswerCode)
        assertNull(localState.mutation)
        assertEquals(
            0,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )

        harness.handler.nextQuestion()
        advanceUntilIdle()

        val persistedState = harness.ready().affinityQuestionnaire

        assertEquals(
            1,
            api.calls.count { it == "patchMyAffinityAnswers" },
        )
        assertEquals(
            listOf("LOW"),
            persistedState.answers.map { it.answerCode },
        )
        assertNull(persistedState.mutation)
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
        homeSummary: AffinityHomeSummaryUiState = AffinityHomeSummaryUiState(),
        questionnaire: AffinityQuestionnaireUiState = AffinityQuestionnaireUiState(),
    ): RealsRootUiState.Ready = RealsRootUiState.Ready(
        session = TestDomain.session().copy(
            profileSnapshot = ProfileSnapshot.Found(
                TestDtos.profile(status = profileStatus).copy(id = profileId).toDomain(),
            ),
        ),
        editingActiveProfile = editingActiveProfile,
        affinityHomeSummary = homeSummary,
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
