package com.reals.app.ui.root

import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.ProfileStatus

internal fun HomeState.canRemainInHomeForProfileStatus(): Boolean =
    when (profileStatus) {
        ProfileStatus.Active -> true
        ProfileStatus.Draft -> hasExistingOperationalInteraction()
        else -> false
    }

internal fun HomeState.hasExistingOperationalInteraction(): Boolean =
    pendingActions.isNotEmpty() ||
        nextSteps.isNotEmpty() ||
        passiveNotices.isNotEmpty() ||
        activeInteractionsSummary.hasPendingSchedulingConnection ||
        activeInteractionsSummary.activeInitialCount > 0 ||
        activeInteractionsSummary.activeConnectionCount > 0 ||
        activeInteractionsSummary.actionableConnectionCount > 0
