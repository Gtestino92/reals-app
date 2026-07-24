package com.reals.app.ui.matchmaking

class HomeRouter {
    fun resolve(
        screenModel: HomeScreenModel,
        autoNavigate: Boolean,
    ): HomeRoute {
        if (!autoNavigate) return HomeRoute.StayHome

        val firstChat = screenModel.pendingActions
            .filterIsInstance<HomeActionItem.FirstChat>()
            .firstOrNull()

        return if (firstChat == null) {
            val activeSecondChat = screenModel.nextSteps
                .firstNotNullOfOrNull { it.activeSecondChatRoute() }
            activeSecondChat ?: HomeRoute.StayHome
        } else {
            HomeRoute.OpenFirstChat(
                matchId = firstChat.matchId,
                chatId = firstChat.chatId,
            )
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

private fun HomeNextStepItem.activeSecondChatRoute(): HomeRoute.OpenSecondChat? {
    val hasActiveChat = when (this) {
        is HomeNextStepItem.SecondChatScheduled -> chatId?.isNotBlank() == true && chatStatus == "ACTIVE"
        is HomeNextStepItem.SecondChatAvailable -> chatId?.isNotBlank() == true && chatStatus == "ACTIVE"
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

