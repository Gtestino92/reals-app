package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.VisualDecision
import com.reals.app.domain.model.VisualProfile

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
        val latestProfile = profile ?: loadingState.profile
        val partnerMessageResult = if (latestProfile.shouldLoadPartnerMessageAutomatically()) {
            dependencies.getPartnerPersonalMessage(matchId)
        } else {
            null
        }
        val visibleProfile = if (partnerMessageResult is ApiResult.Success) {
            latestProfile?.markPartnerPersonalMessageReadLocally()
        } else {
            latestProfile
        }
        val partnerMessageFailure = (partnerMessageResult as? ApiResult.Failure)?.error
        return VisualApprovalLoadResult.Show(
            loadingState.copy(
                match = match,
                profile = visibleProfile,
                partnerMessage = (partnerMessageResult as? ApiResult.Success)?.value
                    ?: partnerMessageForProfile(visibleProfile, loadingState),
                partnerMessageLoaded = partnerMessageResult is ApiResult.Success ||
                    partnerMessageLoadedForProfile(visibleProfile, loadingState),
                readingPartnerMessage = false,
                partnerMessageError = partnerMessageFailure,
                myPersonalMessageSubmitted = visibleProfile?.myPersonalMessageSubmitted
                    ?: loadingState.myPersonalMessageSubmitted,
                loading = false,
                refreshing = false,
                error = (matchResult as? ApiResult.Failure)?.error
                    ?: (profileResult as? ApiResult.Failure)?.error
                    ?: partnerMessageFailure,
            )
        )
    }

    suspend fun readPartnerPersonalMessage(
        current: RealsRootUiState.VisualApproval,
    ): RealsRootUiState.VisualApproval {
        val pending = current.copy(
            readingPartnerMessage = true,
            partnerMessageError = null,
            error = null,
            message = null,
        )

        return when (val result = dependencies.getPartnerPersonalMessage(current.matchId)) {
            is ApiResult.Success -> pending.copy(
                profile = pending.profile?.markPartnerPersonalMessageReadLocally(),
                partnerMessage = result.value,
                partnerMessageLoaded = true,
                readingPartnerMessage = false,
                partnerMessageError = null,
            )

            is ApiResult.Failure -> pending.copy(
                readingPartnerMessage = false,
                partnerMessageError = result.error,
                error = result.error,
            )
        }
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

private fun VisualProfile.markPartnerPersonalMessageReadLocally(): VisualProfile =
    copy(
        partnerPersonalMessageRead = true,
        decisionRequiresPartnerPersonalMessageRead = false,
    )

private fun VisualProfile?.shouldLoadPartnerMessageAutomatically(): Boolean {
    if (this == null) return false
    return partnerPersonalMessageSubmitted &&
        partnerPersonalMessageRead &&
        !decisionRequiresPartnerPersonalMessageRead
}

private fun partnerMessageForProfile(
    profile: VisualProfile?,
    loadingState: RealsRootUiState.VisualApproval,
): String? {
    if (profile?.partnerPersonalMessageSubmitted == false) return null
    if (profile?.decisionRequiresPartnerPersonalMessageRead == true) return null
    return loadingState.partnerMessage
}

private fun partnerMessageLoadedForProfile(
    profile: VisualProfile?,
    loadingState: RealsRootUiState.VisualApproval,
): Boolean {
    if (profile?.partnerPersonalMessageSubmitted == false) return false
    if (profile?.decisionRequiresPartnerPersonalMessageRead == true) return false
    return loadingState.partnerMessageLoaded
}

private fun ApiError.isDomainConflict(): Boolean =
    this is ApiError.Backend && backendErrorCode == BackendErrorCode.DomainConflict

internal sealed interface VisualApprovalLoadResult {
    data class Show(val state: RealsRootUiState.VisualApproval) : VisualApprovalLoadResult
    data class RouteHome(val message: String?) : VisualApprovalLoadResult
}
