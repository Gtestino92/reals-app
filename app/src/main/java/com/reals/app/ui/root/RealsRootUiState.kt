package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.isLegalActionRequired
import com.reals.app.core.time.ServerClockSnapshot
import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SchedulingAvailability
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.domain.model.SecondChatStatus
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.matchmaking.HomeScreenModel

sealed interface RealsRootUiState {
    data object Checking : RealsRootUiState

    data class MissingFirebase(val message: String) : RealsRootUiState

    data class Login(
        val loading: Boolean = false,
        val googleLoading: Boolean = false,
        val googleAttemptId: Long? = null,
        val error: String? = null,
        val passwordResetLoading: Boolean = false,
        val passwordResetAttemptId: Long? = null,
        val passwordResetMessage: String? = null,
        val passwordResetAvailableAtMillis: Long? = null,
    ) : RealsRootUiState

    data class LoadingSession(val email: String?) : RealsRootUiState

    data class AccountDeletionScheduled(
        val deletionFinalizesAt: String?,
    ) : RealsRootUiState

    data class AccountDeletionPending(
        val user: BackendUser,
        val reactivating: Boolean = false,
        val finalizingDeletion: Boolean = false,
        val error: ApiError? = null,
    ) : RealsRootUiState

    data class LegalRequirements(
        val session: ProvisionedSession,
        val resumeContext: LegalResumeContext,
        val requirementsSatisfied: Boolean = false,
        val documents: List<LegalRequirementUiItem> = emptyList(),
        val loading: Boolean = false,
        val submittingDocumentType: LegalDocumentType? = null,
        val error: ApiError? = null,
        val deletingAccount: Boolean = false,
        val accountDeleteError: ApiError? = null,
    ) : RealsRootUiState

    data class Ready(
        val session: ProvisionedSession,
        val profileOp: ProfileManagementState = ProfileManagementState(),
        val photos: PhotoManagementUiState = PhotoManagementUiState(),
        val home: HomeUiState = HomeUiState(),
        val account: AccountUiState = AccountUiState(),
        val editingActiveProfile: Boolean = false,
        val profileManagementDestination: ProfileManagementDestination? = null,
        val affinityHomeSummary: AffinityHomeSummaryUiState = AffinityHomeSummaryUiState(),
        val affinityQuestionnaire: AffinityQuestionnaireUiState = AffinityQuestionnaireUiState(),
        val profileQuestions: ProfileQuestionUiState = ProfileQuestionUiState(),
    ) : RealsRootUiState {
        val creatingProfile: Boolean get() = profileOp.creatingProfile
        val profileCreateError: ApiError? get() = profileOp.profileCreateError
        val countriesLoading: Boolean get() = profileOp.countriesLoading
        val countries: List<CountryReference> get() = profileOp.countries
        val countriesError: ApiError? get() = profileOp.countriesError
        val countriesLoaded: Boolean get() = profileOp.countriesLoaded
        val updatingProfile: Boolean get() = profileOp.updatingProfile
        val profileUpdateError: ApiError? get() = profileOp.profileUpdateError
        val profileUpdateMessage: String? get() = profileOp.profileUpdateMessage
        val updatingMatchFilters: Boolean get() = profileOp.updatingMatchFilters
        val matchFiltersError: ApiError? get() = profileOp.matchFiltersError
        val matchFiltersMessage: String? get() = profileOp.matchFiltersMessage
        val activatingProfile: Boolean get() = profileOp.activatingProfile
        val profileActivationError: ApiError? get() = profileOp.profileActivationError
        val sendingEmailVerification: Boolean get() = profileOp.sendingEmailVerification
        val checkingEmailVerification: Boolean get() = profileOp.checkingEmailVerification
        val emailVerificationMessage: String? get() = profileOp.emailVerificationMessage
        val emailVerificationError: String? get() = profileOp.emailVerificationError
        val emailVerificationRequired: Boolean get() = profileOp.emailVerificationRequired
        val emailVerificationLocallyVerified: Boolean get() = profileOp.emailVerificationLocallyVerified
        val resendEmailVerificationAvailableAtMillis: Long?
            get() =
                profileOp.resendEmailVerificationAvailableAtMillis
        val checkEmailVerificationAvailableAtMillis: Long?
            get() =
                profileOp.checkEmailVerificationAvailableAtMillis
        val loadingPhotos: Boolean get() = photos.loadingPhotos
        val profilePhotos: List<ProfilePhoto> get() = photos.profilePhotos
        val profilePhotosError: ApiError? get() = photos.profilePhotosError
        val addingPhoto: Boolean get() = photos.addingPhoto
        val reorderingPhotos: Boolean get() = photos.reorderingPhotos
        val photoReorderError: ApiError? get() = photos.photoReorderError
        val photoReorderMessage: String? get() = photos.photoReorderMessage
        val photoActionError: ApiError? get() = photos.photoActionError
        val photoActionMessage: String? get() = photos.photoActionMessage
        val homeState: HomeState? get() = home.homeState
        val homeLoading: Boolean get() = home.homeLoading
        val homeError: ApiError? get() = home.homeError
        val homeMessage: String? get() = home.homeMessage
        val matchmakingBlockedReason: ApiError? get() = home.matchmakingBlockedReason
        val deletingAccount: Boolean get() = account.deletingAccount
        val accountDeleteError: ApiError? get() = account.accountDeleteError
        val changingPassword: Boolean get() = account.changingPassword
        val changePasswordError: String? get() = account.changePasswordError
        val changePasswordMessage: String? get() = account.changePasswordMessage
    }

    data class FirstChat(
        val session: ProvisionedSession,
        val matchId: String,
        val chatId: String? = null,
        val match: Match? = null,
        val chat: Chat? = null,
        val messages: List<ChatMessage> = emptyList(),
        val optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        val exitRequests: List<ChatExitRequest> = emptyList(),
        val serverClockSnapshot: ServerClockSnapshot? = null,
        val dismissedUnansweredPeriodReference: String? = null,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val sending: Boolean = false,
        val audioUpload: ChatAudioUploadUiState = ChatAudioUploadUiState(),
        val audioDraft: ChatAudioDraftUiState? = null,
        val actionLoading: Boolean = false,
        val actionLoadingLabel: String? = null,
        val guidanceActionLoading: Boolean = false,
        val manualBlock: ManualBlockUiState = ManualBlockUiState(),
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class SecondChat(
        val session: ProvisionedSession,
        val connectionId: String,
        val matchId: String,
        val partnerName: String? = null,
        val chatId: String? = null,
        val chat: Chat? = null,
        val messages: List<ChatMessage> = emptyList(),
        val optimisticMessages: List<OptimisticOutgoingMessage> = emptyList(),
        val exitRequests: List<ChatExitRequest> = emptyList(),
        val lifecycle: SecondChatLifecycleUiState = SecondChatLifecycleUiState(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val sending: Boolean = false,
        val audioUpload: ChatAudioUploadUiState = ChatAudioUploadUiState(),
        val audioDraft: ChatAudioDraftUiState? = null,
        val actionLoading: Boolean = false,
        val actionLoadingLabel: String? = null,
        val manualBlock: ManualBlockUiState = ManualBlockUiState(),
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class VisualApproval(
        val session: ProvisionedSession,
        val matchId: String,
        val returnHomeSurface: HomeSurface = HomeSurface.Overview,
        val match: Match? = null,
        val profile: VisualProfile? = null,
        val partnerMessage: String? = null,
        val partnerMessageLoaded: Boolean = false,
        val readingPartnerMessage: Boolean = false,
        val partnerMessageError: ApiError? = null,
        val myPersonalMessageSubmitted: Boolean = false,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val writingMessage: Boolean = false,
        val deciding: Boolean = false,
        val decidingLabel: String? = null,
        val manualBlock: ManualBlockUiState = ManualBlockUiState(),
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class Scheduling(
        val session: ProvisionedSession,
        val connectionId: String,
        val matchId: String,
        val partnerName: String? = null,
        val returnHomeSurface: HomeSurface = HomeSurface.Overview,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val submitting: Boolean = false,
        val submittingLabel: String? = null,
        val manualBlock: ManualBlockUiState = ManualBlockUiState(),
        val negotiation: SchedulingNegotiation? = null,
        val proposals: List<SchedulingProposal> = emptyList(),
        val availability: SchedulingAvailability? = null,
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class PartnerProfile(
        val session: ProvisionedSession,
        val matchId: String,
        val fallbackHomeSurface: HomeSurface = HomeSurface.Overview,
        val schedulingReturnContext: SchedulingReturnContext? = null,
        val profile: VisualProfile? = null,
        val partnerMessage: String? = null,
        val partnerMessageLoaded: Boolean = false,
        val loadingPartnerMessage: Boolean = false,
        val partnerMessageError: ApiError? = null,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val manualBlock: ManualBlockUiState = ManualBlockUiState(),
        val error: ApiError? = null,
    ) : RealsRootUiState

    data class PendingEngagement(
        val session: ProvisionedSession,
        val title: String,
        val body: String,
    ) : RealsRootUiState

    data class ActivationComplete(
        val session: ProvisionedSession,
        val result: ProfileActivationResult,
    ) : RealsRootUiState

    data class Failure(val error: ApiError) : RealsRootUiState
}

data class SecondChatLifecycleUiState(
    val status: SecondChatStatus? = null,
    val statusReceivedAtMillis: Long? = null,
    val joining: Boolean = false,
    val claimingNoShow: Boolean = false,
    val joinCompletedInThisSession: Boolean = false,
)

data class ManualBlockUiState(
    val loading: Boolean = false,
    val error: ApiError? = null,
)

data class ChatAudioUploadUiState(
    val uploading: Boolean = false,
    val error: ApiError? = null,
    val completedClientMessageId: String? = null,
    val nonRetryable: Boolean = false,
)

data class ChatAudioDraftUiState(
    val filePath: String,
    val clientMessageId: String,
    val durationMillis: Long,
    val sizeBytes: Long,
)

data class ProfileManagementState(
    val creatingProfile: Boolean = false,
    val profileCreateError: ApiError? = null,
    val countriesLoading: Boolean = false,
    val countries: List<CountryReference> = emptyList(),
    val countriesError: ApiError? = null,
    val countriesLoaded: Boolean = false,
    val updatingProfile: Boolean = false,
    val profileUpdateError: ApiError? = null,
    val profileUpdateMessage: String? = null,
    val updatingMatchFilters: Boolean = false,
    val matchFiltersError: ApiError? = null,
    val matchFiltersMessage: String? = null,
    val activatingProfile: Boolean = false,
    val profileActivationError: ApiError? = null,
    val sendingEmailVerification: Boolean = false,
    val checkingEmailVerification: Boolean = false,
    val emailVerificationMessage: String? = null,
    val emailVerificationError: String? = null,
    val emailVerificationRequired: Boolean = false,
    val emailVerificationLocallyVerified: Boolean = false,
    val resendEmailVerificationAvailableAtMillis: Long? = null,
    val checkEmailVerificationAvailableAtMillis: Long? = null,
)

data class PhotoManagementUiState(
    val loadingPhotos: Boolean = false,
    val profilePhotos: List<ProfilePhoto> = emptyList(),
    val profilePhotosError: ApiError? = null,
    val addingPhoto: Boolean = false,
    val reorderingPhotos: Boolean = false,
    val photoReorderError: ApiError? = null,
    val photoReorderMessage: String? = null,
    val photoActionError: ApiError? = null,
    val photoActionMessage: String? = null,
)

data class HomeUiState(
    val homeState: HomeState? = null,
    val homeStatusVersion: Long? = null,
    val screenModel: HomeScreenModel? = null,
    val surface: HomeSurface = HomeSurface.Overview,
    val allowDraftHomeWithoutInteractions: Boolean = false,
    val homeLoading: Boolean = false,
    val homeError: ApiError? = null,
    val homeMessage: String? = null,
    val matchmakingBlockedReason: ApiError? = null,
    val matchmakingSearchPhase: MatchmakingSearchUiPhase = MatchmakingSearchUiPhase.Idle,
)

enum class HomeSurface {
    Overview,
    Pending,
}

data class SchedulingReturnContext(
    val connectionId: String,
    val matchId: String,
    val partnerName: String?,
    val homeSurface: HomeSurface,
)

enum class MatchmakingSearchUiPhase {
    Idle,
    ResolvingLocation,
    JoiningQueue,
    Searching,
    Failed,
}

data class AccountUiState(
    val deletingAccount: Boolean = false,
    val accountDeleteError: ApiError? = null,
    val changingPassword: Boolean = false,
    val changePasswordError: String? = null,
    val changePasswordMessage: String? = null,
)

enum class ProfileManagementDestination {
    Profile,
    Search,
}

data class AffinityHomeSummaryUiState(
    val profileId: String? = null,
    val catalog: AffinityQuestionCatalog? = null,
    val answers: List<AffinityAnswer> = emptyList(),
    val loading: Boolean = false,
    val loadAttempted: Boolean = false,
)

data class AffinityQuestionnaireUiState(
    val open: Boolean = false,
    val profileId: String? = null,
    val destination: AffinityQuestionnaireDestination = AffinityQuestionnaireDestination.Overview,
    val catalog: AffinityQuestionCatalog? = null,
    val answers: List<AffinityAnswer> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val mutation: AffinityAnswerMutationUiState? = null,
    val error: ApiError? = null,
    val mutationError: ApiError? = null,
    val mutationFeedbackQuestionId: String? = null,
    val message: String? = null,

    val draftQuestionId: String? = null,

    val draftAnswerCode: String? = null,
)

sealed interface AffinityQuestionnaireDestination {
    data object Overview : AffinityQuestionnaireDestination
    data object Categories : AffinityQuestionnaireDestination
    data object Review : AffinityQuestionnaireDestination

    data class Question(
        val questionId: String,
        val source: AffinityQuestionSource,
    ) : AffinityQuestionnaireDestination
}

sealed interface AffinityQuestionSource {
    data object Continue : AffinityQuestionSource

    data class Category(
        val categoryId: String,
        val reviewAll: Boolean,
    ) : AffinityQuestionSource

    data object Review : AffinityQuestionSource
}

data class AffinityAnswerMutationUiState(
    val questionId: String,
    val pendingAnswerCode: String?,
    val requestId: Long = 0L,
)

data class ProfileQuestionUiState(
    val open: Boolean = false,
    val profileId: String? = null,
    val destination: ProfileQuestionDestination = ProfileQuestionDestination.Overview,
    val catalog: ProfileQuestionCatalog? = null,
    val answers: List<ProfileQuestionAnswer> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val mutation: ProfileQuestionMutationUiState? = null,
    val error: ApiError? = null,
    val mutationError: ApiError? = null,
    val feedback: ProfileQuestionFeedback? = null,
    val selectionDraftQuestionIds: List<String> = emptyList(),
)

sealed interface ProfileQuestionDestination {
    data object Overview : ProfileQuestionDestination
    data object Questions : ProfileQuestionDestination
    data object Selection : ProfileQuestionDestination
    data class Editor(val questionId: String) : ProfileQuestionDestination
}

sealed interface ProfileQuestionMutationKind {
    data object Upsert : ProfileQuestionMutationKind
    data object Delete : ProfileQuestionMutationKind
    data object Selection : ProfileQuestionMutationKind
}

data class ProfileQuestionMutationUiState(
    val kind: ProfileQuestionMutationKind,
    val requestId: Long,
    val questionId: String? = null,
)

data class ProfileQuestionFeedback(
    val destination: ProfileQuestionDestination,
    val questionId: String?,
    val message: String,
)

sealed interface LegalResumeContext {
    data object PostSession : LegalResumeContext
    data object PostReactivation : LegalResumeContext

    data class ExistingState(
        val state: RealsRootUiState,
    ) : LegalResumeContext {
        init {
            require(state !is RealsRootUiState.LegalRequirements) {
                "Legal resume state cannot itself be LegalRequirements."
            }
        }
    }
}

data class LegalRequirementUiItem(
    val type: LegalDocumentType,
    val version: String,
    val url: String,
    val requiredAction: LegalDocumentAction,
    val recordedAction: LegalDocumentAction?,
    val actedAt: String?,
    val satisfied: Boolean,
) {
    val key: String get() = "${type.rawValue}:$version"
}

enum class OutgoingMessageDeliveryState {
    Sending,
    Failed,
}

enum class OptimisticOutgoingMessageType {
    Text,
    Audio,
}

data class OptimisticOutgoingMessage(
    val localId: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val createdAtMillis: Long,
    val deliveryState: OutgoingMessageDeliveryState,
    val messageType: OptimisticOutgoingMessageType = OptimisticOutgoingMessageType.Text,
    val audioDurationMillis: Long? = null,
)

internal fun newOptimisticOutgoingMessage(
    chatId: String,
    senderId: String,
    content: String,
    localId: String = optimisticMessageLocalId(),
    createdAtMillis: Long = System.currentTimeMillis(),
): OptimisticOutgoingMessage = OptimisticOutgoingMessage(
    localId = localId,
    chatId = chatId,
    senderId = senderId,
    content = content,
    createdAtMillis = createdAtMillis,
    deliveryState = OutgoingMessageDeliveryState.Sending,
)

internal fun newOptimisticOutgoingAudioMessage(
    chatId: String,
    senderId: String,
    clientMessageId: String,
    durationMillis: Long,
    createdAtMillis: Long = System.currentTimeMillis(),
): OptimisticOutgoingMessage = OptimisticOutgoingMessage(
    localId = clientMessageId,
    chatId = chatId,
    senderId = senderId,
    content = "",
    createdAtMillis = createdAtMillis,
    deliveryState = OutgoingMessageDeliveryState.Sending,
    messageType = OptimisticOutgoingMessageType.Audio,
    audioDurationMillis = durationMillis,
)

private fun optimisticMessageLocalId(): String = "local-${System.currentTimeMillis()}"

internal fun List<OptimisticOutgoingMessage>.withoutOptimisticMessage(
    localId: String,
): List<OptimisticOutgoingMessage> = filterNot { it.localId == localId }

internal fun List<OptimisticOutgoingMessage>.markOptimisticMessageFailed(
    localId: String,
): List<OptimisticOutgoingMessage> = map { message ->
    if (message.localId == localId) {
        message.copy(deliveryState = OutgoingMessageDeliveryState.Failed)
    } else {
        message
    }
}

fun RealsRootUiState.Ready.clearProfileFeedback(): RealsRootUiState.Ready = copy(
    profileOp = profileOp.copy(
        profileUpdateError = null,
        profileUpdateMessage = null,
        matchFiltersError = null,
        matchFiltersMessage = null,
        profileActivationError = null,
        emailVerificationMessage = null,
        emailVerificationError = null,
    ),
    photos = photos.copy(
        profilePhotosError = null,
        photoReorderError = null,
        photoReorderMessage = null,
        photoActionError = null,
        photoActionMessage = null,
    ),
)

fun RealsRootUiState.clearLegalActionRequiredForResume(): RealsRootUiState = when (this) {
    is RealsRootUiState.Ready -> copy(
        profileOp = profileOp.copy(
            profileCreateError = profileOp.profileCreateError.takeUnless { it.isLegalActionRequired() },
            countriesError = profileOp.countriesError.takeUnless { it.isLegalActionRequired() },
            profileUpdateError = profileOp.profileUpdateError.takeUnless { it.isLegalActionRequired() },
            matchFiltersError = profileOp.matchFiltersError.takeUnless { it.isLegalActionRequired() },
            profileActivationError = profileOp.profileActivationError.takeUnless { it.isLegalActionRequired() },
        ),
        photos = photos.copy(
            photoReorderError = photos.photoReorderError.takeUnless { it.isLegalActionRequired() },
            photoActionError = photos.photoActionError.takeUnless { it.isLegalActionRequired() },
        ),
        home = home.copy(
            homeLoading = if (home.homeError.isLegalActionRequired()) false else home.homeLoading,
            homeError = home.homeError.takeUnless { it.isLegalActionRequired() },
            matchmakingBlockedReason =
                home.matchmakingBlockedReason.takeUnless { it.isLegalActionRequired() },
            matchmakingSearchPhase = if (home.homeError.isLegalActionRequired()) {
                MatchmakingSearchUiPhase.Idle
            } else {
                home.matchmakingSearchPhase
            },
        ),
        affinityQuestionnaire = affinityQuestionnaire.copy(
            loading = if (affinityQuestionnaire.error.isLegalActionRequired()) {
                false
            } else {
                affinityQuestionnaire.loading
            },
            refreshing = if (affinityQuestionnaire.error.isLegalActionRequired()) {
                false
            } else {
                affinityQuestionnaire.refreshing
            },
            mutation = if (affinityQuestionnaire.mutationError.isLegalActionRequired()) {
                null
            } else {
                affinityQuestionnaire.mutation
            },
            error = affinityQuestionnaire.error.takeUnless { it.isLegalActionRequired() },
            mutationError = affinityQuestionnaire.mutationError.takeUnless { it.isLegalActionRequired() },
            mutationFeedbackQuestionId = if (affinityQuestionnaire.mutationError.isLegalActionRequired()) {
                null
            } else {
                affinityQuestionnaire.mutationFeedbackQuestionId
            },
        ),
        profileQuestions = profileQuestions.copy(
            loading = if (profileQuestions.error.isLegalActionRequired()) {
                false
            } else {
                profileQuestions.loading
            },
            refreshing = if (profileQuestions.error.isLegalActionRequired()) {
                false
            } else {
                profileQuestions.refreshing
            },
            mutation = if (profileQuestions.mutationError.isLegalActionRequired()) {
                null
            } else {
                profileQuestions.mutation
            },
            error = profileQuestions.error.takeUnless { it.isLegalActionRequired() },
            mutationError = profileQuestions.mutationError.takeUnless { it.isLegalActionRequired() },
            feedback = profileQuestions.feedback.takeUnless {
                profileQuestions.mutationError.isLegalActionRequired()
            },
        ),
    )

    is RealsRootUiState.FirstChat -> copy(error = error.takeUnless { it.isLegalActionRequired() })
    is RealsRootUiState.SecondChat -> copy(error = error.takeUnless { it.isLegalActionRequired() })
    is RealsRootUiState.VisualApproval -> copy(error = error.takeUnless { it.isLegalActionRequired() })
    is RealsRootUiState.Scheduling -> copy(error = error.takeUnless { it.isLegalActionRequired() })
    else -> this
}

private fun ApiError?.isLegalActionRequired(): Boolean = this?.isLegalActionRequired() == true

fun RealsRootUiState.canHandleSystemBack(): Boolean = when (this) {
    is RealsRootUiState.Ready ->
        affinityQuestionnaire.open ||
                profileQuestions.open ||
                (
                    editingActiveProfile &&
                        session.profileSnapshot is ProfileSnapshot.Found &&
                        !photos.reorderingPhotos
                    ) ||
                isHomePendingSurfaceVisible()

    is RealsRootUiState.SecondChat -> canReturnHomeNow() &&
            !sending && !audioUpload.uploading && !actionLoading && !manualBlock.loading

    is RealsRootUiState.VisualApproval ->
        !deciding && !writingMessage && !readingPartnerMessage && !manualBlock.loading

    is RealsRootUiState.Scheduling -> !submitting && !manualBlock.loading
    is RealsRootUiState.PartnerProfile -> !manualBlock.loading
    is RealsRootUiState.PendingEngagement -> true
    is RealsRootUiState.ActivationComplete -> true
    is RealsRootUiState.FirstChat -> canRecoverFirstChatToHome()

    RealsRootUiState.Checking,
    is RealsRootUiState.MissingFirebase,
    is RealsRootUiState.Login,
    is RealsRootUiState.LoadingSession,
    is RealsRootUiState.AccountDeletionScheduled,
    is RealsRootUiState.AccountDeletionPending,
    is RealsRootUiState.LegalRequirements,
    is RealsRootUiState.Failure -> false
}

fun RealsRootUiState.FirstChat.canRecoverFirstChatToHome(): Boolean =
    !loading &&
            chat == null &&
            !refreshing &&
            !sending &&
            !audioUpload.uploading &&
            !actionLoading &&
            !guidanceActionLoading &&
            !manualBlock.loading

fun RealsRootUiState.SecondChat.isJoinedActiveSecondChat(): Boolean =
    lifecycle.timingPresentation().genuinelyActive

fun RealsRootUiState.SecondChat.canReturnHomeNow(
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (!lifecycle.timingPresentation(nowMillis).genuinelyActive) return true
    return lifecycle.status?.canReturnHomeAfterPartnerEntryCutoff(
        statusReceivedAtMillis = lifecycle.statusReceivedAtMillis,
        nowMillis = nowMillis,
    ) == true
}
