package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase

internal class PartnerProfileCoordinator(
    private val getVisualProfile: GetVisualProfileUseCase,
    private val getPartnerPersonalMessage: GetPartnerPersonalMessageUseCase,
) {
    suspend fun load(
        session: ProvisionedSession,
        matchId: String,
        onPending: (RealsRootUiState.PartnerProfile) -> Unit,
    ): RealsRootUiState.PartnerProfile {
        val loadingState = RealsRootUiState.PartnerProfile(
            session = session,
            matchId = matchId,
            loading = true,
        )
        onPending(loadingState)
        return loadFrom(loadingState, onPending)
    }

    suspend fun refresh(
        current: RealsRootUiState.PartnerProfile,
        onPending: (RealsRootUiState.PartnerProfile) -> Unit,
    ): RealsRootUiState.PartnerProfile {
        val refreshingState = current.copy(refreshing = true, error = null)
        onPending(refreshingState)
        return loadFrom(refreshingState, onPending)
    }

    suspend fun retryPartnerMessage(
        current: RealsRootUiState.PartnerProfile,
        onPending: (RealsRootUiState.PartnerProfile) -> Unit,
    ): RealsRootUiState.PartnerProfile {
        if (
            current.matchId.isBlank() ||
            current.loading ||
            current.refreshing ||
            current.loadingPartnerMessage
        ) {
            return current
        }
        val profile = current.profile ?: return current
        if (!profile.partnerPersonalMessageSubmitted) return current

        val pending = current.copy(
            loadingPartnerMessage = true,
            partnerMessageError = null,
            error = null,
        )
        onPending(pending)
        return loadPartnerMessage(pending)
    }

    private suspend fun loadFrom(
        loadingState: RealsRootUiState.PartnerProfile,
        onPending: (RealsRootUiState.PartnerProfile) -> Unit,
    ): RealsRootUiState.PartnerProfile =
        when (val result = getVisualProfile(loadingState.matchId)) {
            is ApiResult.Success -> loadingState.withLoadedProfile(result.value, onPending)

            is ApiResult.Failure -> loadingState.copy(
                loading = false,
                refreshing = false,
                error = result.error,
            )
        }

    private suspend fun RealsRootUiState.PartnerProfile.withLoadedProfile(
        loadedProfile: VisualProfile,
        onPending: (RealsRootUiState.PartnerProfile) -> Unit,
    ): RealsRootUiState.PartnerProfile {
        if (!loadedProfile.partnerPersonalMessageSubmitted) {
            val profilePublishedState = copy(
                profile = loadedProfile,
                partnerMessage = null,
                partnerMessageLoaded = false,
                loadingPartnerMessage = false,
                partnerMessageError = null,
                loading = false,
                refreshing = false,
                error = null,
            )
            onPending(profilePublishedState)
            return profilePublishedState
        }

        val profilePublishedState = copy(
            profile = loadedProfile,
            partnerMessage = partnerMessage.takeIf { partnerMessageLoaded },
            partnerMessageLoaded = partnerMessageLoaded,
            loadingPartnerMessage = true,
            partnerMessageError = null,
            loading = false,
            refreshing = false,
            error = null,
        )
        onPending(profilePublishedState)
        return loadPartnerMessage(profilePublishedState)
    }

    private suspend fun loadPartnerMessage(
        pending: RealsRootUiState.PartnerProfile,
    ): RealsRootUiState.PartnerProfile =
        when (val messageResult = getPartnerPersonalMessage(pending.matchId)) {
            is ApiResult.Success -> pending.copy(
                partnerMessage = messageResult.value,
                partnerMessageLoaded = true,
                loadingPartnerMessage = false,
                partnerMessageError = null,
                loading = false,
                refreshing = false,
                error = null,
            )

            is ApiResult.Failure -> pending.copy(
                partnerMessage = pending.partnerMessage,
                partnerMessageLoaded = pending.partnerMessageLoaded,
                loadingPartnerMessage = false,
                partnerMessageError = messageResult.error,
                loading = false,
                refreshing = false,
                error = null,
            )
        }
}
