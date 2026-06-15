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
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.VisualProfile

sealed interface RealsRootUiState {
    data object Checking : RealsRootUiState

    data class MissingFirebase(val message: String) : RealsRootUiState

    data class Login(val loading: Boolean = false, val error: String? = null) : RealsRootUiState

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
        val profile: ProfileUiState = ProfileUiState(),
        val photos: PhotoManagementUiState = PhotoManagementUiState(),
        val home: HomeUiState = HomeUiState(),
        val account: AccountUiState = AccountUiState(),
        val editingActiveProfile: Boolean = false,
    ) : RealsRootUiState {
        val creatingProfile: Boolean get() = profile.creatingProfile
        val profileCreateError: ApiError? get() = profile.profileCreateError
        val updatingProfile: Boolean get() = profile.updatingProfile
        val profileUpdateError: ApiError? get() = profile.profileUpdateError
        val profileUpdateMessage: String? get() = profile.profileUpdateMessage
        val updatingMatchFilters: Boolean get() = profile.updatingMatchFilters
        val matchFiltersError: ApiError? get() = profile.matchFiltersError
        val matchFiltersMessage: String? get() = profile.matchFiltersMessage
        val activatingProfile: Boolean get() = profile.activatingProfile
        val profileActivationError: ApiError? get() = profile.profileActivationError
        val loadingPhotos: Boolean get() = photos.loadingPhotos
        val profilePhotos: List<ProfilePhoto> get() = photos.profilePhotos
        val profilePhotosError: ApiError? get() = photos.profilePhotosError
        val addingPhoto: Boolean get() = photos.addingPhoto
        val photoActionError: ApiError? get() = photos.photoActionError
        val photoActionMessage: String? get() = photos.photoActionMessage
        val homeState: HomeState? get() = home.homeState
        val homeLoading: Boolean get() = home.homeLoading
        val homeError: ApiError? get() = home.homeError
        val homeMessage: String? get() = home.homeMessage
        val matchmakingBlockedReason: ApiError? get() = home.matchmakingBlockedReason
        val deletingAccount: Boolean get() = account.deletingAccount
        val accountDeleteError: ApiError? get() = account.accountDeleteError
    }

    data class FirstChat(
        val session: ProvisionedSession,
        val matchId: String,
        val chatId: String? = null,
        val match: Match? = null,
        val chat: Chat? = null,
        val messages: List<ChatMessage> = emptyList(),
        val exitRequests: List<ChatExitRequest> = emptyList(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val sending: Boolean = false,
        val actionLoading: Boolean = false,
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
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val writingMessage: Boolean = false,
        val deciding: Boolean = false,
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

data class ProfileUiState(
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
)

data class PhotoManagementUiState(
    val loadingPhotos: Boolean = false,
    val profilePhotos: List<ProfilePhoto> = emptyList(),
    val profilePhotosError: ApiError? = null,
    val addingPhoto: Boolean = false,
    val photoActionError: ApiError? = null,
    val photoActionMessage: String? = null,
)

data class HomeUiState(
    val homeState: HomeState? = null,
    val homeLoading: Boolean = false,
    val homeError: ApiError? = null,
    val homeMessage: String? = null,
    val matchmakingBlockedReason: ApiError? = null,
) {
    val matchmakingBlockedByLimit: Boolean
        get() = matchmakingBlockedReason is ApiError.Backend &&
            matchmakingBlockedReason.code == "ACTIVE_MATCH_LIMIT_REACHED"
}

data class AccountUiState(
    val deletingAccount: Boolean = false,
    val accountDeleteError: ApiError? = null,
)

fun RealsRootUiState.Ready.clearProfileFeedback(): RealsRootUiState.Ready = copy(
    profile = profile.copy(
        profileUpdateError = null,
        profileUpdateMessage = null,
        matchFiltersError = null,
        matchFiltersMessage = null,
        profileActivationError = null,
    ),
    photos = photos.copy(
        profilePhotosError = null,
        photoActionError = null,
        photoActionMessage = null,
    ),
)
