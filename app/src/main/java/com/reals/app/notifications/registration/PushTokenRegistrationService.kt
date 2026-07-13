package com.reals.app.notifications.registration

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.messaging.messaging
import com.reals.app.core.network.ApiResult
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import kotlinx.coroutines.tasks.await

class PushTokenRegistrationService(
    private val context: Context,
    private val registerPushToken: RegisterPushTokenUseCase,
) {
    suspend fun registerCurrentTokenIfPossible() {
        if (!canRegisterToken()) return

        val token = runCatching { Firebase.messaging.token.await() }
            .onFailure { Log.w(TAG, "Could not read FCM token.", it) }
            .getOrNull()
            ?: return

        registerToken(token)
    }

    suspend fun registerToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank() || !canRegisterToken()) return

        when (val result = registerPushToken(cleanToken)) {
            is ApiResult.Success -> {
                if (!result.value) {
                    Log.w(TAG, "Backend did not confirm FCM token registration.")
                }
            }

            is ApiResult.Failure -> Log.w(TAG, "FCM token registration failed.")
        }
    }

    private fun canRegisterToken(): Boolean {
        if (FirebaseApp.getApps(context).isEmpty()) return false
        return Firebase.auth.currentUser != null
    }

    private companion object {
        const val TAG = "PushTokenRegistration"
    }
}
