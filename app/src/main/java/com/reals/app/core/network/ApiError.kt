package com.reals.app.core.network

sealed interface ApiError {

    data class Backend(
        val statusCode: Int,
        val code: String?,
        val error: String?,
        val message: String,
    ) : ApiError

    data class Network(val message: String) : ApiError

    data class Auth(
        val reason: AuthFailureReason,
        val message: String,
    ) : ApiError

    data class Unexpected(val message: String) : ApiError

}

fun ApiError.isAccountDeleted(): Boolean {
    return this is ApiError.Backend && code == "ACCOUNT_DELETED"
}

fun ApiError.isAccountDeletionFinalized(): Boolean {
    return this is ApiError.Backend && code == "ACCOUNT_DELETION_FINALIZED"
}

enum class AuthFailureReason {
    FIREBASE_NOT_CONFIGURED,
    NOT_SIGNED_IN,
    TOKEN_MISSING,
    TOKEN_UNAVAILABLE,
}

fun ApiError.toDisplayMessage(): String = when (this) {
    is ApiError.Backend -> "${statusCode} ${code ?: error ?: "BACKEND_ERROR"}: $message"
    is ApiError.Network -> "No se pudo conectar con el backend: $message"
    is ApiError.Auth -> message
    is ApiError.Unexpected -> "Error inesperado: $message"
}
