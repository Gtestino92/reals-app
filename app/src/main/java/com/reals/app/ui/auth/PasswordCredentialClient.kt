package com.reals.app.ui.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.reals.app.data.repository.isLocallyValidEmail
import kotlinx.coroutines.CancellationException

sealed interface PasswordCredentialResult {
    class Success(
        val email: String,
        val password: String,
    ) : PasswordCredentialResult {
        override fun equals(other: Any?): Boolean {
            return other is Success &&
                email == other.email &&
                password == other.password
        }

        override fun hashCode(): Int = 31 * email.hashCode() + password.hashCode()

        override fun toString(): String = "Success(email=$email)"
    }

    data object Cancelled : PasswordCredentialResult
    data object NotFound : PasswordCredentialResult
    data object Unsupported : PasswordCredentialResult
    data object Failure : PasswordCredentialResult
}

sealed interface PasswordCredentialSaveResult {
    data object Saved : PasswordCredentialSaveResult
    data object Cancelled : PasswordCredentialSaveResult
    data object Failure : PasswordCredentialSaveResult
}

enum class LoginCredentialOrigin {
    ManualEmailPassword,
    SavedPasswordCredential,
    Google,
}

class PasswordCredentialClient(context: Context) {
    private val appContext = context.applicationContext

    suspend fun getPasswordCredential(
        activity: Activity?,
        email: String,
    ): PasswordCredentialResult {
        val credentialActivity = activity ?: return PasswordCredentialResult.Failure
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetPasswordOption(
                    allowedUserIds = allowedPasswordCredentialUserIds(email),
                )
            )
            .build()

        return try {
            val response = CredentialManager.create(appContext).getCredential(
                context = credentialActivity,
                request = request,
            )
            extractPasswordCredential(response.credential)
        } catch (exception: GetCredentialCancellationException) {
            PasswordCredentialResult.Cancelled
        } catch (exception: NoCredentialException) {
            PasswordCredentialResult.NotFound
        } catch (exception: GetCredentialUnsupportedException) {
            PasswordCredentialResult.Unsupported
        } catch (exception: GetCredentialException) {
            PasswordCredentialResult.Failure
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            PasswordCredentialResult.Failure
        }
    }

    suspend fun savePasswordCredential(
        activity: Activity?,
        email: String,
        password: String,
    ): PasswordCredentialSaveResult {
        val credentialActivity = activity ?: return PasswordCredentialSaveResult.Failure
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            return PasswordCredentialSaveResult.Failure
        }

        return try {
            CredentialManager.create(appContext).createCredential(
                context = credentialActivity,
                request = CreatePasswordRequest(
                    id = cleanEmail,
                    password = password,
                ),
            )
            PasswordCredentialSaveResult.Saved
        } catch (exception: CreateCredentialCancellationException) {
            PasswordCredentialSaveResult.Cancelled
        } catch (exception: CreateCredentialException) {
            PasswordCredentialSaveResult.Failure
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            PasswordCredentialSaveResult.Failure
        }
    }
}

internal fun extractPasswordCredential(credential: Credential): PasswordCredentialResult {
    val passwordCredential = credential as? PasswordCredential
        ?: return PasswordCredentialResult.Unsupported
    return passwordCredentialResult(
        email = passwordCredential.id,
        password = passwordCredential.password,
    )
}

internal fun passwordCredentialResult(
    email: String,
    password: String,
): PasswordCredentialResult {
    val cleanEmail = email.trim()
    if (cleanEmail.isBlank() || password.isBlank()) {
        return PasswordCredentialResult.Unsupported
    }
    return PasswordCredentialResult.Success(
        email = cleanEmail,
        password = password,
    )
}

internal fun allowedPasswordCredentialUserIds(email: String): Set<String> {
    val cleanEmail = email.trim()
    return if (isLocallyValidEmail(cleanEmail)) setOf(cleanEmail) else emptySet()
}

internal fun shouldOfferPasswordCredentialSave(origin: LoginCredentialOrigin): Boolean =
    origin == LoginCredentialOrigin.ManualEmailPassword
