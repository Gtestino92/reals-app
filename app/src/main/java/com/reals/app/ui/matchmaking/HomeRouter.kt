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
            HomeRoute.StayHome
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
}

