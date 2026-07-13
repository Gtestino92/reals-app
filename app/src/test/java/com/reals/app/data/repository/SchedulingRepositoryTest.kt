package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProposalStatus
import com.reals.app.core.network.toUserMessage
import com.reals.app.data.dto.AddProposalRequestDto
import com.reals.app.data.dto.RejectPartnerProposalsRequestDto
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.failureError
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class SchedulingRepositoryTest {
    private val api = FakeRealsApi()
    private val repository = SchedulingRepository(api, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `proposal request dto serializes expected round and proposed date times`() {
        val body = AddProposalRequestDto(
            expectedRoundNumber = 1,
            proposedDateTimes = listOf("2026-07-15T19:30:00-03:00"),
        )

        assertEquals(
            """{"expectedRoundNumber":1,"proposedDateTimes":["2026-07-15T19:30:00-03:00"]}""",
            testJson.encodeToString(body),
        )
    }

    @Test
    fun `reject partner proposals request dto serializes expected round`() {
        val body = RejectPartnerProposalsRequestDto(expectedRoundNumber = 1)

        assertEquals(
            """{"expectedRoundNumber":1}""",
            testJson.encodeToString(body),
        )
    }

    @Test
    fun `get scheduling maps negotiation and proposals`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("PENDING"))
        api.proposalsResponse = Response.success(listOf(TestDtos.proposal("PENDING")))

        val negotiation = repository.getNegotiation("connection-1").successValue()
        val proposals = repository.getProposals("connection-1").successValue()

        assertEquals(NegotiationStatus.Pending, negotiation.status)
        assertEquals(ProposalStatus.Pending, proposals.single().status)
        assertEquals(listOf("getConnectionNegotiation", "getConnectionProposals"), api.calls)
    }

    @Test
    fun `submit proposals sends expected round and slot list`() = runBlocking {
        val slots = listOf("2026-06-18T21:00:00Z", "2026-06-19T21:00:00Z")

        repository.submitProposals(
            connectionId = "connection-1",
            expectedRoundNumber = 3,
            proposedDateTimes = slots,
        ).successValue()

        assertEquals("submitConnectionProposals", api.calls.single())
        assertEquals(3, api.proposalsBody?.expectedRoundNumber)
        assertEquals(slots, api.proposalsBody?.proposedDateTimes)
    }

    @Test
    fun `submit proposals failure surfaces scheduling backend code`() = runBlocking {
        api.proposalsResponse = backendErrorResponse(
            statusCode = 409,
            code = "SCHEDULING_INVALID_PROPOSALS",
            message = "raw backend message",
        )

        val error = repository.submitProposals(
            connectionId = "connection-1",
            expectedRoundNumber = 1,
            proposedDateTimes = listOf("2026-06-18T21:00:00Z"),
        ).failureError() as ApiError.Backend

        assertEquals("SCHEDULING_INVALID_PROPOSALS", error.code)
        assertEquals(BackendErrorCode.SchedulingInvalidProposals, error.backendErrorCode)
        assertEquals(
            "Revisa los horarios elegidos. Deben ser futuros, unicos y estar alineados cada media hora.",
            error.toUserMessage(ErrorContext.Scheduling),
        )
    }

    @Test
    fun `accept proposal maps confirmed negotiation`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("CONFIRMED"))

        val negotiation = repository.acceptProposal("connection-1", "proposal-1").successValue()

        assertEquals("acceptConnectionProposal", api.calls.single())
        assertEquals("connection-1/proposal-1", api.lastPathId)
        assertEquals(NegotiationStatus.Confirmed, negotiation.status)
    }

    @Test
    fun `reject partner proposals sends expected round and maps negotiation`() = runBlocking {
        api.negotiationResponse = Response.success(TestDtos.negotiation("FAILED"))

        val negotiation = repository.rejectPartnerProposals("connection-1", expectedRoundNumber = 3).successValue()

        assertEquals("rejectConnectionPartnerProposals", api.calls.single())
        assertEquals(3, api.rejectPartnerProposalsBody?.expectedRoundNumber)
        assertEquals(NegotiationStatus.Failed, negotiation.status)
    }
}
