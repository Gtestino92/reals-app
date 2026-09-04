package com.reals.app.data.repository

import com.reals.app.core.network.ApiResult
import com.reals.app.data.dto.BanAppealResponseDto
import com.reals.app.domain.model.PermanentBanAppealStatus
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class BanAppealRepositoryTest {
    private val api = FakeRealsApi()
    private val tokenProvider = FakeAuthTokenProvider()
    private val repository = BanAppealRepository(api, tokenProvider, testApiExecutor())

    @Test
    fun `get appeal uses authenticated repository behavior and maps response`() = runBlocking {
        api.banAppealResponse = Response.success(
            BanAppealResponseDto(
                status = "PENDING",
                banActive = true,
                appealedAt = "2026-09-01T10:00:00Z",
            )
        )

        val appeal = repository.getMyBanAppeal().successValue()

        assertEquals(listOf("getMyBanAppeal"), api.calls)
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(PermanentBanAppealStatus.Pending, appeal.status)
        assertEquals(true, appeal.banActive)
    }

    @Test
    fun `submit sends statement and accepts 201 without body`() = runBlocking {
        api.submitBanAppealResponse = Response.success(201, Unit)

        repository.submitMyBanAppeal("Necesito revisión").successValue()

        assertEquals(listOf("submitMyBanAppeal"), api.calls)
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals("Necesito revisión", api.banAppealBody?.statement)
    }

    @Test
    fun `invalid token refresh preserves authenticated behavior`() = runBlocking {
        api.banAppealResponse = backendErrorResponse(401, "INVALID_TOKEN")

        val result = repository.getMyBanAppeal()

        assertTrue(result is ApiResult.Failure)
        assertEquals(listOf("getMyBanAppeal", "getMyBanAppeal"), api.calls)
        assertEquals(listOf(false, true), tokenProvider.calls)
    }
}
