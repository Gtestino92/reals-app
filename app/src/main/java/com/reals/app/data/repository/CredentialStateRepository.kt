package com.reals.app.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import kotlinx.coroutines.CancellationException

open class CredentialStateRepository(context: Context) {
    private val appContext = context.applicationContext ?: context

    open suspend fun clearCredentialState() {
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w("RealsCredentials", "Credential state clear failed.", exception)
        }
    }
}
