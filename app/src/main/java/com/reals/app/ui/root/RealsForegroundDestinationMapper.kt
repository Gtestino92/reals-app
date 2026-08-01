package com.reals.app.ui.root

import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.foreground.ForegroundDestination

fun RealsRootUiState.foregroundDestination(): ForegroundDestination = when (this) {
    is RealsRootUiState.Ready -> when {
        editingActiveProfile -> ForegroundDestination.ProfileManagement
        session.profileSnapshot is ProfileSnapshot.Found && shouldRenderHomeSurface() -> ForegroundDestination.Home
        else -> ForegroundDestination.ProfileManagement
    }

    is RealsRootUiState.FirstChat -> ForegroundDestination.FirstChat(
        matchId = matchId,
        chatId = chatId ?: chat?.id,
    )

    is RealsRootUiState.SecondChat -> ForegroundDestination.SecondChat(
        connectionId = connectionId,
    )

    is RealsRootUiState.VisualApproval -> ForegroundDestination.VisualReview(
        matchId = matchId,
    )

    is RealsRootUiState.Scheduling -> ForegroundDestination.Scheduling(
        connectionId = connectionId,
    )

    is RealsRootUiState.PartnerProfile -> ForegroundDestination.PartnerProfile(
        matchId = matchId,
    )

    RealsRootUiState.Checking,
    is RealsRootUiState.MissingFirebase,
    is RealsRootUiState.Login,
    is RealsRootUiState.LoadingSession,
    is RealsRootUiState.AccountDeletionScheduled,
    is RealsRootUiState.AccountDeletionPending,
    is RealsRootUiState.LegalRequirements,
    is RealsRootUiState.PendingEngagement,
    is RealsRootUiState.ActivationComplete,
    is RealsRootUiState.Failure -> ForegroundDestination.Other
}
