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
    EmailNotVerified("EMAIL_NOT_VERIFIED"),
    AuthenticityVerificationNotConfigured("AUTHENTICITY_VERIFICATION_NOT_CONFIGURED"),
    AuthenticityVerificationProviderError("AUTHENTICITY_VERIFICATION_PROVIDER_ERROR"),
    ProfileAuthenticityVerificationRequired("PROFILE_AUTHENTICITY_VERIFICATION_REQUIRED"),
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
    LegalActionRequired("LEGAL_ACTION_REQUIRED"),
    LegalDocumentActionInvalid("LEGAL_DOCUMENT_ACTION_INVALID"),
    LegalDocumentNotFound("LEGAL_DOCUMENT_NOT_FOUND"),
    LegalDocumentVersionNotCurrent("LEGAL_DOCUMENT_VERSION_NOT_CURRENT"),
    UserPairBlocked("USER_PAIR_BLOCKED"),
    DomainConflict("DOMAIN_CONFLICT"),
    PartnerPersonalMessageNotRead("PARTNER_PERSONAL_MESSAGE_NOT_READ"),
    VisualReviewPartnerMessageNotRead("VISUAL_REVIEW_PARTNER_MESSAGE_NOT_READ"),
    ChatNotFound("CHAT_NOT_FOUND"),
    ChatNotAvailable("CHAT_NOT_AVAILABLE"),
    ChatExpired("CHAT_EXPIRED"),
    ChatAbandoned("CHAT_ABANDONED"),
    ChatMessageInvalid("CHAT_MESSAGE_INVALID"),
    ChatDecisionNotAvailable("CHAT_DECISION_NOT_AVAILABLE"),
    ChatDecisionAlreadySubmitted("CHAT_DECISION_ALREADY_SUBMITTED"),
    ChatMinMessagesRequired("CHAT_MIN_MESSAGES_REQUIRED"),
    ChatMutualCancellationPending("CHAT_MUTUAL_CANCELLATION_PENDING"),
    FirstChatGuidanceParticipationRequired("FIRST_CHAT_GUIDANCE_PARTICIPATION_REQUIRED"),
    FirstChatGuidanceNextAlreadyRequested("FIRST_CHAT_GUIDANCE_NEXT_ALREADY_REQUESTED"),
    FirstChatGuidanceCompleted("FIRST_CHAT_GUIDANCE_COMPLETED"),
    ChatExitRequestNotFound("CHAT_EXIT_REQUEST_NOT_FOUND"),
    ChatExitRequestNotAvailable("CHAT_EXIT_REQUEST_NOT_AVAILABLE"),
    ChatExitRequestAlreadyPending("CHAT_EXIT_REQUEST_ALREADY_PENDING"),
    SecondChatNotAvailable("SECOND_CHAT_NOT_AVAILABLE"),
    SecondChatNotAvailableYet("SECOND_CHAT_NOT_AVAILABLE_YET"),
    SecondChatExpired("SECOND_CHAT_EXPIRED"),
    SchedulingNotAvailable("SCHEDULING_NOT_AVAILABLE"),
    SchedulingExpired("SCHEDULING_EXPIRED"),
    SchedulingNegotiationNotFound("SCHEDULING_NEGOTIATION_NOT_FOUND"),
    SchedulingInvalidProposals("SCHEDULING_INVALID_PROPOSALS"),
    SchedulingProposalsAlreadySubmitted("SCHEDULING_PROPOSALS_ALREADY_SUBMITTED"),
    SchedulingProposalNotAvailable("SCHEDULING_PROPOSAL_NOT_AVAILABLE"),
    SchedulingCannotAcceptOwnProposal("SCHEDULING_CANNOT_ACCEPT_OWN_PROPOSAL"),
    SchedulingRoundNotRejectable("SCHEDULING_ROUND_NOT_REJECTABLE"),
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
    Scheduling,
    Account,
    Legal,
    ManualBlock,
}

val ApiError.Backend.backendErrorCode: BackendErrorCode
    get() = BackendErrorCode.fromRaw(code)

fun ApiError.isAccountDeleted(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.AccountDeleted
}

fun ApiError.isAccountDeletionFinalized(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.AccountDeletionFinalized
}

fun ApiError.isTerminalAuthFailure(): Boolean {
    return this is ApiError.Auth && reason == AuthFailureReason.NOT_SIGNED_IN
}

fun ApiError.isLegalActionRequired(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.LegalActionRequired
}

fun ApiError.isUserPairBlocked(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.UserPairBlocked
}

enum class AuthFailureReason {
    FIREBASE_NOT_CONFIGURED,
    NOT_SIGNED_IN,
    TOKEN_MISSING,
    TOKEN_UNAVAILABLE,
}

fun ApiError.toDisplayMessage(): String = toUserMessage()

fun ApiError.toUserMessage(context: ErrorContext = ErrorContext.General): String = when (this) {
    is ApiError.Backend -> if (
        backendErrorCode == BackendErrorCode.DomainConflict &&
        context != ErrorContext.Scheduling &&
        message.isNotBlank()
    ) {
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
    ErrorContext.Scheduling -> "No pudimos coordinar el horario"
    ErrorContext.Account -> "No pudimos actualizar tu cuenta"
    ErrorContext.Legal -> "No pudimos actualizar los documentos"
    ErrorContext.ManualBlock -> "No pudimos bloquear a esta persona"
    ErrorContext.General -> "Algo salio mal"
}

private fun userMessageForBackendError(code: BackendErrorCode, context: ErrorContext): String = when (code) {
    BackendErrorCode.ProfileRequired -> "Necesitas crear tu perfil antes de seguir."
    BackendErrorCode.ProfileNotActive -> "Tu perfil está en borrador. Activa tu perfil para poder buscar chat."
    BackendErrorCode.ActivePenalty -> "Por ahora no podes entrar a la busqueda. Intenta nuevamente mas adelante."
    BackendErrorCode.ActiveMatchLimitReached,
    BackendErrorCode.ActiveConnectionLimitReached -> "Ya tenes conversaciones o experiencias activas. Termina una antes de buscar otra."
    BackendErrorCode.InvalidSearchLocation -> "No pudimos usar tu ubicacion actual. Revisa los permisos o intenta nuevamente."
    BackendErrorCode.ProfileAlreadyExists -> "Ya tenes un perfil creado."
    BackendErrorCode.ProfileNotFound -> "No encontramos tu perfil. Actualiza la sesion e intenta nuevamente."
    BackendErrorCode.ProfileNotActivatable -> "Tu perfil necesita completarse antes de activarlo."
    BackendErrorCode.EmailNotVerified -> "Verificá tu email antes de activar el perfil."
    BackendErrorCode.AuthenticityVerificationNotConfigured ->
        "La verificacion de autenticidad del perfil no esta disponible en este entorno."
    BackendErrorCode.AuthenticityVerificationProviderError ->
        "No pudimos completar la verificacion de autenticidad del perfil. Intenta nuevamente mas tarde."
    BackendErrorCode.ProfileAuthenticityVerificationRequired ->
        "Necesitas verificar la autenticidad del perfil antes de activarlo."
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
    BackendErrorCode.LegalActionRequired -> "Necesitás completar los documentos vigentes antes de continuar."
    BackendErrorCode.LegalDocumentVersionNotCurrent,
    BackendErrorCode.LegalDocumentNotFound -> "Los documentos vigentes cambiaron. Actualizá la información e intentá nuevamente."
    BackendErrorCode.LegalDocumentActionInvalid -> "La acción requerida cambió. Actualizá los documentos e intentá nuevamente."
    BackendErrorCode.UserPairBlocked -> "Esta interacción ya no está disponible."
    BackendErrorCode.DomainConflict -> when (context) {
        ErrorContext.Chat -> "La conversacion no cumple una regla del flujo todavia. Revisa el estado e intenta nuevamente."
        ErrorContext.VisualReview -> "La revision visual no cumple una regla del flujo todavia. Revisa el mensaje personal o actualiza el estado."
        else -> "Esta accion no esta disponible con el estado actual."
    }
    BackendErrorCode.PartnerPersonalMessageNotRead,
    BackendErrorCode.VisualReviewPartnerMessageNotRead -> "Lee el mensaje personal de la otra persona antes de decidir."
    BackendErrorCode.ChatNotFound -> "No encontramos esta conversacion. Actualiza el estado."
    BackendErrorCode.ChatNotAvailable -> "Esta conversacion ya no esta disponible. Actualiza el estado."
    BackendErrorCode.ChatExpired -> "La conversaci\u00f3n venci\u00f3."
    BackendErrorCode.ChatAbandoned -> "La conversaci\u00f3n se cerr\u00f3 por inactividad."
    BackendErrorCode.ChatMessageInvalid -> "Revisa el mensaje. No puede estar vacio ni superar el limite permitido."
    BackendErrorCode.ChatDecisionNotAvailable -> "La decision sobre esta conversacion ya no esta disponible. Actualiza el estado."
    BackendErrorCode.ChatDecisionAlreadySubmitted -> "Ya enviaste tu decision para esta conversacion."
    BackendErrorCode.ChatMinMessagesRequired -> "Antes de decidir, envia al menos un poco mas de conversacion."
    BackendErrorCode.ChatMutualCancellationPending -> "Hay una solicitud de salida pendiente. Resolvela antes de decidir."
    BackendErrorCode.FirstChatGuidanceParticipationRequired ->
        "Particip\u00e1 un poco m\u00e1s antes de pedir otra pregunta."
    BackendErrorCode.FirstChatGuidanceNextAlreadyRequested -> "Ya pediste cambiar esta pregunta."
    BackendErrorCode.FirstChatGuidanceCompleted -> "Ya completaron las preguntas de esta conversaci\u00f3n."
    BackendErrorCode.ChatExitRequestNotFound -> "No encontramos esa solicitud de salida. Actualiza la conversacion."
    BackendErrorCode.ChatExitRequestNotAvailable -> "Esa solicitud de salida ya no esta disponible."
    BackendErrorCode.ChatExitRequestAlreadyPending -> "Ya hay una solicitud de salida pendiente."
    BackendErrorCode.SecondChatNotAvailable -> "El segundo chat todavia no esta disponible o ya no se puede abrir."
    BackendErrorCode.SecondChatNotAvailableYet -> "El segundo chat todavia no esta disponible."
    BackendErrorCode.SecondChatExpired -> "El segundo chat ya vencio."
    BackendErrorCode.SchedulingNotAvailable -> "La coordinacion de horarios ya no esta disponible. Actualiza el estado e intenta nuevamente."
    BackendErrorCode.SchedulingExpired -> "La coordinacion de horarios vencio."
    BackendErrorCode.SchedulingNegotiationNotFound -> "No encontramos la coordinacion de horarios. Actualiza el estado."
    BackendErrorCode.SchedulingInvalidProposals -> "Revisa los horarios elegidos. Deben ser futuros, unicos y estar alineados cada media hora."
    BackendErrorCode.SchedulingProposalsAlreadySubmitted -> "Ya enviaste tus horarios para esta ronda."
    BackendErrorCode.SchedulingProposalNotAvailable -> "Ese horario ya no esta disponible. Actualiza la propuesta."
    BackendErrorCode.SchedulingCannotAcceptOwnProposal -> "No podes aceptar un horario propuesto por vos."
    BackendErrorCode.SchedulingRoundNotRejectable -> "Todavia no se puede rechazar esta ronda. Espera a que ambas personas envien horarios."
    BackendErrorCode.Unknown -> when (context) {
        ErrorContext.ProfileActivation -> "Revisa que tu perfil tenga la informacion y fotos necesarias."
        ErrorContext.PhotoUpload,
        ErrorContext.PhotoReplace -> "Proba con otra foto o intenta nuevamente en unos segundos."
        ErrorContext.Matchmaking -> "No pudimos iniciar la busqueda. Revisa tu perfil e intenta nuevamente."
        ErrorContext.Chat -> "La conversacion cambio de estado. Actualiza e intenta nuevamente."
        ErrorContext.VisualReview -> "La revision visual cambio de estado. Actualiza e intenta nuevamente."
        ErrorContext.Scheduling -> "La coordinacion de horarios cambio de estado. Actualiza e intenta nuevamente."
        else -> "Intenta nuevamente en unos segundos."
    }
}
