package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.usecase.BlockMatchParticipantUseCase

internal class ManualBlockCoordinator(
    private val blockMatchParticipant: BlockMatchParticipantUseCase,
) {
    suspend fun block(
        current: RealsRootUiState,
        onPending: (RealsRootUiState) -> Unit,
    ): ManualBlockResult {
        val pending = current.manualBlockPendingState() ?: return ManualBlockResult.Ignore
        onPending(pending)
        return when (val result = blockMatchParticipant(pending.blockableMatchId())) {
            is ApiResult.Success -> ManualBlockResult.ReturnHome(pending.blockableSession())
            is ApiResult.Failure -> ManualBlockResult.Show(
                pending.withManualBlock(
                    ManualBlockUiState(loading = false, error = result.error),
                ),
            )
        }
    }
}

internal sealed interface ManualBlockResult {
    data object Ignore : ManualBlockResult
    data class Show(val state: RealsRootUiState) : ManualBlockResult
    data class ReturnHome(val session: ProvisionedSession) : ManualBlockResult
}

private fun RealsRootUiState.manualBlockPendingState(): RealsRootUiState? = when (this) {
    is RealsRootUiState.FirstChat ->
        takeUnless {
            loading || refreshing || sending || actionLoading || guidanceActionLoading ||
                manualBlock.loading
        }?.copy(manualBlock = ManualBlockUiState(loading = true))
    is RealsRootUiState.SecondChat ->
        takeUnless {
            loading || refreshing || sending || actionLoading || manualBlock.loading
        }?.copy(manualBlock = ManualBlockUiState(loading = true))
    is RealsRootUiState.VisualApproval ->
        takeUnless {
            loading || refreshing || readingPartnerMessage || writingMessage || deciding ||
                manualBlock.loading
        }?.copy(manualBlock = ManualBlockUiState(loading = true))
    is RealsRootUiState.Scheduling ->
        takeUnless {
            loading || refreshing || submitting || manualBlock.loading
        }?.copy(manualBlock = ManualBlockUiState(loading = true))
    is RealsRootUiState.PartnerProfile ->
        takeUnless {
            loading || refreshing || manualBlock.loading
        }?.copy(manualBlock = ManualBlockUiState(loading = true))
    else -> null
}

private fun RealsRootUiState.withManualBlock(value: ManualBlockUiState): RealsRootUiState = when (this) {
    is RealsRootUiState.FirstChat -> copy(manualBlock = value)
    is RealsRootUiState.SecondChat -> copy(manualBlock = value)
    is RealsRootUiState.VisualApproval -> copy(manualBlock = value)
    is RealsRootUiState.Scheduling -> copy(manualBlock = value)
    is RealsRootUiState.PartnerProfile -> copy(manualBlock = value)
    else -> this
}

internal fun RealsRootUiState.clearManualBlockError(): RealsRootUiState = when (this) {
    is RealsRootUiState.FirstChat ->
        if (manualBlock.loading) this else copy(manualBlock = manualBlock.copy(error = null))
    is RealsRootUiState.SecondChat ->
        if (manualBlock.loading) this else copy(manualBlock = manualBlock.copy(error = null))
    is RealsRootUiState.VisualApproval ->
        if (manualBlock.loading) this else copy(manualBlock = manualBlock.copy(error = null))
    is RealsRootUiState.Scheduling ->
        if (manualBlock.loading) this else copy(manualBlock = manualBlock.copy(error = null))
    is RealsRootUiState.PartnerProfile ->
        if (manualBlock.loading) this else copy(manualBlock = manualBlock.copy(error = null))
    else -> this
}

private fun RealsRootUiState.blockableMatchId(): String = when (this) {
    is RealsRootUiState.FirstChat -> matchId
    is RealsRootUiState.SecondChat -> matchId
    is RealsRootUiState.VisualApproval -> matchId
    is RealsRootUiState.Scheduling -> matchId
    is RealsRootUiState.PartnerProfile -> matchId
    else -> error("State is not blockable")
}

private fun RealsRootUiState.blockableSession(): ProvisionedSession = when (this) {
    is RealsRootUiState.FirstChat -> session
    is RealsRootUiState.SecondChat -> session
    is RealsRootUiState.VisualApproval -> session
    is RealsRootUiState.Scheduling -> session
    is RealsRootUiState.PartnerProfile -> session
    else -> error("State is not blockable")
}
