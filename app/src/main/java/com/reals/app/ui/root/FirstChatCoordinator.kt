package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ProvisionedSession

internal class FirstChatCoordinator(
    private val dependencies: FirstChatFeatureDependencies,
) {
    suspend fun load(
        session: ProvisionedSession,
        matchId: String,
        chatId: String?,
    ): FirstChatLoadResult {
        val matchResult = dependencies.getMatch(matchId)
        if (matchResult is ApiResult.Failure) {
            return FirstChatLoadResult.Show(
                RealsRootUiState.FirstChat(
                    session = session,
                    matchId = matchId,
                    chatId = chatId,
                    loading = false,
                    error = matchResult.error,
                )
            )
        }

        val match = (matchResult as ApiResult.Success).value
        if (match.state !is com.reals.app.domain.model.MatchState.Unknown &&
            match.state != com.reals.app.domain.model.MatchState.ChatActive
        ) {
            return FirstChatLoadResult.RouteHome(firstChatExitMessage(match.state))
        }

        val chatResult = dependencies.getFirstChatForMatch(matchId)
        if (chatResult is ApiResult.Failure) {
            return FirstChatLoadResult.Show(
                RealsRootUiState.FirstChat(
                    session = session,
                    matchId = matchId,
                    chatId = chatId,
                    match = match,
                    loading = false,
                    error = chatResult.error,
                )
            )
        }

        val chat = (chatResult as ApiResult.Success).value
        if (!chat.status.isOpenFirstChatStatus()) {
            return FirstChatLoadResult.RouteHome("El chat cambio de estado. Actualizamos tu Home.")
        }

        val messagesResult = dependencies.getChatMessages(chat.id)
        val exitsResult = dependencies.getChatExitRequests(chat.id)

        return FirstChatLoadResult.Show(
            RealsRootUiState.FirstChat(
                session = session,
                matchId = matchId,
                chatId = chat.id,
                match = match,
                chat = chat,
                messages = (messagesResult as? ApiResult.Success)?.value.orEmpty(),
                exitRequests = (exitsResult as? ApiResult.Success)?.value.orEmpty(),
                loading = false,
                error = (messagesResult as? ApiResult.Failure)?.error
                    ?: (exitsResult as? ApiResult.Failure)?.error,
            )
        )
    }

    suspend fun refresh(
        current: RealsRootUiState.FirstChat,
        silent: Boolean,
    ): FirstChatRefreshResult {
        val chat = current.chat ?: return FirstChatRefreshResult.Reopen(current.matchId, current.chatId)
        val pending = current.copy(
            refreshing = true,
            error = if (silent) current.error else null,
            message = if (silent) current.message else null,
        )
        val chatResult = dependencies.getFirstChatForMatch(current.matchId)
        val matchResult = dependencies.getMatch(current.matchId)
        val messagesResult = dependencies.getChatMessages(chat.id, pending.messages.lastMessageCursor())
        val exitsResult = dependencies.getChatExitRequests(chat.id)
        val updatedMatch = (matchResult as? ApiResult.Success)?.value ?: pending.match
        val updatedChat = (chatResult as? ApiResult.Success)?.value ?: pending.chat
        val updatedExitRequests = (exitsResult as? ApiResult.Success)?.value ?: pending.exitRequests

        if (
            (updatedMatch != null &&
                updatedMatch.state !is com.reals.app.domain.model.MatchState.Unknown &&
                updatedMatch.state != com.reals.app.domain.model.MatchState.ChatActive) ||
            (updatedChat != null && !updatedChat.status.isOpenFirstChatStatus())
        ) {
            return FirstChatRefreshResult.Closed(updatedMatch?.state)
        }

        if (updatedExitRequests.latestExitRequest()?.status.isResolvedExitStatus()) {
            return FirstChatRefreshResult.ExitResolved
        }

        return FirstChatRefreshResult.Show(
            pending.copy(
                match = updatedMatch,
                chat = updatedChat,
                messages = (messagesResult as? ApiResult.Success)?.value
                    ?.let { pending.messages.appendUnique(it) }
                    ?: pending.messages,
                exitRequests = updatedExitRequests,
                refreshing = false,
                error = if (silent) {
                    pending.error
                } else {
                    (chatResult as? ApiResult.Failure)?.error
                        ?: (matchResult as? ApiResult.Failure)?.error
                        ?: (messagesResult as? ApiResult.Failure)?.error
                        ?: (exitsResult as? ApiResult.Failure)?.error
                },
            )
        )
    }

    suspend fun sendMessage(
        current: RealsRootUiState.FirstChat,
        cleanContent: String,
    ): RealsRootUiState.FirstChat {
        val chat = current.chat ?: return current
        val cursorBeforeSend = current.messages.lastMessageCursor()
        val pending = current.copy(sending = true, error = null, message = null)
        return when (val result = dependencies.sendChatMessage(chat.id, cleanContent)) {
            is ApiResult.Success -> {
                val messagesResult = dependencies.getChatMessages(chat.id, cursorBeforeSend)
                val chatResult = dependencies.getFirstChatForMatch(current.matchId)
                pending.copy(
                    chat = (chatResult as? ApiResult.Success)?.value ?: pending.chat,
                    messages = pending.messages.appendUnique(
                        (messagesResult as? ApiResult.Success)?.value.orEmpty() + result.value
                    ),
                    sending = false,
                    error = (messagesResult as? ApiResult.Failure)?.error
                        ?: (chatResult as? ApiResult.Failure)?.error,
                )
            }

            is ApiResult.Failure -> pending.copy(
                sending = false,
                error = result.error,
            )
        }
    }
}

internal sealed interface FirstChatLoadResult {
    data class Show(val state: RealsRootUiState.FirstChat) : FirstChatLoadResult
    data class RouteHome(val message: String) : FirstChatLoadResult
}

internal sealed interface FirstChatRefreshResult {
    data class Show(val state: RealsRootUiState.FirstChat) : FirstChatRefreshResult
    data class Reopen(val matchId: String, val chatId: String?) : FirstChatRefreshResult
    data class Closed(val matchState: com.reals.app.domain.model.MatchState?) : FirstChatRefreshResult
    data object ExitResolved : FirstChatRefreshResult
}
