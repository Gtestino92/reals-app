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

enum class BackendErrorCode(val raw: String) {
    ProfileRequired("PROFILE_REQUIRED"),
    ProfileNotActive("PROFILE_NOT_ACTIVE"),
    ActivePenalty("ACTIVE_PENALTY"),
    ActiveMatchLimitReached("ACTIVE_MATCH_LIMIT_REACHED"),
    ActiveConnectionLimitReached("ACTIVE_CONNECTION_LIMIT_REACHED"),
    InvalidSearchLocation("INVALID_SEARCH_LOCATION"),
    ProfileAlreadyExists("PROFILE_ALREADY_EXISTS"),
    ProfileNotFound("PROFILE_NOT_FOUND"),
    ProfileNotActivatable("PROFILE_NOT_ACTIVATABLE"),
    ProfilePhotosRequired("PROFILE_PHOTOS_REQUIRED"),
    ProfilePersonPhotoRequired("PROFILE_PERSON_PHOTO_REQUIRED"),
    ProfileFullBodyPhotoRequired("PROFILE_FULL_BODY_PHOTO_REQUIRED"),
    ProfilePhotoLimitReached("PROFILE_PHOTO_LIMIT_REACHED"),
    InvalidProfileBirthDate("INVALID_PROFILE_BIRTH_DATE"),
    InvalidMatchFilters("INVALID_MATCH_FILTERS"),
    PhotoPositionInvalid("PHOTO_POSITION_INVALID"),
    PhotoPositionOccupied("PHOTO_POSITION_OCCUPIED"),
    PhotoUrlInvalid("PHOTO_URL_INVALID"),
    InvalidProfilePhoto("INVALID_PROFILE_PHOTO"),
    ProfilePhotoNotFound("PROFILE_PHOTO_NOT_FOUND"),
    AccountDeleted("ACCOUNT_DELETED"),
    AccountDeletionFinalized("ACCOUNT_DELETION_FINALIZED"),
    DomainConflict("DOMAIN_CONFLICT"),
    PartnerPersonalMessageNotRead("PARTNER_PERSONAL_MESSAGE_NOT_READ"),
    VisualReviewPartnerMessageNotRead("VISUAL_REVIEW_PARTNER_MESSAGE_NOT_READ"),
    SecondChatNotAvailable("SECOND_CHAT_NOT_AVAILABLE"),
    SecondChatNotAvailableYet("SECOND_CHAT_NOT_AVAILABLE_YET"),
    SecondChatExpired("SECOND_CHAT_EXPIRED"),
    Unknown("UNKNOWN");

    companion object {
        fun fromRaw(value: String?): BackendErrorCode {
            val rawValue = value?.takeIf { it.isNotBlank() } ?: return Unknown
            return entries.firstOrNull { it.raw == rawValue } ?: Unknown
        }
    }
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
    VisualReview,
    Account,
}

val ApiError.Backend.backendErrorCode: BackendErrorCode
    get() = BackendErrorCode.fromRaw(code)

fun ApiError.isAccountDeleted(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.AccountDeleted
}

fun ApiError.isAccountDeletionFinalized(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.AccountDeletionFinalized
}

enum class AuthFailureReason {
    FIREBASE_NOT_CONFIGURED,
    NOT_SIGNED_IN,
    TOKEN_MISSING,
    TOKEN_UNAVAILABLE,
}

fun ApiError.toDisplayMessage(): String = toUserMessage()

fun ApiError.toUserMessage(context: ErrorContext = ErrorContext.General): String = when (this) {
    is ApiError.Backend -> if (backendErrorCode == BackendErrorCode.DomainConflict && message.isNotBlank()) {
        message
    } else {
        userMessageForBackendError(backendErrorCode, context)
    }
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
    ErrorContext.VisualReview -> "No pudimos completar la revision"
    ErrorContext.Account -> "No pudimos actualizar tu cuenta"
    ErrorContext.General -> "Algo salio mal"
}

private fun userMessageForBackendError(code: BackendErrorCode, context: ErrorContext): String = when (code) {
    BackendErrorCode.ProfileRequired -> "Necesitas crear tu perfil antes de seguir."
    BackendErrorCode.ProfileNotActive -> "Tu perfil esta en borrador. Activa tu perfil para poder buscar chat."
    BackendErrorCode.ActivePenalty -> "Por ahora no podes entrar a la busqueda. Intenta nuevamente mas adelante."
    BackendErrorCode.ActiveMatchLimitReached,
    BackendErrorCode.ActiveConnectionLimitReached -> "Ya tenes conversaciones o experiencias activas. Termina una antes de buscar otra."
    BackendErrorCode.InvalidSearchLocation -> "No pudimos usar tu ubicacion actual. Revisa los permisos o intenta nuevamente."
    BackendErrorCode.ProfileAlreadyExists -> "Ya tenes un perfil creado."
    BackendErrorCode.ProfileNotFound -> "No encontramos tu perfil. Actualiza la sesion e intenta nuevamente."
    BackendErrorCode.ProfileNotActivatable -> "Tu perfil necesita completarse antes de activarlo."
    BackendErrorCode.ProfilePhotosRequired -> "Subi mas fotos para poder activar tu perfil."
    BackendErrorCode.ProfilePersonPhotoRequired -> "Necesitamos al menos una foto clara tuya para activar tu perfil."
    BackendErrorCode.ProfileFullBodyPhotoRequired -> "Necesitamos una foto de cuerpo completo para activar tu perfil."
    BackendErrorCode.ProfilePhotoLimitReached -> "Ya llegaste al maximo de fotos permitidas."
    BackendErrorCode.InvalidProfileBirthDate -> "Revisa tu fecha de nacimiento."
    BackendErrorCode.InvalidMatchFilters -> "Revisa las edades y la distancia. Hay algun valor fuera de rango."
    BackendErrorCode.PhotoPositionInvalid -> "Esa posicion de foto no esta disponible."
    BackendErrorCode.PhotoPositionOccupied -> "Ya hay una foto en esa posicion. Podes reemplazarla o elegir otra."
    BackendErrorCode.PhotoUrlInvalid -> "La foto no tiene un formato valido."
    BackendErrorCode.InvalidProfilePhoto -> "La foto no parece valida. Proba con otra imagen."
    BackendErrorCode.ProfilePhotoNotFound -> "No encontramos esa foto. Actualiza la lista e intenta nuevamente."
    BackendErrorCode.AccountDeleted -> "Esta cuenta esta pendiente de eliminacion. Podes recuperarla si todavia esta dentro del plazo."
    BackendErrorCode.AccountDeletionFinalized -> "La cuenta ya no puede recuperarse. Podes crear una cuenta nueva."
    BackendErrorCode.DomainConflict -> when (context) {
        ErrorContext.Chat -> "La conversacion no cumple una regla del flujo todavia. Revisa el estado e intenta nuevamente."
        ErrorContext.VisualReview -> "La revision visual no cumple una regla del flujo todavia. Revisa el mensaje personal o actualiza el estado."
        else -> "Esta accion no esta disponible con el estado actual."
    }
    BackendErrorCode.PartnerPersonalMessageNotRead,
    BackendErrorCode.VisualReviewPartnerMessageNotRead -> "Lee el mensaje personal de la otra persona antes de decidir."
    BackendErrorCode.SecondChatNotAvailable -> "El segundo chat todavia no esta disponible o ya no se puede abrir."
    BackendErrorCode.SecondChatNotAvailableYet -> "El segundo chat todavia no esta disponible."
    BackendErrorCode.SecondChatExpired -> "El segundo chat ya vencio."
    BackendErrorCode.Unknown -> when (context) {
        ErrorContext.ProfileActivation -> "Revisa que tu perfil tenga la informacion y fotos necesarias."
        ErrorContext.PhotoUpload,
        ErrorContext.PhotoReplace -> "Proba con otra foto o intenta nuevamente en unos segundos."
        ErrorContext.Matchmaking -> "No pudimos iniciar la busqueda. Revisa tu perfil e intenta nuevamente."
        ErrorContext.Chat -> "La conversacion cambio de estado. Actualiza e intenta nuevamente."
        ErrorContext.VisualReview -> "La revision visual cambio de estado. Actualiza e intenta nuevamente."
        else -> "Intenta nuevamente en unos segundos."
    }
}
