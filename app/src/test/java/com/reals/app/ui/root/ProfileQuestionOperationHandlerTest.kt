package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.AuthFailureReason
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.ProfileQuestionRepository
import com.reals.app.di.ProfileQuestionFeatureDependencies
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.usecase.DeleteMyProfileQuestionAnswerUseCase
import com.reals.app.domain.usecase.GetMyProfileQuestionAnswersUseCase
import com.reals.app.domain.usecase.GetProfileQuestionCatalogUseCase
import com.reals.app.domain.usecase.ReplaceMyProfileQuestionSelectionsUseCase
import com.reals.app.domain.usecase.UpsertMyProfileQuestionAnswerUseCase
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
class ProfileQuestionOperationHandlerTest {
    @Test
    fun `successful upsert installs authoritative answers`() = runTest {
        val api = FakeRealsApi().apply {
            profileQuestionAnswersResponse = Response.success(
                TestDtos.profileQuestionAnswers(
                    listOf(TestDtos.profileQuestionAnswer(answer = "Autoritativa")),
                ),
            )
        }
        val harness = harness(api = api, initialState = ready(profileQuestions = loadedState()))

        harness.handler.saveAnswer("PERFECT_SUNDAY_001", "  Local  ")
        advanceUntilIdle()

        assertEquals("Local", api.upsertProfileQuestionAnswerBody?.answer)
        assertEquals(listOf("Autoritativa"), harness.ready().profileQuestions.answers.map { it.answer })
        assertNull(harness.ready().profileQuestions.mutation)
    }

    @Test
    fun `successful delete installs compacted authoritative selections`() = runTest {
        val api = FakeRealsApi().apply {
            profileQuestionAnswersResponse = Response.success(
                TestDtos.profileQuestionAnswers(
                    listOf(TestDtos.profileQuestionAnswer("LIFE_SOUNDTRACK_001", selectedPosition = 1)),
                ),
            )
        }
        val harness = harness(api = api, initialState = ready(profileQuestions = loadedState()))

        harness.handler.deleteAnswer("PERFECT_SUNDAY_001")
        advanceUntilIdle()

        assertEquals(listOf("LIFE_SOUNDTRACK_001" to 1), harness.ready().profileQuestions.answers.map { it.questionId to it.selectedPosition })
    }

    @Test
    fun `successful selection replacement returns to Overview with authoritative state and feedback`() =
        runTest {
            val api = FakeRealsApi().apply {
                profileQuestionAnswersResponse = Response.success(
                    TestDtos.profileQuestionAnswers(
                        listOf(
                            TestDtos.profileQuestionAnswer(
                                "PERFECT_SUNDAY_001",
                                selectedPosition = 2,
                            ),
                            TestDtos.profileQuestionAnswer(
                                "LIFE_SOUNDTRACK_001",
                                selectedPosition = 1,
                            ),
                        ),
                    ),
                )
            }

            val state = loadedState(
                destination = ProfileQuestionDestination.Selection,
                answers = listOf(
                    TestDtos.profileQuestionAnswer(
                        "PERFECT_SUNDAY_001",
                    ).toDomain(),
                    TestDtos.profileQuestionAnswer(
                        "LIFE_SOUNDTRACK_001",
                    ).toDomain(),
                ),
                selectionDraft = listOf(
                    "LIFE_SOUNDTRACK_001",
                    "PERFECT_SUNDAY_001",
                ),
            )

            val harness = harness(
                api = api,
                initialState = ready(profileQuestions = state),
            )

            harness.handler.saveSelection()
            advanceUntilIdle()

            val result = harness.ready().profileQuestions

            assertEquals(
                listOf(
                    "LIFE_SOUNDTRACK_001",
                    "PERFECT_SUNDAY_001",
                ),
                api.replaceProfileQuestionSelectionsBody?.questionIds,
            )

            assertEquals(
                listOf(
                    "LIFE_SOUNDTRACK_001" to 1,
                    "PERFECT_SUNDAY_001" to 2,
                ),
                result.answers
                    .sortedBy { it.selectedPosition }
                    .map { it.questionId to it.selectedPosition },
            )

            assertEquals(
                listOf(
                    "LIFE_SOUNDTRACK_001",
                    "PERFECT_SUNDAY_001",
                ),
                result.selectionDraftQuestionIds,
            )

            assertEquals(
                ProfileQuestionDestination.Overview,
                result.destination,
            )
            assertNull(result.mutation)
            assertNull(result.mutationError)

            assertEquals(
                ProfileQuestionDestination.Overview,
                result.feedback?.destination,
            )
            assertEquals(
                "Selección guardada",
                result.feedback?.message,
            )
            assertNull(result.feedback?.questionId)
        }

    @Test
    fun `stale out of order mutation completion cannot overwrite newer open mutation`() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeUpsertMyProfileQuestionAnswerResponse = {
                if (calls.count { it == "upsertMyProfileQuestionAnswer" } == 1) firstGate.await() else secondGate.await()
            }
        }
        val harness = harness(api = api, initialState = ready(profileQuestions = loadedState()))
        harness.handler.saveAnswer("PERFECT_SUNDAY_001", "Primera")
        runCurrent()

        harness.state.value = harness.ready().copy(profileQuestions = harness.ready().profileQuestions.copy(mutation = null))
        api.profileQuestionAnswersResponse = Response.success(
            TestDtos.profileQuestionAnswers(listOf(TestDtos.profileQuestionAnswer(answer = "Segunda"))),
        )
        harness.handler.saveAnswer("PERFECT_SUNDAY_001", "Segunda")
        runCurrent()
        secondGate.complete(Unit)
        advanceUntilIdle()
        api.profileQuestionAnswersResponse = Response.success(
            TestDtos.profileQuestionAnswers(listOf(TestDtos.profileQuestionAnswer(answer = "Primera"))),
        )
        firstGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Segunda"), harness.ready().profileQuestions.answers.map { it.answer })
    }

    @Test
    fun `navigation during mutation closes surface but late completion updates authoritative answers`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeUpsertMyProfileQuestionAnswerResponse = { gate.await() }
            profileQuestionAnswersResponse = Response.success(
                TestDtos.profileQuestionAnswers(listOf(TestDtos.profileQuestionAnswer(answer = "Guardada"))),
            )
        }
        val harness = harness(api = api, initialState = ready(profileQuestions = loadedState()))

        harness.handler.saveAnswer("PERFECT_SUNDAY_001", "Guardada")
        runCurrent()
        harness.handler.navigateBack()
        harness.handler.navigateBack()
        harness.handler.navigateBack()
        assertFalse(harness.ready().profileQuestions.open)
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(harness.ready().profileQuestions.open)
        assertEquals(listOf("Guardada"), harness.ready().profileQuestions.answers.map { it.answer })
    }

    @Test
    fun `ordinary errors clear on navigation while legal and terminal auth are preserved`() = runTest {
        val harness = harness(initialState = ready(profileQuestions = loadedState(mutationError = ApiError.Unexpected("x"))))
        harness.handler.openQuestions()
        assertNull(harness.ready().profileQuestions.mutationError)

        val legal = ApiError.Backend(409, "LEGAL_ACTION_REQUIRED", "legal", "legal")
        val legalHarness = harness(initialState = ready(profileQuestions = loadedState(mutationError = legal)))
        legalHarness.handler.openQuestions()
        assertEquals(legal, legalHarness.ready().profileQuestions.mutationError)

        val terminal = ApiError.Auth(AuthFailureReason.NOT_SIGNED_IN, "not signed in")
        val terminalHarness = harness(initialState = ready(profileQuestions = loadedState(mutationError = terminal)))
        terminalHarness.handler.openQuestions()
        assertEquals(terminal, terminalHarness.ready().profileQuestions.mutationError)
    }

    @Test
    fun `legal and terminal auth errors remain observable after mutation failure`() = runTest {
        val legalHarness = harness(
            api = FakeRealsApi().apply {
                profileQuestionAnswersResponse = backendErrorResponse(409, "LEGAL_ACTION_REQUIRED")
            },
            initialState = ready(profileQuestions = loadedState()),
        )
        legalHarness.handler.saveAnswer("PERFECT_SUNDAY_001", "Respuesta")
        advanceUntilIdle()
        assertTrue(legalHarness.ready().profileQuestions.mutationError is ApiError.Backend)

        val terminalHarness = harness(
            api = FakeRealsApi(),
            tokenProvider = FakeAuthTokenProvider().apply { failMissingUser() },
            initialState = ready(profileQuestions = loadedState()),
        )
        terminalHarness.handler.saveAnswer("PERFECT_SUNDAY_001", "Respuesta")
        advanceUntilIdle()
        assertTrue(terminalHarness.ready().profileQuestions.mutationError is ApiError.Auth)
    }

    @Test
    fun `completion after internal navigation installs answers without unrelated feedback`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeUpsertMyProfileQuestionAnswerResponse = { gate.await() }
            profileQuestionAnswersResponse = Response.success(
                TestDtos.profileQuestionAnswers(
                    listOf(TestDtos.profileQuestionAnswer(answer = "Guardada")),
                ),
            )
        }
        val harness = harness(
            api = api,
            initialState = ready(profileQuestions = loadedState()),
        )

        harness.handler.saveAnswer("PERFECT_SUNDAY_001", "Guardada")
        runCurrent()

        harness.handler.navigateBack()

        assertEquals(
            ProfileQuestionDestination.Questions,
            harness.ready().profileQuestions.destination,
        )

        gate.complete(Unit)
        advanceUntilIdle()

        val state = harness.ready().profileQuestions
        assertEquals(listOf("Guardada"), state.answers.map { it.answer })
        assertEquals(ProfileQuestionDestination.Questions, state.destination)
        assertNull(state.feedback)
        assertNull(state.mutationError)
    }

    @Test
    fun `ordinary mutation error is not shown after internal navigation`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeUpsertMyProfileQuestionAnswerResponse = { gate.await() }
            profileQuestionAnswersResponse = backendErrorResponse(
                statusCode = 400,
                code = "INVALID_PROFILE_QUESTION_ANSWER",
            )
        }
        val harness = harness(
            api = api,
            initialState = ready(profileQuestions = loadedState()),
        )

        harness.handler.saveAnswer("PERFECT_SUNDAY_001", "Respuesta")
        runCurrent()
        harness.handler.navigateBack()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            ProfileQuestionDestination.Questions,
            harness.ready().profileQuestions.destination,
        )
        assertNull(harness.ready().profileQuestions.mutationError)
    }

    @Test
    fun `failed selection replacement remains in Selection and preserves draft`() = runTest {
        val api = FakeRealsApi().apply {
            profileQuestionAnswersResponse = backendErrorResponse(
                statusCode = 500,
                code = "SERVER_ERROR",
            )
        }

        val confirmedAnswers = listOf(
            TestDtos.profileQuestionAnswer(
                "PERFECT_SUNDAY_001",
                selectedPosition = 1,
            ).toDomain(),
            TestDtos.profileQuestionAnswer(
                "LIFE_SOUNDTRACK_001",
                selectedPosition = 2,
            ).toDomain(),
        )

        val draft = listOf(
            "LIFE_SOUNDTRACK_001",
            "PERFECT_SUNDAY_001",
        )

        val harness = harness(
            api = api,
            initialState = ready(
                profileQuestions = loadedState(
                    destination = ProfileQuestionDestination.Selection,
                    answers = confirmedAnswers,
                    selectionDraft = draft,
                ),
            ),
        )

        harness.handler.saveSelection()
        advanceUntilIdle()

        val result = harness.ready().profileQuestions

        assertEquals(
            draft,
            api.replaceProfileQuestionSelectionsBody?.questionIds,
        )
        assertEquals(
            ProfileQuestionDestination.Selection,
            result.destination,
        )
        assertEquals(
            confirmedAnswers,
            result.answers,
        )
        assertEquals(
            draft,
            result.selectionDraftQuestionIds,
        )
        assertNull(result.mutation)
        assertTrue(result.mutationError is ApiError.Backend)
        assertNull(result.feedback)
    }

    @Test
    fun `late selection success preserves destination changed while request is active`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()

        val api = FakeRealsApi().apply {
            beforeReplaceMyProfileQuestionSelectionsResponse = {
                requestStarted.complete(Unit)
                releaseResponse.await()
            }

            profileQuestionAnswersResponse = Response.success(
                TestDtos.profileQuestionAnswers(
                    listOf(
                        TestDtos.profileQuestionAnswer(
                            "PERFECT_SUNDAY_001",
                            selectedPosition = 2,
                        ),
                        TestDtos.profileQuestionAnswer(
                            "LIFE_SOUNDTRACK_001",
                            selectedPosition = 1,
                        ),
                    ),
                ),
            )
        }

        val harness = harness(
            api = api,
            initialState = ready(
                profileQuestions = loadedState(
                    destination = ProfileQuestionDestination.Selection,
                    answers = listOf(
                        TestDtos.profileQuestionAnswer(
                            "PERFECT_SUNDAY_001",
                            selectedPosition = 1,
                        ).toDomain(),
                        TestDtos.profileQuestionAnswer(
                            "LIFE_SOUNDTRACK_001",
                            selectedPosition = 2,
                        ).toDomain(),
                    ),
                    selectionDraft = listOf(
                        "LIFE_SOUNDTRACK_001",
                        "PERFECT_SUNDAY_001",
                    ),
                ),
            ),
        )

        harness.handler.saveSelection()
        runCurrent()
        requestStarted.await()

        assertEquals(
            ProfileQuestionMutationKind.Selection,
            harness.ready().profileQuestions.mutation?.kind,
        )

        /*
         * Simulate the user leaving Selection before the response arrives.
         * navigateBack() moves Selection -> Overview.
         */
        harness.handler.navigateBack()

        assertEquals(
            ProfileQuestionDestination.Overview,
            harness.ready().profileQuestions.destination,
        )

        /*
         * Move somewhere other than the automatic success target so the test
         * can detect an incorrect forced navigation back to Overview.
         */
        harness.handler.openQuestions()

        assertEquals(
            ProfileQuestionDestination.Questions,
            harness.ready().profileQuestions.destination,
        )

        releaseResponse.complete(Unit)
        advanceUntilIdle()

        val result = harness.ready().profileQuestions

        assertEquals(
            ProfileQuestionDestination.Questions,
            result.destination,
        )
        assertEquals(
            listOf(
                "LIFE_SOUNDTRACK_001",
                "PERFECT_SUNDAY_001",
            ),
            result.selectionDraftQuestionIds,
        )
        assertEquals(
            listOf(
                "LIFE_SOUNDTRACK_001" to 1,
                "PERFECT_SUNDAY_001" to 2,
            ),
            result.answers
                .sortedBy { it.selectedPosition }
                .map { it.questionId to it.selectedPosition },
        )
        assertNull(result.mutation)
        assertNull(result.mutationError)
        assertNull(result.feedback)
    }

    private fun loadedState(
        destination: ProfileQuestionDestination = ProfileQuestionDestination.Editor("PERFECT_SUNDAY_001"),
        answers: List<ProfileQuestionAnswer> = listOf(
            TestDtos.profileQuestionAnswer("PERFECT_SUNDAY_001", selectedPosition = 1).toDomain(),
            TestDtos.profileQuestionAnswer("LIFE_SOUNDTRACK_001", selectedPosition = 2).toDomain(),
        ),
        mutationError: ApiError? = null,
        selectionDraft: List<String> = answers
            .filter { it.selectedPosition != null }
            .sortedBy { it.selectedPosition }
            .map { it.questionId },
    ): ProfileQuestionUiState = ProfileQuestionUiState(
        open = true,
        profileId = "profile-1",
        destination = destination,
        catalog = TestDtos.profileQuestionCatalog().toDomain(),
        answers = answers,
        mutationError = mutationError,
        selectionDraftQuestionIds = selectionDraft,
    )

    private fun TestScope.harness(
        api: FakeRealsApi = FakeRealsApi(),
        tokenProvider: FakeAuthTokenProvider = FakeAuthTokenProvider(),
        initialState: RealsRootUiState.Ready = ready(),
    ): Harness {
        val repository = ProfileQuestionRepository(api, tokenProvider, testApiExecutor())
        val state = MutableStateFlow<RealsRootUiState>(initialState)
        val handler = ProfileQuestionOperationHandler(
            uiState = state,
            dependencies = ProfileQuestionFeatureDependencies(
                getCatalog = GetProfileQuestionCatalogUseCase(repository),
                getMyAnswers = GetMyProfileQuestionAnswersUseCase(repository),
                upsertAnswer = UpsertMyProfileQuestionAnswerUseCase(repository),
                deleteAnswer = DeleteMyProfileQuestionAnswerUseCase(repository),
                replaceSelections = ReplaceMyProfileQuestionSelectionsUseCase(repository),
            ),
            scope = this,
        )
        return Harness(api, state, handler)
    }

    private fun ready(
        profileId: String = "profile-1",
        profileStatus: String = "ACTIVE",
        profileQuestions: ProfileQuestionUiState = ProfileQuestionUiState(),
    ): RealsRootUiState.Ready = RealsRootUiState.Ready(
        session = TestDomain.session().copy(
            profileSnapshot = ProfileSnapshot.Found(
                TestDtos.profile(status = profileStatus).copy(id = profileId).toDomain(),
            ),
        ),
        profileQuestions = profileQuestions,
    )

    private data class Harness(
        val api: FakeRealsApi,
        val state: MutableStateFlow<RealsRootUiState>,
        val handler: ProfileQuestionOperationHandler,
    ) {
        fun ready(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }
}
