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

enum class ErrorContext {
    General,
    ProfileCreation,
    ProfileUpdate,
    MatchFilters,
    ProfileActivation,
    PhotoUpload,
    PhotoReplace,
    PhotoDelete,
    Matchmaking,
    Home,
    Chat,
    Account,
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

fun ApiError.toDisplayMessage(): String = toUserMessage()

fun ApiError.toUserMessage(context: ErrorContext = ErrorContext.General): String = when (this) {
    is ApiError.Backend -> userMessageForBackendError(code, context)
    is ApiError.Network -> "No pudimos conectarnos. Revisa tu conexion e intenta nuevamente."
    is ApiError.Auth -> when (reason) {
        AuthFailureReason.FIREBASE_NOT_CONFIGURED -> "La app todavia no esta lista para iniciar sesion en este entorno."
        AuthFailureReason.NOT_SIGNED_IN,
        AuthFailureReason.TOKEN_MISSING,
        AuthFailureReason.TOKEN_UNAVAILABLE -> "Tu sesion necesita renovarse. Volve a iniciar sesion."
    }
    is ApiError.Unexpected -> "Algo no salio como esperabamos. Intenta nuevamente."
}

fun ApiError.toUserTitle(context: ErrorContext = ErrorContext.General): String = when (context) {
    ErrorContext.ProfileCreation -> "No pudimos crear tu perfil"
    ErrorContext.ProfileUpdate -> "No pudimos guardar tu perfil"
    ErrorContext.MatchFilters -> "No pudimos guardar tus filtros"
    ErrorContext.ProfileActivation -> "Tu perfil todavia no se pudo activar"
    ErrorContext.PhotoUpload -> "No pudimos subir la foto"
    ErrorContext.PhotoReplace -> "No pudimos reemplazar la foto"
    ErrorContext.PhotoDelete -> "No pudimos borrar la foto"
    ErrorContext.Matchmaking -> "No pudimos iniciar la busqueda"
    ErrorContext.Home -> "No pudimos actualizar tu estado"
    ErrorContext.Chat -> "No pudimos completar la accion"
    ErrorContext.Account -> "No pudimos actualizar tu cuenta"
    ErrorContext.General -> "Algo salio mal"
}

private fun userMessageForBackendError(code: String?, context: ErrorContext): String = when (code) {
    "PROFILE_REQUIRED" -> "Necesitas crear tu perfil antes de seguir."
    "PROFILE_NOT_ACTIVE" -> "Tu perfil esta en borrador. Activa tu perfil para poder buscar chat."
    "ACTIVE_PENALTY" -> "Por ahora no podes entrar a la busqueda. Intenta nuevamente mas adelante."
    "ACTIVE_MATCH_LIMIT_REACHED" -> "Ya tenes conversaciones o experiencias activas. Termina una antes de buscar otra."
    "INVALID_SEARCH_LOCATION" -> "No pudimos usar tu ubicacion actual. Revisa los permisos o intenta nuevamente."
    "PROFILE_ALREADY_EXISTS" -> "Ya tenes un perfil creado."
    "PROFILE_NOT_FOUND" -> "No encontramos tu perfil. Actualiza la sesion e intenta nuevamente."
    "PROFILE_NOT_ACTIVATABLE" -> "Tu perfil necesita completarse antes de activarlo."
    "PROFILE_PHOTOS_REQUIRED" -> "Subi mas fotos para poder activar tu perfil."
    "PROFILE_PERSON_PHOTO_REQUIRED" -> "Necesitamos al menos una foto clara tuya para activar tu perfil."
    "PROFILE_FULL_BODY_PHOTO_REQUIRED" -> "Necesitamos una foto de cuerpo completo para activar tu perfil."
    "PROFILE_PHOTO_LIMIT_REACHED" -> "Ya llegaste al maximo de fotos permitidas."
    "INVALID_PROFILE_BIRTH_DATE" -> "Revisa tu fecha de nacimiento."
    "INVALID_MATCH_FILTERS" -> "Revisa las edades y la distancia. Hay algun valor fuera de rango."
    "PHOTO_POSITION_INVALID" -> "Esa posicion de foto no esta disponible."
    "PHOTO_POSITION_OCCUPIED" -> "Ya hay una foto en esa posicion. Podes reemplazarla o elegir otra."
    "PHOTO_URL_INVALID" -> "La foto no tiene un formato valido."
    "INVALID_PROFILE_PHOTO" -> "La foto no parece valida. Proba con otra imagen."
    "PROFILE_PHOTO_NOT_FOUND" -> "No encontramos esa foto. Actualiza la lista e intenta nuevamente."
    "ACCOUNT_DELETED" -> "Esta cuenta esta pendiente de eliminacion. Podes recuperarla si todavia esta dentro del plazo."
    "ACCOUNT_DELETION_FINALIZED" -> "La cuenta ya no puede recuperarse. Podes crear una cuenta nueva."
    else -> when (context) {
        ErrorContext.ProfileActivation -> "Revisa que tu perfil tenga la informacion y fotos necesarias."
        ErrorContext.PhotoUpload,
        ErrorContext.PhotoReplace -> "Proba con otra foto o intenta nuevamente en unos segundos."
        ErrorContext.Matchmaking -> "No pudimos iniciar la busqueda. Revisa tu perfil e intenta nuevamente."
        ErrorContext.Chat -> "La conversacion cambio de estado. Actualiza e intenta nuevamente."
        else -> "Intenta nuevamente en unos segundos."
    }
}
