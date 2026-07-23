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
        return loadFrom(loadingState)
    }

    suspend fun refresh(
        current: RealsRootUiState.PartnerProfile,
        onPending: (RealsRootUiState.PartnerProfile) -> Unit,
    ): RealsRootUiState.PartnerProfile {
        val refreshingState = current.copy(refreshing = true, error = null)
        onPending(refreshingState)
        return loadFrom(refreshingState)
    }

    private suspend fun loadFrom(
        loadingState: RealsRootUiState.PartnerProfile,
    ): RealsRootUiState.PartnerProfile =
        when (val result = getVisualProfile(loadingState.matchId)) {
            is ApiResult.Success -> loadingState.withLoadedProfile(result.value)

            is ApiResult.Failure -> loadingState.copy(
                loading = false,
                refreshing = false,
                error = result.error,
            )
        }

    private suspend fun RealsRootUiState.PartnerProfile.withLoadedProfile(
        loadedProfile: VisualProfile,
    ): RealsRootUiState.PartnerProfile {
        if (!loadedProfile.partnerPersonalMessageSubmitted) {
            return copy(
                profile = loadedProfile,
                partnerMessage = null,
                partnerMessageLoaded = false,
                partnerMessageError = null,
                loading = false,
                refreshing = false,
                error = null,
            )
        }

        return when (val messageResult = getPartnerPersonalMessage(matchId)) {
            is ApiResult.Success -> copy(
                profile = loadedProfile,
                partnerMessage = messageResult.value,
                partnerMessageLoaded = true,
                partnerMessageError = null,
                loading = false,
                refreshing = false,
                error = null,
            )

            is ApiResult.Failure -> copy(
                profile = loadedProfile,
                partnerMessage = partnerMessage,
                partnerMessageLoaded = partnerMessageLoaded,
                partnerMessageError = messageResult.error,
                loading = false,
                refreshing = false,
                error = null,
            )
        }
    }
}
