package com.reals.app.ui.chat

import com.reals.app.core.time.isExpired
import com.reals.app.core.time.isWithinWarningWindow

internal const val VISUAL_PHASE_WARNING_MINUTES = 10L

internal data class VisualApprovalLifecycleUiState(
    val showWarning: Boolean,
    val expired: Boolean,
)

internal fun visualApprovalLifecycleUiState(
    visualExpiresAt: String?,
    nowMillis: Long = System.currentTimeMillis(),
    warningMinutes: Long = VISUAL_PHASE_WARNING_MINUTES,
): VisualApprovalLifecycleUiState =
    VisualApprovalLifecycleUiState(
        showWarning = isWithinWarningWindow(
            value = visualExpiresAt,
            nowMillis = nowMillis,
            warningMillis = warningMinutes * 60_000L,
        ),
        expired = isExpired(visualExpiresAt, nowMillis),
    )
