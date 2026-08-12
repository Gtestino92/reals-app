package com.reals.app.data.repository

import android.content.ContextWrapper
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `invalid Firebase user maps email verification operations to not signed in`() {
        val failure = FirebaseAuthInvalidUserException(
            "ERROR_USER_DISABLED",
            "Firebase user is disabled",
        )

        assertEquals(EmailVerificationSendResult.NotSignedIn, failure.toEmailVerificationSendResult())
        assertEquals(EmailVerificationCheckResult.NotSignedIn, failure.toEmailVerificationCheckResult())
    }

    @Test
    fun `generic email verification failure remains non terminal`() {
        val failure = IllegalStateException("temporary Firebase failure")

        assertEquals(EmailVerificationSendResult.Failure, failure.toEmailVerificationSendResult())
        assertEquals(EmailVerificationCheckResult.Failure, failure.toEmailVerificationCheckResult())
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
    fun `change password returns not signed in when Firebase user is missing`() = kotlinx.coroutines.test.runTest {
        val result = unconfiguredRepository().changePassword("current-password", "new-password")

        assertEquals(ChangePasswordResult.NotSignedIn, result)
    }

    @Test
    fun `current user without Firebase config cannot change password`() {
        assertEquals(false, unconfiguredRepository().currentUserHasPasswordProvider())
    }

    @Test
    fun `password provider enables password change capability`() {
        assertEquals(true, providerIdsHavePasswordProvider(listOf(EmailAuthProvider.PROVIDER_ID)))
    }

    @Test
    fun `google only provider disables password change capability`() {
        assertEquals(false, providerIdsHavePasswordProvider(listOf("google.com")))
    }

    @Test
    fun `linked password provider enables password change capability`() {
        assertEquals(true, providerIdsHavePasswordProvider(listOf("google.com", EmailAuthProvider.PROVIDER_ID)))
    }

    @Test
    fun `change password reauthentication maps wrong current password`() {
        val result = changePasswordReauthenticationResultForFirebaseErrorCode("ERROR_INVALID_CREDENTIAL")

        assertEquals(ChangePasswordResult.WrongCurrentPassword, result)
    }

    @Test
    fun `change password update maps weak new password`() {
        val result = changePasswordUpdateResultForFirebaseErrorCode("ERROR_WEAK_PASSWORD")

        assertEquals(ChangePasswordResult.WeakNewPassword, result)
    }

    @Test
    fun `change password update maps invalid new password`() {
        val result = changePasswordUpdateResultForFirebaseErrorCode("ERROR_INVALID_PASSWORD")

        assertEquals(ChangePasswordResult.InvalidNewPassword, result)
    }

    private fun unconfiguredRepository(): FirebaseAuthRepository =
        object : FirebaseAuthRepository(ContextWrapper(null)) {
            override fun isConfigured(): Boolean = false
        }
}
