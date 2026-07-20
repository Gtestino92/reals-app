package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.security.TextSafety
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.VisualDecision
import com.reals.app.domain.model.VisualProfile

internal class VisualApprovalCoordinator(
    private val dependencies: VisualApprovalFeatureDependencies,
) {
    suspend fun open(
        session: ProvisionedSession,
        matchId: String,
        locallyHidden: Boolean,
        onPending: (RealsRootUiState.VisualApproval) -> Unit,
    ): VisualApprovalFlowResult {
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return VisualApprovalFlowResult.Ignore
        if (locallyHidden) {
            return VisualApprovalFlowResult.ReturnHome(session = session)
        }

        return loadFlow(
            session = session,
            matchId = cleanMatchId,
            initialMatch = null,
            previous = null,
            locallyHidden = false,
            onPending = onPending,
        )
    }

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
            match.state != MatchState.VisualPhase &&
            match.state !is MatchState.Unknown
        ) {
            return VisualApprovalLoadResult.RouteHome(
                message = "La revisión visual cambió de estado. Actualizamos tu Home.",
            )
        }

        val profileResult = dependencies.getVisualProfile(matchId)
        if ((profileResult as? ApiResult.Failure)?.error.isVisualContentNotAvailable()) {
            return VisualApprovalLoadResult.RouteHome(
                message = "El contenido visual ya no est\u00e1 disponible. Actualizamos tu Home.",
            )
        }
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

    suspend fun refresh(
        current: RealsRootUiState.VisualApproval,
        locallyHidden: Boolean,
        onPending: (RealsRootUiState.VisualApproval) -> Unit,
    ): VisualApprovalFlowResult {
        if (
            current.loading ||
            current.refreshing ||
            current.readingPartnerMessage ||
            current.writingMessage ||
            current.deciding
        ) {
            return VisualApprovalFlowResult.Ignore
        }

        return loadFlow(
            session = current.session,
            matchId = current.matchId,
            initialMatch = current.match,
            previous = current.copy(refreshing = true, error = null, message = null),
            locallyHidden = locallyHidden,
            onPending = onPending,
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

    suspend fun readPartnerPersonalMessageAction(
        current: RealsRootUiState.VisualApproval,
        onPending: (RealsRootUiState.VisualApproval) -> Unit,
    ): VisualApprovalFlowResult {
        if (
            current.loading ||
            current.refreshing ||
            current.readingPartnerMessage ||
            current.writingMessage ||
            current.deciding ||
            current.partnerMessageLoaded
        ) {
            return VisualApprovalFlowResult.Ignore
        }

        val profile = current.profile ?: return VisualApprovalFlowResult.Ignore
        if (!profile.partnerPersonalMessageSubmitted) return VisualApprovalFlowResult.Ignore

        onPending(
            current.copy(
                readingPartnerMessage = true,
                partnerMessageError = null,
                error = null,
                message = null,
            )
        )
        val updated = readPartnerPersonalMessage(current)
        return if (updated.error.isVisualContentNotAvailable()) {
            VisualApprovalFlowResult.ReloadHome(
                session = current.session,
                message = "El contenido visual ya no est\u00e1 disponible. Actualizamos tu Home.",
                hideVisualMatchId = current.matchId,
                autoNavigateEngagements = false,
            )
        } else {
            VisualApprovalFlowResult.Show(updated)
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

    suspend fun savePersonalMessageAction(
        current: RealsRootUiState.VisualApproval,
        message: String,
        onPending: (RealsRootUiState.VisualApproval) -> Unit,
    ): VisualApprovalFlowResult {
        if (current.readingPartnerMessage || current.writingMessage || current.deciding) {
            return VisualApprovalFlowResult.Ignore
        }
        if (current.myPersonalMessageSubmitted) {
            return VisualApprovalFlowResult.Show(
                current.copy(
                    writingMessage = false,
                    error = null,
                    message = "Ya habias guardado tu mensaje personal.",
                )
            )
        }

        val cleanMessage = TextSafety.normalizeMultiline(message, maxLength = 280)
        if (cleanMessage.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanMessage)) {
            return VisualApprovalFlowResult.Show(
                current.copy(
                    error = ApiError.Unexpected("El mensaje personal no es válido."),
                    message = null,
                )
            )
        }

        onPending(current.copy(writingMessage = true, error = null, message = null))
        val updated = savePersonalMessage(current, cleanMessage)
        return if (updated.error.isVisualContentNotAvailable()) {
            VisualApprovalFlowResult.ReloadHome(
                session = current.session,
                message = "El contenido visual ya no est\u00e1 disponible. Actualizamos tu Home.",
                hideVisualMatchId = current.matchId,
                autoNavigateEngagements = false,
            )
        } else {
            VisualApprovalFlowResult.Show(updated)
        }
    }

    suspend fun submitDecision(
        current: RealsRootUiState.VisualApproval,
        decision: VisualDecision,
        onPending: (RealsRootUiState.VisualApproval) -> Unit,
    ): VisualApprovalFlowResult {
        if (
            current.deciding ||
            current.writingMessage ||
            current.readingPartnerMessage ||
            current.profile?.decisionRequiresPartnerPersonalMessageRead == true
        ) {
            return VisualApprovalFlowResult.Ignore
        }

        val pending = current.copy(
            deciding = true,
            decidingLabel = if (decision == VisualDecision.Approved) {
                "Aprobando..."
            } else {
                "Rechazando..."
            },
            error = null,
            message = null,
        )
        onPending(pending)

        return when (val result = dependencies.submitVisualDecision(current.matchId, decision)) {
            is ApiResult.Success -> {
                when (result.value.state) {
                    MatchState.VisualPhase -> VisualApprovalFlowResult.ReloadHome(
                        session = current.session,
                        hideVisualMatchId = current.matchId,
                        autoNavigateEngagements = false,
                    )

                    MatchState.VisualApproved -> VisualApprovalFlowResult.ReloadHome(
                        session = current.session,
                        hideVisualMatchId = current.matchId,
                        autoNavigateEngagements = false,
                    )

                    MatchState.VisualRejected,
                    MatchState.ChatRejected,
                    MatchState.Expired -> VisualApprovalFlowResult.ReloadHome(
                        session = current.session,
                        hideVisualMatchId = current.matchId,
                        autoNavigateEngagements = false,
                    )

                    MatchState.ChatActive,
                    is MatchState.Unknown -> VisualApprovalFlowResult.Show(
                        state = pending.copy(
                            match = result.value,
                            deciding = false,
                            decidingLabel = null,
                            message = "Guardamos tu decisión.",
                        ),
                        hideVisualMatchId = current.matchId,
                    )
                }
            }

            is ApiResult.Failure -> if (result.error.isVisualContentNotAvailable()) {
                VisualApprovalFlowResult.ReloadHome(
                    session = current.session,
                    message = "El contenido visual ya no est\u00e1 disponible. Actualizamos tu Home.",
                    hideVisualMatchId = current.matchId,
                    autoNavigateEngagements = false,
                )
            } else {
                VisualApprovalFlowResult.Show(
                    pending.copy(
                        deciding = false,
                        decidingLabel = null,
                        error = result.error,
                    )
                )
            }
        }
    }

    private suspend fun loadFlow(
        session: ProvisionedSession,
        matchId: String,
        initialMatch: Match?,
        previous: RealsRootUiState.VisualApproval?,
        locallyHidden: Boolean,
        onPending: (RealsRootUiState.VisualApproval) -> Unit,
    ): VisualApprovalFlowResult {
        val loadingState = previous ?: RealsRootUiState.VisualApproval(
            session = session,
            matchId = matchId,
            match = initialMatch,
            loading = true,
        )
        onPending(loadingState)

        return when (
            val result = load(
                session = session,
                matchId = matchId,
                initialMatch = initialMatch,
                previous = previous,
                locallyHidden = locallyHidden,
            )
        ) {
            is VisualApprovalLoadResult.Show -> VisualApprovalFlowResult.Show(result.state)
            is VisualApprovalLoadResult.RouteHome -> VisualApprovalFlowResult.ReloadHome(
                session = session,
                message = result.message,
                autoNavigateEngagements = false,
            )
        }
    }
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

private fun ApiError?.isVisualContentNotAvailable(): Boolean =
    this is ApiError.Backend && backendErrorCode == BackendErrorCode.VisualContentNotAvailable

internal sealed interface VisualApprovalLoadResult {
    data class Show(val state: RealsRootUiState.VisualApproval) : VisualApprovalLoadResult
    data class RouteHome(val message: String?) : VisualApprovalLoadResult
}

internal sealed interface VisualApprovalFlowResult {
    data object Ignore : VisualApprovalFlowResult

    data class Show(
        val state: RealsRootUiState.VisualApproval,
        val hideVisualMatchId: String? = null,
    ) : VisualApprovalFlowResult

    data class ReturnHome(
        val session: ProvisionedSession,
        val message: String? = null,
        val hideVisualMatchId: String? = null,
        val autoNavigateEngagements: Boolean = false,
    ) : VisualApprovalFlowResult

    data class ReloadHome(
        val session: ProvisionedSession,
        val message: String? = null,
        val hideVisualMatchId: String? = null,
        val autoNavigateEngagements: Boolean = false,
    ) : VisualApprovalFlowResult
}
