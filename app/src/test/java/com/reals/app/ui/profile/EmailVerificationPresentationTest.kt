package com.reals.app.ui.profile

import com.reals.app.core.network.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailVerificationPresentationTest {
    @Test
    fun `does not show remediation when local verification is unknown and no explicit requirement exists`() {
        assertFalse(
            shouldShowEmailVerificationActions(
                emailVerificationLocallyVerified = false,
                emailVerificationRequired = false,
                activationError = null,
            ),
        )
    }

    @Test
    fun `does not show remediation when local verification is confirmed`() {
        assertFalse(
            shouldShowEmailVerificationActions(
                emailVerificationLocallyVerified = true,
                emailVerificationRequired = false,
                activationError = null,
            ),
        )
    }

    @Test
    fun `shows remediation when verification is explicitly required`() {
        assertTrue(
            shouldShowEmailVerificationActions(
                emailVerificationLocallyVerified = false,
                emailVerificationRequired = true,
                activationError = null,
            ),
        )
    }

    @Test
    fun `shows remediation when activation failed because email is not verified`() {
        assertTrue(
            shouldShowEmailVerificationActions(
                emailVerificationLocallyVerified = false,
                emailVerificationRequired = false,
                activationError = backendError("EMAIL_NOT_VERIFIED"),
            ),
        )
    }

    @Test
    fun `does not show remediation when local verification is confirmed even with stale explicit state`() {
        assertFalse(
            shouldShowEmailVerificationActions(
                emailVerificationLocallyVerified = true,
                emailVerificationRequired = true,
                activationError = backendError("EMAIL_NOT_VERIFIED"),
            ),
        )
    }

    private fun backendError(code: String): ApiError.Backend = ApiError.Backend(
        statusCode = 409,
        code = code,
        error = "Conflict",
        message = "backend error",
    )
}
