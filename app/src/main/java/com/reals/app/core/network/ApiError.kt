package com.reals.app.core.network

import com.reals.app.core.appcheck.AppCheckFailureReason

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

    data class AppCheck(
        val reason: AppCheckFailureReason,
        val message: String,
    ) : ApiError

    data class PhotoPreparation(
        val reason: PhotoPreparationReason,
        val message: String,
    ) : ApiError

    data object LocalFirebaseEmailVerification : ApiError

    data class Unexpected(val message: String) : ApiError

}

enum class PhotoPreparationReason {
    UndecodableSource,
    SourceTooLarge,
    CacheWriteFailure,
    EncodingFailure,
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
    InvalidToken("INVALID_TOKEN"),
    MissingAppCheckToken("MISSING_APP_CHECK_TOKEN"),
    InvalidAppCheckToken("INVALID_APP_CHECK_TOKEN"),
    AppCheckVerificationUnavailable("APP_CHECK_VERIFICATION_UNAVAILABLE"),
    AuthenticityVerificationNotConfigured("AUTHENTICITY_VERIFICATION_NOT_CONFIGURED"),
    AuthenticityVerificationProviderError("AUTHENTICITY_VERIFICATION_PROVIDER_ERROR"),
    ProfileAuthenticityVerificationRequired("PROFILE_AUTHENTICITY_VERIFICATION_REQUIRED"),
    ProfilePhotosRequired("PROFILE_PHOTOS_REQUIRED"),
    ProfilePersonPhotoRequired("PROFILE_PERSON_PHOTO_REQUIRED"),
    ProfileFullBodyPhotoRequired("PROFILE_FULL_BODY_PHOTO_REQUIRED"),
    ProfilePhotoLimitReached("PROFILE_PHOTO_LIMIT_REACHED"),
    InvalidProfileBirthDate("INVALID_PROFILE_BIRTH_DATE"),
    InvalidProfileCountry("INVALID_PROFILE_COUNTRY"),
    InvalidMatchFilters("INVALID_MATCH_FILTERS"),
    PhotoPositionInvalid("PHOTO_POSITION_INVALID"),
    PhotoPositionOccupied("PHOTO_POSITION_OCCUPIED"),
    PhotoUrlInvalid("PHOTO_URL_INVALID"),
    InvalidProfilePhoto("INVALID_PROFILE_PHOTO"),
    ProfilePhotoUploadBusy("PROFILE_PHOTO_UPLOAD_BUSY"),
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
    VisualContentNotAvailable("VISUAL_CONTENT_NOT_AVAILABLE"),
    ChatNotFound("CHAT_NOT_FOUND"),
    ChatNotAvailable("CHAT_NOT_AVAILABLE"),
    ChatExpired("CHAT_EXPIRED"),
    ChatAbandoned("CHAT_ABANDONED"),
    ChatMessageInvalid("CHAT_MESSAGE_INVALID"),
    ChatDecisionNotAvailable("CHAT_DECISION_NOT_AVAILABLE"),
    ChatDecisionAlreadySubmitted("CHAT_DECISION_ALREADY_SUBMITTED"),
    ChatMinMessagesRequired("CHAT_MIN_MESSAGES_REQUIRED"),
    ChatMutualCancellationPending("CHAT_MUTUAL_CANCELLATION_PENDING"),
    ChatAudioFeatureDisabled("CHAT_AUDIO_FEATURE_DISABLED"),
    ChatAudioInvalidFormat("CHAT_AUDIO_INVALID_FORMAT"),
    ChatAudioTooLarge("CHAT_AUDIO_TOO_LARGE"),
    ChatAudioTooLong("CHAT_AUDIO_TOO_LONG"),
    ChatAudioGuidanceRequired("CHAT_AUDIO_GUIDANCE_REQUIRED"),
    ChatAudioGuidanceNotAvailable("CHAT_AUDIO_GUIDANCE_NOT_AVAILABLE"),
    ChatAudioLimitReached("CHAT_AUDIO_LIMIT_REACHED"),
    ChatAudioWaitingForBoth("CHAT_AUDIO_WAITING_FOR_BOTH"),
    ChatAudioNotAvailableYet("CHAT_AUDIO_NOT_AVAILABLE_YET"),
    ChatAudioUploadBusy("CHAT_AUDIO_UPLOAD_BUSY"),
    ChatAudioUploadFailed("CHAT_AUDIO_UPLOAD_FAILED"),
    ChatMessageIdempotencyConflict("CHAT_MESSAGE_IDEMPOTENCY_CONFLICT"),
    FirstChatGuidanceParticipationRequired("FIRST_CHAT_GUIDANCE_PARTICIPATION_REQUIRED"),
    FirstChatGuidanceNextAlreadyRequested("FIRST_CHAT_GUIDANCE_NEXT_ALREADY_REQUESTED"),
    FirstChatGuidanceCompleted("FIRST_CHAT_GUIDANCE_COMPLETED"),
    ChatExitRequestNotFound("CHAT_EXIT_REQUEST_NOT_FOUND"),
    ChatExitRequestNotAvailable("CHAT_EXIT_REQUEST_NOT_AVAILABLE"),
    ChatExitRequestAlreadyPending("CHAT_EXIT_REQUEST_ALREADY_PENDING"),
    SecondChatNotAvailable("SECOND_CHAT_NOT_AVAILABLE"),
    SecondChatJoinRequired("SECOND_CHAT_JOIN_REQUIRED"),
    SecondChatNotAvailableYet("SECOND_CHAT_NOT_AVAILABLE_YET"),
    SecondChatEntryClosed("SECOND_CHAT_ENTRY_CLOSED"),
    SecondChatAlreadyResolved("SECOND_CHAT_ALREADY_RESOLVED"),
    SecondChatExpired("SECOND_CHAT_EXPIRED"),
    SecondChatNoShowClaimNotAvailable("SECOND_CHAT_NO_SHOW_CLAIM_NOT_AVAILABLE"),
    SecondChatNoShowClaimAlreadyPending("SECOND_CHAT_NO_SHOW_CLAIM_ALREADY_PENDING"),
    SecondChatCompletionNotAvailable("SECOND_CHAT_COMPLETION_NOT_AVAILABLE"),
    SecondChatCompletionRequestAlreadyPending("SECOND_CHAT_COMPLETION_REQUEST_ALREADY_PENDING"),
    SecondChatCompletionRequestNotFound("SECOND_CHAT_COMPLETION_REQUEST_NOT_FOUND"),
    SecondChatCompletionRequestNotActionable("SECOND_CHAT_COMPLETION_REQUEST_NOT_ACTIONABLE"),
    SecondChatCompletionRequestCooldown("SECOND_CHAT_COMPLETION_REQUEST_COOLDOWN"),
    SecondChatInactivityClaimNotAvailable("SECOND_CHAT_INACTIVITY_CLAIM_NOT_AVAILABLE"),
    SecondChatInactivityClaimAlreadyPending("SECOND_CHAT_INACTIVITY_CLAIM_ALREADY_PENDING"),
    SecondChatConversationAlreadyResolved("SECOND_CHAT_CONVERSATION_ALREADY_RESOLVED"),
    SecondChatOrdinaryCancellationNotAllowed("SECOND_CHAT_ORDINARY_CANCELLATION_NOT_ALLOWED"),
    SchedulingNotAvailable("SCHEDULING_NOT_AVAILABLE"),
    SchedulingExpired("SCHEDULING_EXPIRED"),
    SchedulingNegotiationNotFound("SCHEDULING_NEGOTIATION_NOT_FOUND"),
    SchedulingInvalidProposals("SCHEDULING_INVALID_PROPOSALS"),
    SchedulingProposalsAlreadySubmitted("SCHEDULING_PROPOSALS_ALREADY_SUBMITTED"),
    SchedulingRoundChanged("SCHEDULING_ROUND_CHANGED"),
    SchedulingSlotConflict("SCHEDULING_SLOT_CONFLICT"),
    SchedulingProposalNotAvailable("SCHEDULING_PROPOSAL_NOT_AVAILABLE"),
    SchedulingCannotAcceptOwnProposal("SCHEDULING_CANNOT_ACCEPT_OWN_PROPOSAL"),
    SchedulingPartnerProposalsNotAvailable("SCHEDULING_PARTNER_PROPOSALS_NOT_AVAILABLE"),
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
    is ApiError.Network -> "No pudimos conectarnos. Revisá tu conexión e intentá nuevamente."
    is ApiError.Auth -> when (reason) {
        AuthFailureReason.FIREBASE_NOT_CONFIGURED -> "La app todavía no está lista para iniciar sesión en este entorno."
        AuthFailureReason.NOT_SIGNED_IN,
        AuthFailureReason.TOKEN_MISSING,
        AuthFailureReason.TOKEN_UNAVAILABLE -> "Tu sesión necesita renovarse. Volvé a iniciar sesión."
    }
    is ApiError.AppCheck -> "No pudimos verificar ésta instalación. Revisá tu conexión e intentá nuevamente."
    is ApiError.PhotoPreparation -> "No se pudo preparar la foto. Probá con otra imagen o intentá nuevamente."
    ApiError.LocalFirebaseEmailVerification ->
        "No pudimos preparar la cuenta Firebase para pruebas locales. Verificá que el backend local tenga habilitada la auto-verificación y volvé a intentar."
    is ApiError.Unexpected -> "Algo no salió como esperábamos. Intentá nuevamente."
}

fun ApiError.toUserTitle(context: ErrorContext = ErrorContext.General): String = when (context) {
    ErrorContext.ProfileCreation -> "No pudimos crear tu perfil"
    ErrorContext.ProfileUpdate -> "No pudimos guardar tu perfil"
    ErrorContext.MatchFilters -> "No pudimos guardar tus filtros"
    ErrorContext.ProfileActivation -> "Tu perfil todavía no se pudo activar"
    ErrorContext.PhotoUpload -> "No pudimos subir la foto"
    ErrorContext.PhotoReplace -> "No pudimos reemplazar la foto"
    ErrorContext.PhotoDelete -> "No pudimos borrar la foto"
    ErrorContext.Matchmaking -> "No pudimos iniciar la búsqueda"
    ErrorContext.Home -> "No pudimos actualizar tu estado"
    ErrorContext.Chat -> "No pudimos completar la acción"
    ErrorContext.VisualReview -> "No pudimos completar la revisión"
    ErrorContext.Scheduling -> "No pudimos coordinar el horario"
    ErrorContext.Account -> "No pudimos actualizar tu cuenta"
    ErrorContext.Legal -> "No pudimos actualizar los documentos"
    ErrorContext.ManualBlock -> "No pudimos bloquear a ésta persona"
    ErrorContext.General -> "Algo salió mal"
}

private fun userMessageForBackendError(code: BackendErrorCode, context: ErrorContext): String = when (code) {
    BackendErrorCode.ProfileRequired -> "Necesitás crear tu perfil antes de seguir."
    BackendErrorCode.ProfileNotActive -> "Tu perfil está en borrador. Activa tu perfil para poder buscar chat."
    BackendErrorCode.ActivePenalty -> "Por ahora no podés entrar a la búsqueda. Intentá nuevamente más adelante."
    BackendErrorCode.ActiveMatchLimitReached,
    BackendErrorCode.ActiveConnectionLimitReached -> "Ya tenés conversaciones o experiencias activas. Terminá una antes de buscar otra."
    BackendErrorCode.InvalidSearchLocation -> "No pudimos usar tu ubicación actual. Revisá los permisos o intentá nuevamente."
    BackendErrorCode.ProfileAlreadyExists -> "Ya tenés un perfil creado."
    BackendErrorCode.ProfileNotFound -> "No encontramos tu perfil. Actualizá la sesión e intentá nuevamente."
    BackendErrorCode.ProfileNotActivatable -> "Tu perfil necesita completarse antes de activarlo."
    BackendErrorCode.EmailNotVerified -> "Verificá tu email antes de activar el perfil."
    BackendErrorCode.InvalidToken -> "Tu sesión necesita renovarse. Volvé a iniciar sesión."
    BackendErrorCode.MissingAppCheckToken,
    BackendErrorCode.InvalidAppCheckToken,
    BackendErrorCode.AppCheckVerificationUnavailable ->
        "No pudimos verificar ésta instalación. Revisá tu conexión e intentá nuevamente."
    BackendErrorCode.AuthenticityVerificationNotConfigured ->
        "La verificación de autenticidad del perfil no está disponible en este entorno."
    BackendErrorCode.AuthenticityVerificationProviderError ->
        "No pudimos completar la verificación de autenticidad del perfil. Intentá nuevamente más tarde."
    BackendErrorCode.ProfileAuthenticityVerificationRequired ->
        "Necesitás verificar la autenticidad del perfil antes de activarlo."
    BackendErrorCode.ProfilePhotosRequired -> "Subí más fotos para poder activar tu perfil."
    BackendErrorCode.ProfilePersonPhotoRequired -> "Necesitamos al menos una foto clara tuya para activar tu perfil."
    BackendErrorCode.ProfileFullBodyPhotoRequired -> "Necesitamos una foto de cuerpo completo para activar tu perfil."
    BackendErrorCode.ProfilePhotoLimitReached -> "Ya llegaste al máximo de fotos permitidas."
    BackendErrorCode.InvalidProfileBirthDate -> "Revisá tu fecha de nacimiento."
    BackendErrorCode.InvalidProfileCountry -> "Seleccioná un país válido."
    BackendErrorCode.InvalidMatchFilters -> "Revisá las edades y la distancia. Hay algún valor fuera de rango."
    BackendErrorCode.PhotoPositionInvalid -> "Esa posición de foto no está disponible."
    BackendErrorCode.PhotoPositionOccupied -> "Ya hay una foto en esa posición. Podés reemplazarla o elegir otra."
    BackendErrorCode.PhotoUrlInvalid -> "La foto no tiene un formato válido."
    BackendErrorCode.InvalidProfilePhoto -> "La foto no parece válida. Probá con otra imagen."
    BackendErrorCode.ProfilePhotoUploadBusy -> "La carga de fotos está ocupada. Esperá unos segundos e intentá nuevamente."
    BackendErrorCode.ProfilePhotoNotFound -> "No encontramos esa foto. Actualizá la lista e intentá nuevamente."
    BackendErrorCode.AccountDeleted -> "Esta cuenta está pendiente de eliminación. Podés recuperarla si todavía está dentro del plazo."
    BackendErrorCode.AccountDeletionFinalized -> "La cuenta ya no puede recuperarse. Podés crear una cuenta nueva."
    BackendErrorCode.LegalActionRequired -> "Necesitás completar los documentos vigentes antes de continuar."
    BackendErrorCode.LegalDocumentVersionNotCurrent,
    BackendErrorCode.LegalDocumentNotFound -> "Los documentos vigentes cambiaron. Actualizá la información e intentá nuevamente."
    BackendErrorCode.LegalDocumentActionInvalid -> "La acción requerida cambió. Actualizá los documentos e intentá nuevamente."
    BackendErrorCode.UserPairBlocked -> "Esta interacción ya no está disponible."
    BackendErrorCode.DomainConflict -> when (context) {
        ErrorContext.Chat -> "La conversación no cumple una regla del flujo todavía. Revisá el estado e intentá nuevamente."
        ErrorContext.VisualReview -> "La revisión visual no cumple una regla del flujo todavía. Revisá el mensaje personal o actualiza el estado."
        else -> "Esta acción no está disponible con el estado actual."
    }
    BackendErrorCode.PartnerPersonalMessageNotRead,
    BackendErrorCode.VisualReviewPartnerMessageNotRead ->
        "No pudimos registrar la decisión visual. Actualizá el estado e intentá nuevamente."
    BackendErrorCode.VisualContentNotAvailable -> "El contenido visual ya no está disponible. Actualizá tu Home."
    BackendErrorCode.ChatNotFound -> "No encontramos ésta conversación. Actualizá el estado."
    BackendErrorCode.ChatNotAvailable -> "Esta conversación ya no está disponible. Actualizá el estado."
    BackendErrorCode.ChatExpired -> "La conversaci\u00f3n venci\u00f3."
    BackendErrorCode.ChatAbandoned -> "La conversaci\u00f3n se cerr\u00f3 por inactividad."
    BackendErrorCode.ChatMessageInvalid -> "Revisá el mensaje. No puede estar vacío ni superar el límite permitido."
    BackendErrorCode.ChatDecisionNotAvailable -> "La decisión sobre ésta conversación ya no está disponible. Actualizá el estado."
    BackendErrorCode.ChatDecisionAlreadySubmitted -> "Ya enviaste tu decisión para ésta conversación."
    BackendErrorCode.ChatMinMessagesRequired -> "Antes de decidir, enviá al menos un poco más de conversación."
    BackendErrorCode.ChatMutualCancellationPending ->
        "La conversaci\u00f3n est\u00e1 pausada mientras se resuelve la solicitud."
    BackendErrorCode.ChatAudioFeatureDisabled -> "Los audios no están disponibles en este entorno."
    BackendErrorCode.ChatAudioInvalidFormat -> "El audio debe ser M4A/AAC válido."
    BackendErrorCode.ChatAudioTooLarge -> "El audio supera el tamaño máximo permitido."
    BackendErrorCode.ChatAudioTooLong -> "El audio supera la duración máxima permitida."
    BackendErrorCode.ChatAudioGuidanceRequired -> "Respondan la pregunta actual para habilitar audios."
    BackendErrorCode.ChatAudioGuidanceNotAvailable -> "Los audios se habilitarán al avanzar en las preguntas."
    BackendErrorCode.ChatAudioLimitReached -> "Ya enviaste el audio disponible en este chat."
    BackendErrorCode.ChatAudioWaitingForBoth -> "El audio se habilita cuando ambas personas hayan ingresado."
    BackendErrorCode.ChatAudioNotAvailableYet -> "El audio todavía no está disponible."
    BackendErrorCode.ChatAudioUploadBusy -> "La carga de audios está ocupada. Intentá nuevamente en unos segundos."
    BackendErrorCode.ChatAudioUploadFailed -> "No pudimos subir el audio. Intentá nuevamente."
    BackendErrorCode.ChatMessageIdempotencyConflict ->
        "No pudimos reintentar este audio. Borrá la grabación y creá una nueva."
    BackendErrorCode.FirstChatGuidanceParticipationRequired ->
        "Particip\u00e1 un poco m\u00e1s antes de pedir otra pregunta."
    BackendErrorCode.FirstChatGuidanceNextAlreadyRequested -> "Ya pediste cambiar ésta pregunta."
    BackendErrorCode.FirstChatGuidanceCompleted -> "Ya completaron las preguntas de ésta conversaci\u00f3n."
    BackendErrorCode.ChatExitRequestNotFound -> "No encontramos esa solicitud de salida. Actualizá la conversación."
    BackendErrorCode.ChatExitRequestNotAvailable -> "Esa solicitud de salida ya no está disponible."
    BackendErrorCode.ChatExitRequestAlreadyPending -> "Ya hay una solicitud de salida pendiente."
    BackendErrorCode.SecondChatNotAvailable -> "El segundo chat todavía no está disponible o ya no se puede abrir."
    BackendErrorCode.SecondChatJoinRequired -> "Entrá al segundo chat para continuar."
    BackendErrorCode.SecondChatNotAvailableYet -> "El segundo chat todavía no está disponible."
    BackendErrorCode.SecondChatEntryClosed -> "La ventana para entrar al segundo chat ya cerró."
    BackendErrorCode.SecondChatAlreadyResolved,
    BackendErrorCode.SecondChatConversationAlreadyResolved -> "El segundo chat ya terminó. Actualizamos el estado."
    BackendErrorCode.SecondChatExpired -> "El segundo chat ya venció."
    BackendErrorCode.SecondChatNoShowClaimNotAvailable -> "Todavía no podés marcar que la otra persona no llegó."
    BackendErrorCode.SecondChatNoShowClaimAlreadyPending -> "Ya hay una solicitud de no-show pendiente."
    BackendErrorCode.SecondChatCompletionNotAvailable ->
        "Todav\u00eda no pod\u00e9s proponer finalizar este segundo chat."
    BackendErrorCode.SecondChatCompletionRequestAlreadyPending ->
        "Ya hay una propuesta de cierre pendiente."
    BackendErrorCode.SecondChatCompletionRequestNotFound ->
        "Esa propuesta ya no est\u00e1 disponible. Actualizamos el estado."
    BackendErrorCode.SecondChatCompletionRequestNotActionable ->
        "Esa propuesta ya no se puede responder."
    BackendErrorCode.SecondChatCompletionRequestCooldown ->
        "Podr\u00e1s volver a proponer el cierre en unos segundos."
    BackendErrorCode.SecondChatInactivityClaimNotAvailable ->
        "Todav\u00eda no pod\u00e9s reclamar falta de respuesta."
    BackendErrorCode.SecondChatInactivityClaimAlreadyPending ->
        "Ya hay un reclamo por falta de respuesta pendiente."
    BackendErrorCode.SecondChatOrdinaryCancellationNotAllowed ->
        "El segundo chat no permite cancelar por esta vía. Podés reportar por seguridad o bloquear a la persona."
    BackendErrorCode.SchedulingNotAvailable -> "La coordinación de horarios ya no está disponible. Actualizá el estado e intentá nuevamente."
    BackendErrorCode.SchedulingExpired -> "La coordinación de horarios venció."
    BackendErrorCode.SchedulingNegotiationNotFound -> "No encontramos la coordinación de horarios. Actualizá el estado."
    BackendErrorCode.SchedulingInvalidProposals -> "Revisá los horarios elegidos. Deben ser futuros, únicos y estar alineados cada media hora."
    BackendErrorCode.SchedulingProposalsAlreadySubmitted -> "Ya enviaste tus horarios para ésta ronda."
    BackendErrorCode.SchedulingRoundChanged -> "La ronda cambió. Actualizamos las opciones; revisalas antes de continuar."
    BackendErrorCode.SchedulingSlotConflict -> "Ese horario ya no está disponible porque se superpone con otra cita confirmada."
    BackendErrorCode.SchedulingProposalNotAvailable -> "Ese horario ya no está disponible. Actualizamos las opciones."
    BackendErrorCode.SchedulingCannotAcceptOwnProposal -> "No podés aceptar un horario propuesto por vos."
    BackendErrorCode.SchedulingPartnerProposalsNotAvailable -> "Esas opciones ya no están disponibles. Actualizamos el estado de la coordinación."
    BackendErrorCode.Unknown -> when (context) {
        ErrorContext.ProfileActivation -> "Revisá que tu perfil tenga la información y fotos necesarias."
        ErrorContext.PhotoUpload,
        ErrorContext.PhotoReplace -> "Probá con otra foto o intentá nuevamente en unos segundos."
        ErrorContext.Matchmaking -> "No pudimos iniciar la búsqueda. Revisá tu perfil e intentá nuevamente."
        ErrorContext.Chat -> "La conversación cambió de estado. Actualizá e intentá nuevamente."
        ErrorContext.VisualReview -> "La revisión visual cambió de estado. Actualizá e intentá nuevamente."
        ErrorContext.Scheduling -> "La coordinación de horarios cambió de estado. Actualizá e intentá nuevamente."
        else -> "Intentá nuevamente en unos segundos."
    }
}
