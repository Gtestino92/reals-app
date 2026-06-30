package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.core.time.isExpired
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ChatExitReason
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
    ): SecondChatLoadResult {
        val chatResult = dependencies.getSecondChatForConnection(connectionId)
        if (chatResult is ApiResult.Failure) {
            return SecondChatLoadResult.Show(RealsRootUiState.SecondChat(
                session = session,
                connectionId = connectionId,
                matchId = matchId,
                partnerName = partnerName,
                loading = false,
                error = chatResult.error,
            ))
        }

        val chat = (chatResult as ApiResult.Success).value
        chat.secondChatHomeMessage()?.let { message ->
            return SecondChatLoadResult.ReturnHome(session, message)
        }
        val messagesResult = dependencies.getChatMessages(chat.id)
        val exitsResult = dependencies.getChatExitRequests(chat.id)

        return SecondChatLoadResult.Show(RealsRootUiState.SecondChat(
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
        ))
    }

    suspend fun refresh(
        current: RealsRootUiState.SecondChat,
        silent: Boolean,
    ): SecondChatLoadResult {
        val chatId = current.chat?.id ?: current.chatId ?: return SecondChatLoadResult.Show(current)
        val pending = current.copy(
            refreshing = true,
            error = if (silent) current.error else null,
            message = if (silent) current.message else null,
        )
        val chatResult = dependencies.getChat(chatId)
        val messagesResult = dependencies.getChatMessages(chatId, pending.messages.lastMessageCursor())
        val exitsResult = dependencies.getChatExitRequests(chatId)

        val updated = pending.copy(
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
        updated.chat?.secondChatHomeMessage()?.let { message ->
            return SecondChatLoadResult.ReturnHome(current.session, message)
        }
        return SecondChatLoadResult.Show(updated)
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

    suspend fun safetyCancel(
        current: RealsRootUiState.SecondChat,
        details: String,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatActionResult {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
            return SecondChatActionResult.Ignore
        }
        val chat = current.chat ?: return SecondChatActionResult.Ignore

        val cleanDetails = normalizeSafetyReportDetails(details) ?: return SecondChatActionResult.Show(
            current.copy(
                error = invalidSafetyReportDetailsError(),
                message = null,
            )
        )

        val pending = current.copy(
            actionLoading = true,
            actionLoadingLabel = "Enviando reporte...",
            error = null,
            message = null,
        )
        onPending(pending)

        return when (val result = dependencies.safetyCancelChat(
            chat.id,
            ChatExitReason.InappropriateBehavior,
            cleanDetails,
        )) {
            is ApiResult.Success -> SecondChatActionResult.ReturnHome(
                session = current.session,
                message = "Reporte enviado. Cerramos esta conversacion por seguridad.",
            )

            is ApiResult.Failure -> SecondChatActionResult.Show(
                pending.copy(
                    actionLoading = false,
                    actionLoadingLabel = null,
                    error = result.error,
                )
            )
        }
    }
}

internal fun ChatStatus.isOpenSecondChatStatus(): Boolean = this == ChatStatus.Active

private fun com.reals.app.domain.model.Chat.secondChatHomeMessage(
    nowMillis: Long = System.currentTimeMillis(),
): String? = when (status) {
    ChatStatus.Closed,
    ChatStatus.Cancelled,
    ChatStatus.Abandoned,
    ChatStatus.Finished -> "Este segundo chat ya no est\u00e1 disponible."
    ChatStatus.Expired -> if (readOnlyUntil == null || isExpired(readOnlyUntil, nowMillis)) {
        "Este segundo chat ya no est\u00e1 disponible."
    } else {
        null
    }
    else -> null
}

internal sealed interface SecondChatLoadResult {
    data class Show(val state: RealsRootUiState.SecondChat) : SecondChatLoadResult
    data class ReturnHome(val session: ProvisionedSession, val message: String) : SecondChatLoadResult
}

internal sealed interface SecondChatActionResult {
    data object Ignore : SecondChatActionResult

    data class Show(val state: RealsRootUiState.SecondChat) : SecondChatActionResult

    data class ReturnHome(
        val session: ProvisionedSession,
        val message: String?,
    ) : SecondChatActionResult
}
