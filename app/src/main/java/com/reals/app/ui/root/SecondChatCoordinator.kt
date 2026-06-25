package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ProvisionedSession

internal class SecondChatCoordinator(
    private val dependencies: FirstChatFeatureDependencies,
) {
    suspend fun load(
        session: ProvisionedSession,
        connectionId: String,
        matchId: String,
        partnerName: String?,
    ): RealsRootUiState.SecondChat {
        val chatResult = dependencies.getSecondChatForConnection(connectionId)
        if (chatResult is ApiResult.Failure) {
            return RealsRootUiState.SecondChat(
                session = session,
                connectionId = connectionId,
                matchId = matchId,
                partnerName = partnerName,
                loading = false,
                error = chatResult.error,
            )
        }

        val chat = (chatResult as ApiResult.Success).value
        val messagesResult = dependencies.getChatMessages(chat.id)
        val exitsResult = dependencies.getChatExitRequests(chat.id)

        return RealsRootUiState.SecondChat(
            session = session,
            connectionId = connectionId,
            matchId = matchId,
            partnerName = partnerName,
            chatId = chat.id,
            chat = chat,
            messages = (messagesResult as? ApiResult.Success)?.value.orEmpty(),
            exitRequests = (exitsResult as? ApiResult.Success)?.value.orEmpty(),
            loading = false,
            error = (messagesResult as? ApiResult.Failure)?.error
                ?: (exitsResult as? ApiResult.Failure)?.error,
        )
    }

    suspend fun refresh(
        current: RealsRootUiState.SecondChat,
        silent: Boolean,
    ): RealsRootUiState.SecondChat {
        val chatId = current.chat?.id ?: current.chatId ?: return current
        val pending = current.copy(
            refreshing = true,
            error = if (silent) current.error else null,
            message = if (silent) current.message else null,
        )
        val chatResult = dependencies.getChat(chatId)
        val messagesResult = dependencies.getChatMessages(chatId, pending.messages.lastMessageCursor())
        val exitsResult = dependencies.getChatExitRequests(chatId)

        return pending.copy(
            chat = (chatResult as? ApiResult.Success)?.value ?: pending.chat,
            messages = (messagesResult as? ApiResult.Success)?.value
                ?.let { pending.messages.appendUnique(it) }
                ?: pending.messages,
            exitRequests = (exitsResult as? ApiResult.Success)?.value ?: pending.exitRequests,
            refreshing = false,
            error = if (silent) {
                pending.error
            } else {
                (chatResult as? ApiResult.Failure)?.error
                    ?: (messagesResult as? ApiResult.Failure)?.error
                    ?: (exitsResult as? ApiResult.Failure)?.error
            },
        )
    }

    suspend fun sendMessage(
        current: RealsRootUiState.SecondChat,
        cleanContent: String,
        localId: String,
    ): RealsRootUiState.SecondChat {
        val chat = current.chat ?: return current
        val cursorBeforeSend = current.messages.lastMessageCursor()

        return when (val result = dependencies.sendChatMessage(chat.id, cleanContent)) {
            is ApiResult.Success -> {
                val messagesResult = dependencies.getChatMessages(chat.id, cursorBeforeSend)
                val chatResult = dependencies.getChat(chat.id)
                current.copy(
                    chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
                    messages = current.messages.appendUnique(
                        (messagesResult as? ApiResult.Success)?.value.orEmpty() + result.value
                    ),
                    optimisticMessages = current.optimisticMessages.filterNot { it.localId == localId },
                    sending = false,
                    error = (messagesResult as? ApiResult.Failure)?.error
                        ?: (chatResult as? ApiResult.Failure)?.error,
                )
            }

            is ApiResult.Failure -> current.copy(
                optimisticMessages = current.optimisticMessages.markOptimisticMessageFailed(localId),
                sending = false,
                error = result.error,
            )
        }
    }
}

internal fun ChatStatus.isOpenSecondChatStatus(): Boolean = this == ChatStatus.Active
