package com.reals.app.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

sealed interface AuthOperationResult {
    data object Success : AuthOperationResult
    data class Failure(val message: String) : AuthOperationResult
}

class FirebaseAuthRepository(private val context: Context) {
    fun isConfigured(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun hasSignedInUser(): Boolean = authOrNull()?.currentUser != null

    fun currentUserEmail(): String? = authOrNull()?.currentUser?.email

    suspend fun signIn(email: String, password: String): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)
        return runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { AuthOperationResult.Failure(it.localizedMessage ?: "No se pudo iniciar sesion.") },
        )
    }

    suspend fun signUp(email: String, password: String): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)
        return runCatching {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { AuthOperationResult.Failure(it.localizedMessage ?: "No se pudo crear la cuenta.") },
        )
    }

    fun signOut() {
        authOrNull()?.signOut()
    }

    private fun authOrNull(): FirebaseAuth? {
        if (!isConfigured()) return null
        return Firebase.auth
    }

    companion object {
        const val firebaseMissingMessage =
            "Firebase no esta configurado. Registra com.reals.app y agrega app/google-services.json."
    }
}
