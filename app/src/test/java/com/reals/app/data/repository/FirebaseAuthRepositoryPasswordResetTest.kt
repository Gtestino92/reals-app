package com.reals.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import android.content.ContextWrapper

class FirebaseAuthRepositoryPasswordResetTest {
    @Test
    fun `send email verification returns not signed in when Firebase user is missing`() = kotlinx.coroutines.test.runTest {
        val result = unconfiguredRepository().sendEmailVerificationEmail()

        assertEquals(EmailVerificationSendResult.NotSignedIn, result)
    }

    @Test
    fun `check email verification returns not signed in when Firebase user is missing`() = kotlinx.coroutines.test.runTest {
        val result = unconfiguredRepository().reloadAndRefreshEmailVerification()

        assertEquals(EmailVerificationCheckResult.NotSignedIn, result)
    }

    @Test
    fun `valid local email passes simple validation`() {
        assertEquals(true, isLocallyValidEmail("alex@example.com"))
    }

    @Test
    fun `invalid local email fails simple validation`() {
        assertEquals(false, isLocallyValidEmail(""))
        assertEquals(false, isLocallyValidEmail("alex"))
        assertEquals(false, isLocallyValidEmail("alex@example"))
        assertEquals(false, isLocallyValidEmail("alex @example.com"))
    }

    @Test
    fun `invalid email Firebase error code maps to invalid email format`() {
        assertEquals(
            PasswordResetResult.InvalidEmailFormat,
            passwordResetResultForFirebaseErrorCode("ERROR_INVALID_EMAIL"),
        )
    }

    @Test
    fun `invalid user Firebase error code maps to generic handled success`() {
        assertEquals(
            PasswordResetResult.SentOrHandledGenerically,
            passwordResetResultForFirebaseErrorCode("ERROR_USER_NOT_FOUND"),
        )
    }

    @Test
    fun `unexpected Firebase failure maps to silent failure`() {
        val result = IllegalStateException("network or configuration failure").toPasswordResetResult()

        assertEquals(PasswordResetResult.SilentFailure, result)
    }

    private fun unconfiguredRepository(): FirebaseAuthRepository =
        object : FirebaseAuthRepository(ContextWrapper(null)) {
            override fun isConfigured(): Boolean = false
        }
}
