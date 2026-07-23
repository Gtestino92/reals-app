package com.reals.app.core.network

import com.reals.app.core.appcheck.AppCheckFailureReason
import com.reals.app.core.appcheck.AppCheckTokenAcquisitionException
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ApiExecutorTest {
    private val executor = testApiExecutor()

    @Test
    fun `success response maps body`() = runBlocking {
        val result = executor.execute { Response.success(PingResponseDto("ok")) }

        assertEquals("ok", (result as ApiResult.Success).value.status)
    }

    @Test
    fun `400 validation error maps backend error`() = runBlocking {
        val result = executor.execute { backendErrorResponse<PingResponseDto>(400, "VALIDATION_ERROR", "bad input") }

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(400, error.statusCode)
        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals("bad input", error.message)
    }

    @Test
    fun `401 maps backend error so repositories can refresh token`() = runBlocking {
        val result = executor.execute { backendErrorResponse<PingResponseDto>(401, "UNAUTHORIZED", "expired") }

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(401, error.statusCode)
        assertEquals("UNAUTHORIZED", error.code)
    }

    @Test
    fun `403 maps backend access denied`() = runBlocking {
        val result = executor.execute { backendErrorResponse<PingResponseDto>(403, "ACCESS_DENIED", "forbidden") }

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(403, error.statusCode)
        assertEquals("ACCESS_DENIED", error.code)
    }

    @Test
    fun `409 maps backend domain conflict`() = runBlocking {
        val result = executor.execute { backendErrorResponse<PingResponseDto>(409, "DOMAIN_CONFLICT", "already done") }

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(409, error.statusCode)
        assertEquals("DOMAIN_CONFLICT", error.code)
        assertEquals("already done", error.toUserMessage(ErrorContext.VisualReview))
    }

    @Test
    fun `network exception maps to network error`() = runBlocking {
        val result = executor.execute<PingResponseDto> { throw IOException("offline") }

        val error = (result as ApiResult.Failure).error as ApiError.Network
        assertEquals("offline", error.message)
    }

    @Test
    fun `app check token failure maps to app check error before network error`() = runBlocking {
        val result = executor.execute<PingResponseDto> {
            throw AppCheckTokenAcquisitionException(
                reason = AppCheckFailureReason.TOKEN_UNAVAILABLE,
                message = "temporary app check failure",
            )
        }

        val error = (result as ApiResult.Failure).error as ApiError.AppCheck
        assertEquals(AppCheckFailureReason.TOKEN_UNAVAILABLE, error.reason)
        assertEquals(
            "No pudimos verificar éstainstalación. Revisá tu conexión e intentá nuevamente.",
            error.toUserMessage(),
        )
    }

    @Test
    fun `unexpected exception maps to unexpected error`() = runBlocking {
        val result = executor.execute<PingResponseDto> { error("boom") }

        val error = (result as ApiResult.Failure).error as ApiError.Unexpected
        assertEquals("boom", error.message)
    }

    @Test
    fun `malformed error body still gives stable backend error`() = runBlocking {
        val body = "not-json".toResponseBody("text/plain".toMediaType())
        val result = executor.execute { Response.error<PingResponseDto>(500, body) }

        val error = (result as ApiResult.Failure).error as ApiError.Backend
        assertEquals(500, error.statusCode)
        assertEquals(null, error.code)
        assertEquals("not-json", error.message)
    }

    @Test
    fun `auth helper messages cover account deleted and token failures`() {
        assertTrue(ApiError.Backend(409, "ACCOUNT_DELETED", "ACCOUNT_DELETED", "").isAccountDeleted())
        assertTrue(
            ApiError.Backend(
                409,
                "ACCOUNT_DELETION_FINALIZED",
                "ACCOUNT_DELETION_FINALIZED",
                "",
            ).isAccountDeletionFinalized()
        )
        assertEquals(
            "Tu sesión necesita renovarse. Volvé a iniciar sesión.",
            ApiError.Auth(AuthFailureReason.TOKEN_MISSING, "missing").toUserMessage(),
        )
    }
}
