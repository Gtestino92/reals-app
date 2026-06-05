package com.reals.app.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
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
            onFailure = { AuthOperationResult.Failure(it.toSignInMessage()) },
        )
    }

    suspend fun signUp(email: String, password: String): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)
        return runCatching {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }.fold(
            onSuccess = { AuthOperationResult.Success },
            onFailure = { AuthOperationResult.Failure(it.toSignUpMessage()) },
        )
    }

    fun signOut() {
        authOrNull()?.signOut()
    }

    suspend fun deleteFirebaseUser(): AuthOperationResult {
        val auth = authOrNull()
            ?: return AuthOperationResult.Failure(firebaseMissingMessage)

        val user = auth.currentUser
            ?: return AuthOperationResult.Failure("No hay usuario autenticado en Firebase.")

        return runCatching {
            user.delete().await()
            auth.signOut()
        }.fold(
            onSuccess = {
                AuthOperationResult.Success
            },
            onFailure = {
                AuthOperationResult.Failure(
                    it.localizedMessage ?: "No se pudo eliminar el usuario en Firebase."
                )
            },
        )
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
                "Ya existe una cuenta con ese email."

            is FirebaseAuthWeakPasswordException ->
                "La contraseña es demasiado débil."

            is FirebaseAuthInvalidCredentialsException ->
                "El email no tiene un formato válido."

            else ->
                localizedMessage ?: "No se pudo crear la cuenta."
        }
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
