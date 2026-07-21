package com.reals.app.core.appcheck

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import java.util.concurrent.TimeUnit

class FirebaseAppCheckTokenProvider(
    private val context: Context,
    private val timeoutSeconds: Long = 10,
) : AppCheckTokenProvider {
    override fun getToken(forceRefresh: Boolean): String {
        if (FirebaseApp.getApps(context).isEmpty()) {
            throw AppCheckTokenAcquisitionException(
                reason = AppCheckFailureReason.NOT_CONFIGURED,
                message = "Firebase App Check no está configurado.",
            )
        }

        val result = try {
            Tasks.await(
                FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh),
                timeoutSeconds,
                TimeUnit.SECONDS,
            )
        } catch (exception: Exception) {
            throw AppCheckTokenAcquisitionException(
                reason = AppCheckFailureReason.TOKEN_UNAVAILABLE,
                message = exception.message ?: "No se pudo obtener el token de Firebase App Check.",
                cause = exception,
            )
        }

        return result.token.takeIf { it.isNotBlank() }
            ?: throw AppCheckTokenAcquisitionException(
                reason = AppCheckFailureReason.TOKEN_UNAVAILABLE,
                message = "Firebase App Check no devolvió un token válido.",
            )
    }
}

