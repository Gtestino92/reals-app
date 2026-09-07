package com.reals.app.ui.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import kotlinx.coroutines.CancellationException

sealed interface PasswordCredentialSaveResult {
    data object Saved : PasswordCredentialSaveResult
    data object Cancelled : PasswordCredentialSaveResult
    data object Failure : PasswordCredentialSaveResult
}

class PasswordCredentialClient(context: Context) {
    private val appContext = context.applicationContext

    suspend fun savePasswordCredential(
        activity: Activity?,
        email: String,
        password: String,
    ): PasswordCredentialSaveResult {
        val cleanEmail = passwordCredentialSaveEmailOrNull(email, password)
        if (cleanEmail == null) {
            return PasswordCredentialSaveResult.Failure
        }
        val credentialActivity = activity ?: return PasswordCredentialSaveResult.Failure

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

internal fun passwordCredentialSaveEmailOrNull(
    email: String,
    password: String,
): String? = email.trim().takeIf { it.isNotBlank() && password.isNotBlank() }
