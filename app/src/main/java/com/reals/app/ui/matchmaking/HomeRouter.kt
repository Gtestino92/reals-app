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
                .mapNotNull { nextStep ->
                    nextStep.activeSecondChatRoute(nowMillis)
                }
                .firstOrNull()

            activeSecondChat ?: HomeRoute.StayHome
        } else {
            HomeRoute.StayHome
        }
    }
}

sealed interface HomeRoute {
    data object StayHome : HomeRoute

    data class OpenFirstChat(
        val matchId: String,
        val chatId: String,
    ) : HomeRoute

    data class OpenSecondChat(
        val connectionId: String,
        val matchId: String,
        val partnerName: String?,
    ) : HomeRoute
}

private fun HomeNextStepItem.activeSecondChatRoute(
    nowMillis: Long,
): HomeRoute.OpenSecondChat? =
    when (this) {
        is HomeNextStepItem.SecondChatScheduled ->
            if (
                chatId?.isNotBlank() == true &&
                chatStatus == "ACTIVE" &&
                myAttendanceStatus.isJoinedSecondChatAttendance() &&
                canOpenSecondChatNow(nowMillis)
            ) {
                HomeRoute.OpenSecondChat(
                    connectionId = connectionId,
                    matchId = matchId,
                    partnerName = partnerDisplayName,
                )
            } else {
                null
            }

        is HomeNextStepItem.SecondChatAvailable ->
            if (
                chatId?.isNotBlank() == true &&
                chatStatus == "ACTIVE" &&
                myAttendanceStatus.isJoinedSecondChatAttendance() &&
                canOpenSecondChatNow(nowMillis)
            ) {
                HomeRoute.OpenSecondChat(
                    connectionId = connectionId,
                    matchId = matchId,
                    partnerName = partnerDisplayName,
                )
            } else {
                null
            }

        else -> null
    }

private fun String?.isJoinedSecondChatAttendance(): Boolean =
    this == "ON_TIME" || this == "LATE"
