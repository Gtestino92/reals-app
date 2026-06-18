package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.VisualDecision
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.failureError
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class MatchRepositoryTest {
    private val api = FakeRealsApi()
    private val repository = MatchRepository(api, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `getMatch maps response`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_PHASE"))

        val match = repository.getMatch("match-1").successValue()

        assertEquals("getMatch", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(MatchState.VisualPhase, match.state)
    }

    @Test
    fun `getVisualProfile maps myPersonalMessageSubmitted`() = runBlocking {
        api.visualProfileResponse = Response.success(TestDtos.visualProfile(myPersonalMessageSubmitted = true))

        val profile = repository.getVisualProfile("match-1").successValue()

        assertEquals(true, profile.myPersonalMessageSubmitted)
        assertEquals(listOf("photo-1", "photo-2"), profile.photos.map { it.id })
    }

    @Test
    fun `putMyPersonalMessage sends body`() = runBlocking {
        repository.putMyPersonalMessage("match-1", "hola").successValue()

        assertEquals("putMyPersonalMessage", api.calls.single())
        assertEquals("match-1", api.lastPathId)
        assertEquals("hola", api.personalMessageBody?.message)
    }

    @Test
    fun `putMyPersonalMessage conflict maps backend domain conflict`() = runBlocking {
        api.unitResponse = backendErrorResponse(409, "DOMAIN_CONFLICT", "already submitted")

        val error = repository.putMyPersonalMessage("match-1", "hola").failureError()

        assertTrue(error is ApiError.Backend)
        assertEquals("DOMAIN_CONFLICT", (error as ApiError.Backend).code)
    }

    @Test
    fun `getPartnerPersonalMessage maps response`() = runBlocking {
        val message = repository.getPartnerPersonalMessage("match-1").successValue()

        assertEquals("hola", message)
    }

    @Test
    fun `visual and chat decisions send backend body and map state`() = runBlocking {
        api.matchResponse = Response.success(TestDtos.match("VISUAL_APPROVED"))

        assertEquals(
            MatchState.VisualApproved,
            repository.submitVisualDecision("match-1", VisualDecision.Approved).successValue().state,
        )
        assertEquals("APPROVED", api.visualDecisionBody?.decision)

        repository.submitChatDecision("match-1", ChatContinueDecision.Rejected).successValue()
        assertEquals("submitChatDecision", api.calls.last())
        assertEquals("REJECTED", api.chatDecisionBody?.decision)
    }
}
