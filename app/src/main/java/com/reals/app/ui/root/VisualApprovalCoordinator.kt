package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.VisualDecision

internal class VisualApprovalCoordinator(
    private val dependencies: VisualApprovalFeatureDependencies,
) {
    suspend fun load(
        session: ProvisionedSession,
        matchId: String,
        initialMatch: Match?,
        previous: RealsRootUiState.VisualApproval?,
        locallyHidden: Boolean,
    ): VisualApprovalLoadResult {
        val loadingState = previous ?: RealsRootUiState.VisualApproval(
            session = session,
            matchId = matchId,
            match = initialMatch,
            loading = true,
        )

        if (locallyHidden) {
            return VisualApprovalLoadResult.RouteHome(message = null)
        }

        val matchResult = dependencies.getMatch(matchId)
        val match = (matchResult as? ApiResult.Success)?.value ?: initialMatch
        if (match != null &&
            match.state != com.reals.app.domain.model.MatchState.VisualPhase &&
            match.state !is com.reals.app.domain.model.MatchState.Unknown
        ) {
            return VisualApprovalLoadResult.RouteHome(
                message = "La revision visual cambio de estado. Actualizamos tu Home.",
            )
        }

        val profileResult = dependencies.getVisualProfile(matchId)
        val profile = (profileResult as? ApiResult.Success)?.value
        val partnerMessageResult = dependencies.getPartnerPersonalMessage(matchId)
        return VisualApprovalLoadResult.Show(
            loadingState.copy(
                match = match,
                profile = profile ?: loadingState.profile,
                partnerMessage = (partnerMessageResult as? ApiResult.Success)?.value ?: loadingState.partnerMessage,
                partnerMessageLoaded = partnerMessageResult is ApiResult.Success || loadingState.partnerMessageLoaded,
                myPersonalMessageSubmitted = profile?.myPersonalMessageSubmitted
                    ?: loadingState.myPersonalMessageSubmitted,
                loading = false,
                refreshing = false,
                error = (matchResult as? ApiResult.Failure)?.error
                    ?: (profileResult as? ApiResult.Failure)?.error
                    ?: (partnerMessageResult as? ApiResult.Failure)?.error,
            )
        )
    }

    suspend fun savePersonalMessage(
        current: RealsRootUiState.VisualApproval,
        cleanMessage: String,
    ): RealsRootUiState.VisualApproval {
        val pending = current.copy(writingMessage = true, error = null, message = null)
        return when (val result = dependencies.putMyPersonalMessage(current.matchId, cleanMessage)) {
            is ApiResult.Success -> pending.copy(
                writingMessage = false,
                myPersonalMessageSubmitted = true,
                message = "Guardamos tu mensaje personal.",
            )

            is ApiResult.Failure -> if (result.error.isDomainConflict()) {
                pending.copy(
                    writingMessage = false,
                    myPersonalMessageSubmitted = true,
                    message = "Ya habias guardado tu mensaje personal.",
                )
            } else {
                pending.copy(
                    writingMessage = false,
                    error = result.error,
                )
            }
        }
    }

    suspend fun submitDecision(matchId: String, decision: VisualDecision): ApiResult<Match> =
        dependencies.submitVisualDecision(matchId, decision)
}

private fun ApiError.isDomainConflict(): Boolean =
    this is ApiError.Backend && code == "DOMAIN_CONFLICT"

internal sealed interface VisualApprovalLoadResult {
    data class Show(val state: RealsRootUiState.VisualApproval) : VisualApprovalLoadResult
    data class RouteHome(val message: String?) : VisualApprovalLoadResult
}
