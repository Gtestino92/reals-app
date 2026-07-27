package com.reals.app.ui.root

import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatStatus

internal const val SECOND_CHAT_ABSOLUTE_EXPIRY_WARNING_MILLIS = 10 * 60 * 1000L

internal data class SecondChatTimingPresentation(
    val joined: Boolean,
    val lifecycleActive: Boolean,
    val remainingMillis: Long?,
    val genuinelyActive: Boolean,
    val locallyExpired: Boolean,
    val showAbsoluteExpiryWarning: Boolean,
)

internal fun SecondChatLifecycleUiState.timingPresentation(
    nowMillis: Long = System.currentTimeMillis(),
): SecondChatTimingPresentation =
    status.secondChatTimingPresentation(
        statusReceivedAtMillis = statusReceivedAtMillis,
        nowMillis = nowMillis,
    )

internal fun SecondChatStatus?.secondChatTimingPresentation(
    statusReceivedAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): SecondChatTimingPresentation {
    val status = this
    val joined = status?.isJoinedSecondChat() == true
    val lifecycleActive = status?.chatStatus == ChatStatus.Active
    val remainingMillis = status?.remainingAbsoluteMillis(
        statusReceivedAtMillis = statusReceivedAtMillis,
        nowMillis = nowMillis,
    )
    val genuinelyActive = joined && lifecycleActive && remainingMillis != null && remainingMillis > 0
    val locallyExpired = joined && lifecycleActive && remainingMillis != null && remainingMillis <= 0
    return SecondChatTimingPresentation(
        joined = joined,
        lifecycleActive = lifecycleActive,
        remainingMillis = remainingMillis,
        genuinelyActive = genuinelyActive,
        locallyExpired = locallyExpired,
        showAbsoluteExpiryWarning = genuinelyActive &&
            remainingMillis <= SECOND_CHAT_ABSOLUTE_EXPIRY_WARNING_MILLIS,
    )
}

internal fun SecondChatStatus.remainingMillisFromServerSnapshot(
    targetTime: String,
    statusReceivedAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val server = backendInstantOrNull(serverTime) ?: return null
    val target = backendInstantOrNull(targetTime) ?: return null
    val localReceipt = statusReceivedAtMillis ?: nowMillis
    val synchronizedNow = server.toEpochMilli() + (nowMillis - localReceipt)
    return target.toEpochMilli() - synchronizedNow
}

private fun SecondChatStatus.remainingAbsoluteMillis(
    statusReceivedAtMillis: Long?,
    nowMillis: Long,
): Long? = remainingMillisFromServerSnapshot(
    targetTime = absoluteExpiresAt,
    statusReceivedAtMillis = statusReceivedAtMillis,
    nowMillis = nowMillis,
)

private fun SecondChatStatus.isJoinedSecondChat(): Boolean =
    chatId?.isNotBlank() == true &&
        (
            myAttendanceStatus == SecondChatAttendanceStatus.OnTime ||
                myAttendanceStatus == SecondChatAttendanceStatus.Late
            )
