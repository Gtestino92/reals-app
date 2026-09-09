package com.reals.app.ui.auth

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

sealed interface GoogleCredentialResult {
    data class Success(val idToken: String) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data object NotConfigured : GoogleCredentialResult
    data object Failure : GoogleCredentialResult
}

class GoogleCredentialClient(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun getGoogleIdToken(activity: Activity?): GoogleCredentialResult {
        val serverClientId = appContext.defaultWebClientId()
            ?: return GoogleCredentialResult.NotConfigured
        val credentialActivity = activity ?: return GoogleCredentialResult.Failure
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(serverClientId).build()
            )
            .build()

        return try {
            val response = CredentialManager.create(appContext).getCredential(
                context = credentialActivity,
                request = request,
            )
            extractGoogleIdToken(response.credential)
        } catch (exception: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (exception: GetCredentialException) {
            GoogleCredentialResult.Failure
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            GoogleCredentialResult.Failure
        }
    }
}

internal fun extractGoogleIdToken(credential: Credential): GoogleCredentialResult {
    val customCredential = credential as? CustomCredential
        ?: return GoogleCredentialResult.Failure
    return extractGoogleIdToken(
        type = customCredential.type,
        data = customCredential.data,
    )
}

internal fun extractGoogleIdToken(
    type: String,
    data: Bundle,
    parser: (Bundle) -> String = { GoogleIdTokenCredential.createFrom(it).idToken },
): GoogleCredentialResult {
    if (type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleCredentialResult.Failure
    }
    val idToken = runCatching { parser(data) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return GoogleCredentialResult.Failure
    return GoogleCredentialResult.Success(idToken)
}

private fun Context.defaultWebClientId(): String? {
    val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
    if (resourceId == 0) return null
    return getString(resourceId)
        .trim()
        .takeIf(::isUsableGoogleServerClientId)
}

internal fun isUsableGoogleServerClientId(value: String): Boolean =
    value.isNotBlank() && value.endsWith(".apps.googleusercontent.com")
