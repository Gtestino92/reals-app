package com.reals.app.core.firebase

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.reals.app.data.api.AuthTokenProvider
import kotlinx.coroutines.tasks.await

class FirebaseNotConfiguredException : IllegalStateException(
    "Firebase no está configurado. Registrá com.reals.app en Firebase y agregá app/google-services.json."
)

class MissingFirebaseUserException : IllegalStateException(
    "No hay usuario autenticado en Firebase."
)

class MissingFirebaseTokenException : IllegalStateException(
    "Firebase no devolvió un ID token válido."
)

class FirebaseAuthTokenProvider(private val context: Context) : AuthTokenProvider {
    override suspend fun getIdToken(forceRefresh: Boolean): String {
        if (FirebaseApp.getApps(context).isEmpty()) {
            throw FirebaseNotConfiguredException()
        }
        val user = Firebase.auth.currentUser ?: throw MissingFirebaseUserException()
        val token = user.getIdToken(forceRefresh).await().token
        return token ?: throw MissingFirebaseTokenException()
    }
}
