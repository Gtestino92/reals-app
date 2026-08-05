package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isLegalActionRequired
import com.reals.app.core.network.isTerminalAuthFailure
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.failureError
import com.reals.app.testutil.testApiExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AffinityQuestionRepositoryTest {
    @Test
    fun `catalog GET uses expected endpoint and authorization`() = kotlinx.coroutines.test.runTest {
        val harness = harness()

        val result = harness.repository.getCatalog()

        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("getAffinityQuestionCatalog"), harness.api.calls)
        assertEquals("Bearer test-token", harness.api.lastAuthorization)
    }

    @Test
    fun `answers GET uses expected endpoint and authorization`() = kotlinx.coroutines.test.runTest {
        val harness = harness()

        val result = harness.repository.getMyAnswers()

        assertEquals(listOf("getMyAffinityAnswers"), harness.api.calls)
        assertEquals("Bearer test-token", harness.api.lastAuthorization)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), (result as ApiResult.Success).value.map { it.questionId })
    }

    @Test
    fun `PATCH sends exactly one expected answer and maps complete response`() = kotlinx.coroutines.test.runTest {
        val api = FakeRealsApi().apply {
            affinityAnswersResponse = Response.success(
                TestDtos.affinityAnswers(
                    listOf(
                        TestDtos.affinityAnswer("MUSIC_DISCOVERY_001", answerCode = "LOW"),
                        TestDtos.affinityAnswer("PLANS_WEEKEND_001", answerCode = "QUIET"),
                    )
                )
            )
        }
        val harness = harness(api)

        val result = harness.repository.patchAnswer(" MUSIC_DISCOVERY_001 ", " LOW ")

        assertEquals(listOf("patchMyAffinityAnswers"), api.calls)
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(1, api.patchAffinityAnswersBody?.answers?.size)
        assertEquals("MUSIC_DISCOVERY_001", api.patchAffinityAnswersBody?.answers?.single()?.questionId)
        assertEquals("LOW", api.patchAffinityAnswersBody?.answers?.single()?.answerCode)
        assertEquals(
            listOf("MUSIC_DISCOVERY_001", "PLANS_WEEKEND_001"),
            (result as ApiResult.Success).value.map { it.questionId },
        )
    }

    @Test
    fun `DELETE uses encoded path question id and maps complete response`() = kotlinx.coroutines.test.runTest {
        val harness = harness()

        val result = harness.repository.deleteAnswer("QUESTION/1")

        assertEquals(listOf("deleteMyAffinityAnswer"), harness.api.calls)
        assertEquals("QUESTION/1", harness.api.lastPathId)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), (result as ApiResult.Success).value.map { it.questionId })
    }

    @Test
    fun `blank inputs are rejected without network calls`() = kotlinx.coroutines.test.runTest {
        val harness = harness()

        assertTrue(harness.repository.patchAnswer("", "LOW").failureError() is ApiError.Unexpected)
        assertTrue(harness.repository.patchAnswer("Q1", " ").failureError() is ApiError.Unexpected)
        assertTrue(harness.repository.deleteAnswer(" ").failureError() is ApiError.Unexpected)
        assertTrue(harness.api.calls.isEmpty())
    }

    @Test
    fun `backend failures propagate and remain classifiable`() = kotlinx.coroutines.test.runTest {
        val legalApi = FakeRealsApi().apply {
            affinityAnswersResponse = backendErrorResponse(409, "LEGAL_ACTION_REQUIRED")
        }
        val terminalToken = FakeAuthTokenProvider().apply { failMissingUser() }

        val legalError = AffinityQuestionRepository(
            legalApi,
            FakeAuthTokenProvider(),
            testApiExecutor(),
        ).patchAnswer("Q1", "A1").failureError()
        val terminalError = AffinityQuestionRepository(
            FakeRealsApi(),
            terminalToken,
            testApiExecutor(),
        ).getMyAnswers().failureError()

        assertTrue(legalError.isLegalActionRequired())
        assertTrue(terminalError.isTerminalAuthFailure())
    }

    private fun harness(
        api: FakeRealsApi = FakeRealsApi(),
        tokenProvider: FakeAuthTokenProvider = FakeAuthTokenProvider(),
    ) = Harness(
        api = api,
        repository = AffinityQuestionRepository(api, tokenProvider, testApiExecutor()),
    )

    private data class Harness(
        val api: FakeRealsApi,
        val repository: AffinityQuestionRepository,
    )
}
