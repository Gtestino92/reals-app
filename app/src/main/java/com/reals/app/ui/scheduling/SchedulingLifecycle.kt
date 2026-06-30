package com.reals.app.ui.scheduling

import com.reals.app.core.time.isExpired
import com.reals.app.core.time.isWithinWarningWindow

internal const val SCHEDULING_WARNING_MINUTES = 24L * 60L

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
