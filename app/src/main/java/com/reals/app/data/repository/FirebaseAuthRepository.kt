package com.reals.app.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

sealed interface AuthOperationResult {
    data object Success : AuthOperationResult
    data class Failure(val message: String) : AuthOperationResult
}

sealed interface EmailVerificationSendResult {
    data object Sent : EmailVerificationSendResult
    data object AlreadyVerified : EmailVerificationSendResult
    data object NotSignedIn : EmailVerificationSendResult
    data object Failure : EmailVerificationSendResult
}

sealed interface EmailVerificationCheckResult {
    data object Verified : EmailVerificationCheckResult
    data object NotVerified : EmailVerificationCheckResult
    data object NotSignedIn : EmailVerificationCheckResult
    data object Failure : EmailVerificationCheckResult
}

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object NotSignedIn : ChangePasswordResult
    data object PasswordProviderUnavailable : ChangePasswordResult
    data object MissingEmail : ChangePasswordResult
    data object WrongCurrentPassword : ChangePasswordResult
    data object WeakNewPassword : ChangePasswordResult
    data object InvalidNewPassword : ChangePasswordResult
    data object Failure : ChangePasswordResult
}

open class FirebaseAuthRepository(private val context: Context) {
    open fun isConfigured(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    open fun hasSignedInUser(): Boolean = authOrNull()?.currentUser != null

    open fun currentUserEmail(): String? = authOrNull()?.currentUser?.email

    open fun currentUserEmailVerified(): Boolean? =
        authOrNull()?.currentUser?.isEmailVerified

    open fun currentUserHasPasswordProvider(): Boolean {
        val user = authOrNull()?.currentUser ?: return false
        return providerIdsHavePasswordProvider(user.providerData.map { it.providerId })
    }

    open suspend fun signIn(email: String, password: String): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)

        return runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { AuthOperationResult.Failure(it.toSignInMessage()) },
        )
    }

    open suspend fun signUp(email: String, password: String): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)
        return runCatching {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { AuthOperationResult.Failure(it.toSignUpMessage()) },
        )
    }

    open suspend fun signInWithGoogleIdToken(idToken: String): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)

        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { AuthOperationResult.Failure(it.toGoogleSignInMessage()) },
        )
    }

    open suspend fun sendEmailVerificationEmail(): EmailVerificationSendResult {
        val user = authOrNull()?.currentUser
            ?: return EmailVerificationSendResult.NotSignedIn

        return runCatching {
            user.reload().await()
            if (user.isEmailVerified) {
                EmailVerificationSendResult.AlreadyVerified
            } else {
                user.sendEmailVerification().await()
                EmailVerificationSendResult.Sent
            }
        }.getOrElse(Throwable::toEmailVerificationSendResult)
    }

    open suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult {
        val user = authOrNull()?.currentUser
            ?: return EmailVerificationCheckResult.NotSignedIn

        return runCatching {
            user.reload().await()
            user.getIdToken(true).await()
            if (user.isEmailVerified) {
                EmailVerificationCheckResult.Verified
            } else {
                EmailVerificationCheckResult.NotVerified
            }
        }.getOrElse(Throwable::toEmailVerificationCheckResult)
    }

    open suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): ChangePasswordResult {
        val user = authOrNull()?.currentUser
            ?: return ChangePasswordResult.NotSignedIn
        if (!providerIdsHavePasswordProvider(user.providerData.map { it.providerId })) {
            return ChangePasswordResult.PasswordProviderUnavailable
        }
        val email = user.email?.trim()?.takeIf { it.isNotBlank() }
            ?: return ChangePasswordResult.MissingEmail
        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        runCatching {
            user.reauthenticate(credential).await()
        }.getOrElse {
            return it.toChangePasswordReauthenticationResult()
        }

        return runCatching {
            user.updatePassword(newPassword).await()
            user.getIdToken(true).await()
            ChangePasswordResult.Success
        }.getOrElse {
            it.toChangePasswordUpdateResult()
        }
    }

    open fun signOut() {
        authOrNull()?.signOut()
    }

    private fun Throwable.toSignInMessage(): String {
        return when (this) {
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException ->
                "No se pudo iniciar sesión. Revisá el email y la contraseña. Si eliminaste tu cuenta, creá una nueva."

            else ->
                localizedMessage ?: "No se pudo iniciar sesión."
        }
    }

    private fun Throwable.toSignUpMessage(): String {
        return when (this) {
            is FirebaseAuthUserCollisionException ->
                "Ya existe una cuenta con ese email. Iniciá sesión con el método con el que creaste la cuenta."

            is FirebaseAuthWeakPasswordException ->
                "La contraseña es demasiado débil."

            is FirebaseAuthInvalidCredentialsException ->
                "El email no tiene un formato válido."

            else ->
                localizedMessage ?: "No se pudo crear la cuenta."
        }
    }

    private fun Throwable.toGoogleSignInMessage(): String {
        return when (this) {
            is FirebaseAuthUserCollisionException ->
                "Ya existe una cuenta asociada a ese email. Iniciá sesión con el método original."

            else -> "No pudimos iniciar sesión con Google. Intentá nuevamente."
        }
    }

    private fun authOrNull(): FirebaseAuth? {
        if (!isConfigured()) return null
        return Firebase.auth
    }

    companion object {
        const val firebaseMissingMessage =
            "Firebase no está configurado. Registrá com.reals.app y agregá app/google-services.json."
    }
}

internal fun isLocallyValidEmail(email: String): Boolean {
    if (email.isBlank()) return false
    val atIndex = email.indexOf('@')
    val dotIndex = email.lastIndexOf('.')
    return atIndex > 0 &&
        dotIndex > atIndex + 1 &&
        dotIndex < email.lastIndex - 1 &&
        !email.any(Char::isWhitespace)
}

internal fun providerIdsHavePasswordProvider(providerIds: Iterable<String>): Boolean {
    return providerIds.any { it == EmailAuthProvider.PROVIDER_ID }
}

internal fun Throwable.toEmailVerificationSendResult(): EmailVerificationSendResult {
    return if (this is FirebaseAuthInvalidUserException) {
        EmailVerificationSendResult.NotSignedIn
    } else {
        EmailVerificationSendResult.Failure
    }
}

internal fun Throwable.toEmailVerificationCheckResult(): EmailVerificationCheckResult {
    return if (this is FirebaseAuthInvalidUserException) {
        EmailVerificationCheckResult.NotSignedIn
    } else {
        EmailVerificationCheckResult.Failure
    }
}

internal fun Throwable.toChangePasswordReauthenticationResult(): ChangePasswordResult {
    return changePasswordReauthenticationResultForFirebaseErrorCode((this as? FirebaseAuthException)?.errorCode)
}

internal fun changePasswordReauthenticationResultForFirebaseErrorCode(errorCode: String?): ChangePasswordResult {
    return when (errorCode) {
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIAL" -> ChangePasswordResult.WrongCurrentPassword
        "ERROR_USER_DISABLED",
        "ERROR_USER_NOT_FOUND",
        "ERROR_USER_TOKEN_EXPIRED",
        "ERROR_INVALID_USER_TOKEN" -> ChangePasswordResult.NotSignedIn
        else -> ChangePasswordResult.Failure
    }
}

internal fun Throwable.toChangePasswordUpdateResult(): ChangePasswordResult {
    return changePasswordUpdateResultForFirebaseErrorCode((this as? FirebaseAuthException)?.errorCode)
}

internal fun changePasswordUpdateResultForFirebaseErrorCode(errorCode: String?): ChangePasswordResult {
    return when (errorCode) {
        "ERROR_WEAK_PASSWORD" -> ChangePasswordResult.WeakNewPassword
        "ERROR_INVALID_PASSWORD" -> ChangePasswordResult.InvalidNewPassword
        "ERROR_USER_DISABLED",
        "ERROR_USER_NOT_FOUND",
        "ERROR_USER_TOKEN_EXPIRED",
        "ERROR_INVALID_USER_TOKEN" -> ChangePasswordResult.NotSignedIn
        else -> ChangePasswordResult.Failure
    }
}
