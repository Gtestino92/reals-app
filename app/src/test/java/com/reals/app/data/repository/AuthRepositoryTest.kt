package com.reals.app.data.repository

import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun `only exact password reset rate limit contract produces feedback`() = runBlocking {
        for ((status, code, expected) in listOf(
            Triple(429, "RATE_LIMIT_EXCEEDED", PasswordResetResult.RateLimited),
            Triple(500, "RATE_LIMIT_EXCEEDED", PasswordResetResult.SilentFailure),
            Triple(429, "UNKNOWN", PasswordResetResult.SilentFailure),
            Triple(429, "ACCOUNT_PROVISIONING_LIMIT_EXCEEDED", PasswordResetResult.SilentFailure),
        )) {
            val api = FakeRealsApi().apply { passwordResetResponse = backendErrorResponse(status, code, "private") }
            assertEquals(expected, AuthRepository(api, testApiExecutor()).requestPasswordReset("alex@example.com"))
            assertNull(api.lastAuthorization)
            assertEquals(listOf("requestPasswordReset"), api.calls)
        }
    }

    @Test
    fun `valid password reset email calls public backend endpoint without bearer token`() = runBlocking {
        val api = FakeRealsApi()
        val repository = AuthRepository(api, testApiExecutor())

        val result = repository.requestPasswordReset(" alex@example.com ")

        assertEquals(PasswordResetResult.SentOrHandledGenerically, result)
        assertEquals(listOf("requestPasswordReset"), api.calls)
        assertEquals("alex@example.com", api.passwordResetBody?.email)
        assertNull(api.lastAuthorization)
    }

    @Test
    fun `invalid password reset email does not call backend`() = runBlocking {
        val api = FakeRealsApi()
        val repository = AuthRepository(api, testApiExecutor())

        val result = repository.requestPasswordReset("not-an-email")

        assertEquals(PasswordResetResult.InvalidEmailFormat, result)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `password reset backend failure stays generic and silent`() = runBlocking {
        val api = FakeRealsApi().apply {
            passwordResetResponse = backendErrorResponse(500, "SERVER_ERROR", "server error")
        }
        val repository = AuthRepository(api, testApiExecutor())

        val result = repository.requestPasswordReset("alex@example.com")

        assertEquals(PasswordResetResult.SilentFailure, result)
        assertEquals(listOf("requestPasswordReset"), api.calls)
    }
}
