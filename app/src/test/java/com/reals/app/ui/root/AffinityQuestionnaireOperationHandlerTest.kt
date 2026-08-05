package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
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
    fun `selection enters one mutation ignores concurrent second and success replaces complete answers`() = runTest {
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
                        TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", answerCode = "LOW"),
                        TestDtos.affinityAnswer("PLANS_WEEKEND_001", answerCode = "QUIET"),
                    )
                )
            )
        }
        val harness = loadedHarness(api)

        harness.handler.selectAnswer("MUSIC_DISCOVERY_001", "LOW")
        runCurrent()
        requestStarted.await()
        harness.handler.selectAnswer("PLANS_WEEKEND_001", "QUIET")

        assertEquals("MUSIC_DISCOVERY_001", harness.ready().affinityQuestionnaire.mutation?.questionId)
        assertEquals(1, api.calls.count { it == "patchMyAffinityAnswers" })

        releaseResponse.complete(Unit)
        advanceUntilIdle()
        assertNull(harness.ready().affinityQuestionnaire.mutation)
        assertEquals(
            listOf("MUSIC_DISCOVERY_001", "PLANS_WEEKEND_001"),
            harness.ready().affinityQuestionnaire.answers.map { it.questionId },
        )
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
        assertEquals(listOf("MUSIC_DISCOVERY_001"), mutationHarness.ready().affinityQuestionnaire.answers.map { it.questionId })
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
