package com.reals.app.ui.scheduling

import com.reals.app.core.time.isExpired
import com.reals.app.core.time.isWithinWarningWindow
import com.reals.app.domain.model.NegotiationStatus
import com.reals.app.domain.model.SchedulingNegotiation
import com.reals.app.ui.common.deadlineRemainingFraction

internal const val SCHEDULING_WARNING_MINUTES = 60L

internal data class SchedulingLifecycleUiState(
    val showWarning: Boolean,
    val expired: Boolean,
)

internal fun schedulingLifecycleUiState(
    schedulingExpiresAt: String?,
    nowMillis: Long = System.currentTimeMillis(),
    warningMinutes: Long = SCHEDULING_WARNING_MINUTES,
): SchedulingLifecycleUiState =
    SchedulingLifecycleUiState(
        showWarning = isWithinWarningWindow(
            value = schedulingExpiresAt,
            nowMillis = nowMillis,
            warningMillis = warningMinutes * 60_000L,
        ),
        expired = isExpired(schedulingExpiresAt, nowMillis),
    )

internal fun schedulingDeadlineRemainingFraction(
    negotiationCreatedAt: String?,
    schedulingExpiresAt: String?,
    nowMillis: Long = System.currentTimeMillis(),
): Double? = deadlineRemainingFraction(
    startedAt = negotiationCreatedAt,
    expiresAt = schedulingExpiresAt,
    nowMillis = nowMillis,
)

internal fun shouldShowSchedulingDeadlineProgress(
    negotiation: SchedulingNegotiation?,
): Boolean = negotiation?.status == NegotiationStatus.Pending
