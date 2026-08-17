package com.reals.app.ui.matchmaking

class HomeRouter {
    fun resolve(
        screenModel: HomeScreenModel,
        autoNavigate: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): HomeRoute {
        val firstChat = screenModel.pendingActions
            .filterIsInstance<HomeActionItem.FirstChat>()
            .firstOrNull()

        return if (firstChat != null) {
            HomeRoute.OpenFirstChat(
                matchId = firstChat.matchId,
                chatId = firstChat.chatId,
            )
        } else if (autoNavigate) {
            val activeSecondChat = screenModel.nextSteps
                .mapNotNull { nextStep: HomeNextStepItem  ->
                    nextStep.activeSecondChatRoute(nowMillis)
                }
                .firstOrNull()

            activeSecondChat ?: HomeRoute.StayHome
        } else {
            HomeRoute.StayHome
        }
    }
}

private fun HomeNextStepItem.activeSecondChatRoute(
    nowMillis: Long,
): HomeRoute.OpenSecondChat? {
    val hasActiveChat = when (this) {
        is HomeNextStepItem.SecondChatScheduled ->
            chatId?.isNotBlank() == true &&
                    chatStatus == "ACTIVE" &&
                    myAttendanceStatus.isJoinedSecondChatAttendance() &&
                    canOpenSecondChatNow(nowMillis)

        is HomeNextStepItem.SecondChatAvailable ->
            chatId?.isNotBlank() == true &&
                    chatStatus == "ACTIVE" &&
                    myAttendanceStatus.isJoinedSecondChatAttendance() &&
                    canOpenSecondChatNow(nowMillis)

        else -> false
    }

    if (!hasActiveChat) return null

    return HomeRoute.OpenSecondChat(
        connectionId = connectionIdForSecondChat(),
        matchId = when (this) {
            is HomeNextStepItem.SecondChatScheduled -> matchId
            is HomeNextStepItem.SecondChatAvailable -> matchId
            else -> ""
        },
        partnerName = when (this) {
            is HomeNextStepItem.SecondChatScheduled -> partnerDisplayName
            is HomeNextStepItem.SecondChatAvailable -> partnerDisplayName
            else -> null
        },
    )
}

private fun String?.isJoinedSecondChatAttendance(): Boolean =
    this == "ON_TIME" || this == "LATE"
