package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.di.SchedulingFeatureDependencies
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SchedulingNegotiation

internal class SchedulingCoordinator(
    private val dependencies: SchedulingFeatureDependencies,
) {
    suspend fun load(
        session: ProvisionedSession,
        connectionId: String,
        matchId: String,
        partnerName: String?,
    ): RealsRootUiState.Scheduling {
        val loading = RealsRootUiState.Scheduling(
            session = session,
            connectionId = connectionId,
            matchId = matchId,
            partnerName = partnerName,
            loading = true,
        )
        return refresh(loading, silent = false)
    }

    suspend fun refresh(
        current: RealsRootUiState.Scheduling,
        silent: Boolean,
    ): RealsRootUiState.Scheduling {
        val pending = current.copy(
            loading = current.negotiation == null && !silent,
            refreshing = current.negotiation != null,
            error = if (silent) current.error else null,
            message = if (silent) current.message else null,
        )

        val negotiationResult = dependencies.getNegotiation(current.connectionId)
        if (negotiationResult is ApiResult.Failure) {
            return pending.copy(
                loading = false,
                refreshing = false,
                error = if (silent) pending.error else negotiationResult.error,
            )
        }

        val proposalsResult = dependencies.getProposals(current.connectionId)
        return pending.copy(
            negotiation = (negotiationResult as ApiResult.Success).value,
            proposals = (proposalsResult as? ApiResult.Success)?.value ?: pending.proposals,
            loading = false,
            refreshing = false,
            error = if (silent) {
                pending.error
            } else {
                (proposalsResult as? ApiResult.Failure)?.error
            },
        )
    }

    suspend fun submitProposals(
        current: RealsRootUiState.Scheduling,
        proposedDateTimes: List<String>,
        onPending: (RealsRootUiState.Scheduling) -> Unit,
    ): RealsRootUiState.Scheduling {
        val pending = current.copy(submitting = true, error = null, message = null)
        onPending(pending)

        val submitResult = dependencies.submitProposals(current.connectionId, proposedDateTimes)
        val refreshed = refresh(pending, silent = false)
        return when (submitResult) {
            is ApiResult.Success -> refreshed.copy(
                submitting = false,
                submittingLabel = null,
                error = refreshed.error,
                message = schedulingActionMessage(
                    negotiation = refreshed.negotiation,
                    pendingMessage = "Enviamos tus horarios.",
                ),
            )

            is ApiResult.Failure -> refreshed.copy(
                submitting = false,
                submittingLabel = null,
                error = submitResult.error,
                message = null,
            )
        }
    }

    suspend fun acceptProposal(
        current: RealsRootUiState.Scheduling,
        proposalId: String,
    ): RealsRootUiState.Scheduling {
        val pending = current.copy(submitting = true, error = null, message = null)
        return when (val acceptResult = dependencies.acceptProposal(current.connectionId, proposalId)) {
            is ApiResult.Success -> {
                val refreshed = refresh(
                    pending.copy(negotiation = acceptResult.value),
                    silent = false,
                )
                refreshed.copy(
                    submitting = false,
                    submittingLabel = null,
                    message = schedulingActionMessage(
                        negotiation = refreshed.negotiation,
                        pendingMessage = "Aceptamos el horario.",
                    ),
                )
            }

            is ApiResult.Failure -> refresh(pending, silent = false).copy(
                submitting = false,
                submittingLabel = null,
                error = acceptResult.error,
            )
        }
    }

    suspend fun rejectRound(current: RealsRootUiState.Scheduling): RealsRootUiState.Scheduling {
        val pending = current.copy(submitting = true, error = null, message = null)
        val previousRound = current.negotiation?.roundNumber
        return when (val rejectResult = dependencies.rejectRound(current.connectionId)) {
            is ApiResult.Success -> {
                val refreshed = refresh(
                    pending.copy(negotiation = rejectResult.value),
                    silent = false,
                )
                refreshed.copy(
                    submitting = false,
                    submittingLabel = null,
                    message = rejectRoundMessage(previousRound, refreshed.negotiation),
                )
            }

            is ApiResult.Failure -> refresh(pending, silent = false).copy(
                submitting = false,
                submittingLabel = null,
                error = rejectResult.error,
            )
        }
    }
}

private fun schedulingActionMessage(
    negotiation: SchedulingNegotiation?,
    pendingMessage: String,
): String = when (negotiation?.status) {
    NegotiationStatus.Confirmed -> "Horario confirmado."
    NegotiationStatus.Failed -> "No hubo acuerdo."
    else -> pendingMessage
}

private fun rejectRoundMessage(
    previousRound: Int?,
    negotiation: SchedulingNegotiation?,
): String = when (negotiation?.status) {
    NegotiationStatus.Confirmed -> "Horario confirmado."
    NegotiationStatus.Failed -> "No hubo acuerdo."
    NegotiationStatus.Pending -> if (previousRound != null && negotiation.roundNumber > previousRound) {
        "Ronda rechazada, se abrio una nueva ronda."
    } else {
        "Ronda rechazada."
    }
    else -> "Ronda rechazada."
}
