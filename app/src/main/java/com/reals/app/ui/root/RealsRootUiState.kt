package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.matchmaking.HomeScreenModel

sealed interface RealsRootUiState {
    data object Checking : RealsRootUiState

    data class MissingFirebase(val message: String) : RealsRootUiState

    data class Login(
        val loading: Boolean = false,
        val error: String? = null,
        val passwordResetLoading: Boolean = false,
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
        val error: ApiError? = null,
    ) : RealsRootUiState

    data class Ready(
        val session: ProvisionedSession,
        val profileOp: ProfileManagementState = ProfileManagementState(),
        val photos: PhotoManagementUiState = PhotoManagementUiState(),
        val home: HomeUiState = HomeUiState(),
        val account: AccountUiState = AccountUiState(),
        val editingActiveProfile: Boolean = false,
    ) : RealsRootUiState {
        val creatingProfile: Boolean get() = profileOp.creatingProfile
        val profileCreateError: ApiError? get() = profileOp.profileCreateError
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
        val resendEmailVerificationAvailableAtMillis: Long? get() =
            profileOp.resendEmailVerificationAvailableAtMillis
        val checkEmailVerificationAvailableAtMillis: Long? get() =
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
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val sending: Boolean = false,
        val actionLoading: Boolean = false,
        val actionLoadingLabel: String? = null,
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
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val sending: Boolean = false,
        val actionLoading: Boolean = false,
        val actionLoadingLabel: String? = null,
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class VisualApproval(
        val session: ProvisionedSession,
        val matchId: String,
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
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class Scheduling(
        val session: ProvisionedSession,
        val connectionId: String,
        val matchId: String,
        val partnerName: String? = null,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val submitting: Boolean = false,
        val submittingLabel: String? = null,
        val negotiation: SchedulingNegotiation? = null,
        val proposals: List<SchedulingProposal> = emptyList(),
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState

    data class PartnerProfile(
        val session: ProvisionedSession,
        val matchId: String,
        val profile: VisualProfile? = null,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
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

data class ProfileManagementState(
    val creatingProfile: Boolean = false,
    val profileCreateError: ApiError? = null,
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
    val homeLoading: Boolean = false,
    val homeError: ApiError? = null,
    val homeMessage: String? = null,
    val matchmakingBlockedReason: ApiError? = null,
    val matchmakingSearchPhase: MatchmakingSearchUiPhase = MatchmakingSearchUiPhase.Idle,
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

enum class OutgoingMessageDeliveryState {
    Sending,
    Failed,
}

data class OptimisticOutgoingMessage(
    val localId: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val createdAtMillis: Long,
    val deliveryState: OutgoingMessageDeliveryState,
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

fun RealsRootUiState.canHandleSystemBack(): Boolean = when (this) {
    is RealsRootUiState.Ready -> {
        val profile = (session.profileSnapshot as? ProfileSnapshot.Found)?.profile
        editingActiveProfile && profile?.status == ProfileStatus.Active && !photos.reorderingPhotos
    }

    is RealsRootUiState.SecondChat -> !sending && !actionLoading
    is RealsRootUiState.VisualApproval -> !deciding && !writingMessage && !readingPartnerMessage
    is RealsRootUiState.Scheduling -> !submitting
    is RealsRootUiState.PartnerProfile -> true
    is RealsRootUiState.PendingEngagement -> true
    is RealsRootUiState.ActivationComplete -> true

    RealsRootUiState.Checking,
    is RealsRootUiState.MissingFirebase,
    is RealsRootUiState.Login,
    is RealsRootUiState.LoadingSession,
    is RealsRootUiState.AccountDeletionScheduled,
    is RealsRootUiState.AccountDeletionPending,
    is RealsRootUiState.FirstChat,
    is RealsRootUiState.Failure -> false
}
