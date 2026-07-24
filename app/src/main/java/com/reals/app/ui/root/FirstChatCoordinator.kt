package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.security.TextSafety
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.ProvisionedSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class FirstChatCoordinator(
    private val dependencies: FirstChatFeatureDependencies,
) {
    suspend fun load(
        session: ProvisionedSession,
        matchId: String,
        chatId: String?,
    ): FirstChatLoadResult {
        val (matchResult, chatResult) = coroutineScope {
            val matchDeferred = async { dependencies.getMatch(matchId) }
            val chatDeferred = async { dependencies.getFirstChatForMatch(matchId) }
            matchDeferred.await() to chatDeferred.await()
        }

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
        if (match.state !is MatchState.Unknown &&
            match.state != MatchState.ChatActive
        ) {
            return FirstChatLoadResult.RouteHome(firstChatExitMessage(match.state))
        }

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
            return FirstChatLoadResult.RouteHome(
                chat.status.firstChatClosedMessage() ?: "El chat cambió de estado. Actualizamos tu Home."
            )
        }

        if (chat.myDecision != ChatDecisionState.Pending) {
            return FirstChatLoadResult.RouteHome("Ya registramos tu decisión. Actualizamos tu Home.")
        }

        val (messagesResult, exitsResult) = coroutineScope {
            val messagesDeferred = async { dependencies.getChatMessages(chat.id) }
            val exitsDeferred = async { dependencies.getChatExitRequests(chat.id) }
            messagesDeferred.await() to exitsDeferred.await()
        }

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
        val resolvedExitRequest = updatedExitRequests
            .latestExitRequest()
            ?.takeIf { it.status.isResolvedExitStatus() }

        if (resolvedExitRequest != null) {
            return FirstChatRefreshResult.ExitResolved(
                message = resolvedExitRequest.resolvedHomeMessage(current.session.user.id)
            )
        }

        if (
            (updatedMatch != null &&
                updatedMatch.state !is MatchState.Unknown &&
                updatedMatch.state != MatchState.ChatActive) ||
            (updatedChat != null && !updatedChat.status.isOpenFirstChatStatus())
        ) {
            return FirstChatRefreshResult.Closed(
                matchState = updatedMatch?.state,
                chatStatus = updatedChat?.status,
            )
        }

        if (updatedChat != null && updatedChat.myDecision != ChatDecisionState.Pending) {
            return FirstChatRefreshResult.Closed(
                matchState = updatedMatch?.state,
                chatStatus = updatedChat?.status,
            )
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
        localId: String,
    ): FirstChatSendResult {
        val chat = current.chat ?: return FirstChatSendResult.Show(current)
        val cursorBeforeSend = current.messages.lastMessageCursor()
        return when (val result = dependencies.sendChatMessage(chat.id, cleanContent)) {
            is ApiResult.Success -> {
                val sentMessage = result.value
                val messagesWithSent = current.messages.appendUnique(listOf(sentMessage))

                val messagesResult = dependencies.getChatMessages(chat.id, cursorBeforeSend)
                val chatResult = dependencies.getFirstChatForMatch(current.matchId)

                FirstChatSendResult.Show(current.copy(
                    chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
                    messages = messagesWithSent.appendUnique(
                        (messagesResult as? ApiResult.Success)?.value.orEmpty()
                    ),
                    optimisticMessages = current.optimisticMessages.filterNot { it.localId == localId },
                    sending = false,
                    error = (messagesResult as? ApiResult.Failure)?.error
                        ?: (chatResult as? ApiResult.Failure)?.error,
                ))
            }

            is ApiResult.Failure -> result.error.firstChatSendExpiryRoute(current)
                ?: refreshAfterPendingMutualCancellation(
                    error = result.error,
                    current = current,
                    chatId = chat.id,
                    localId = localId,
                )
                ?: FirstChatSendResult.Show(
                    current.copy(
                        optimisticMessages = current.optimisticMessages.markOptimisticMessageFailed(localId),
                        sending = false,
                        error = result.error,
                    )
            )
        }
    }

    private suspend fun refreshAfterPendingMutualCancellation(
        error: ApiError,
        current: RealsRootUiState.FirstChat,
        chatId: String,
        localId: String,
    ): FirstChatSendResult.Show? {
        if (error !is ApiError.Backend ||
            error.backendErrorCode != BackendErrorCode.ChatMutualCancellationPending
        ) {
            return null
        }

        val chatResult = dependencies.getFirstChatForMatch(current.matchId)
        val exitsResult = dependencies.getChatExitRequests(chatId)
        return FirstChatSendResult.Show(
            current.copy(
                chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
                exitRequests = (exitsResult as? ApiResult.Success)?.value
                    ?: current.exitRequests,
                optimisticMessages = current.optimisticMessages.filterNot { it.localId == localId },
                sending = false,
                error = (chatResult as? ApiResult.Failure)?.error
                    ?: (exitsResult as? ApiResult.Failure)?.error
                    ?: error,
            )
        )
    }

    private suspend fun refreshActionAfterPendingMutualCancellation(
        error: ApiError,
        current: RealsRootUiState.FirstChat,
        pending: RealsRootUiState.FirstChat,
    ): FirstChatActionResult.Show? {
        val chatId = current.chat?.id ?: return null
        return refreshLockedExitStateAfterPendingMutualCancellation(
            error = error,
            current = pending,
            chatId = chatId,
        )?.let { refreshed ->
            FirstChatActionResult.Show(
                refreshed.copy(
                    actionLoading = false,
                    actionLoadingLabel = null,
                )
            )
        }
    }

    private suspend fun refreshGuidanceAfterPendingMutualCancellation(
        error: ApiError,
        pending: RealsRootUiState.FirstChat,
        chatId: String,
    ): FirstChatActionResult.Show? =
        refreshLockedExitStateAfterPendingMutualCancellation(
            error = error,
            current = pending,
            chatId = chatId,
        )?.let { refreshed ->
            FirstChatActionResult.Show(
                refreshed.copy(guidanceActionLoading = false)
            )
        }

    private suspend fun refreshLockedExitStateAfterPendingMutualCancellation(
        error: ApiError,
        current: RealsRootUiState.FirstChat,
        chatId: String,
    ): RealsRootUiState.FirstChat? {
        if (error !is ApiError.Backend ||
            error.backendErrorCode != BackendErrorCode.ChatMutualCancellationPending
        ) {
            return null
        }

        val chatResult = dependencies.getFirstChatForMatch(current.matchId)
        val exitsResult = dependencies.getChatExitRequests(chatId)
        return current.copy(
            chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
            exitRequests = (exitsResult as? ApiResult.Success)?.value
                ?: current.exitRequests,
            error = (chatResult as? ApiResult.Failure)?.error
                ?: (exitsResult as? ApiResult.Failure)?.error
                ?: error,
        )
    }

    suspend fun submitDecision(
        current: RealsRootUiState.FirstChat,
        decision: ChatContinueDecision,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
            return FirstChatActionResult.Ignore
        }
        if (current.hasPendingExitRequest()) {
            return FirstChatActionResult.Ignore
        }
        val chat = current.chat
        if (chat != null && chat.myDecision != ChatDecisionState.Pending) {
            return FirstChatActionResult.Show(
                current.copy(
                    message = "Ya registramos tu decisión para este chat.",
                    error = null,
                )
            )
        }

        val pending = current.copy(
            actionLoading = true,
            actionLoadingLabel = if (decision == ChatContinueDecision.Approved) {
                "Aprobando..."
            } else {
                "Rechazando..."
            },
            error = null,
            message = null,
        )
        onPending(pending)

        return when (val result = dependencies.submitChatDecision(current.matchId, decision)) {
            is ApiResult.Success -> {
                when (val state = result.value.state) {
                    MatchState.ChatActive -> {
                        if (decision == ChatContinueDecision.Approved) {
                            FirstChatActionResult.ReloadHome(
                                session = current.session,
                                message = "Aprobaste el chat. Te avisaremos si la otra persona también aprueba.",
                                hideFirstChatMatchId = current.matchId,
                                autoNavigateEngagements = false,
                            )
                        } else {
                            FirstChatActionResult.Show(
                                pending.copy(
                                    match = result.value,
                                    actionLoading = false,
                                    actionLoadingLabel = null,
                                    message = firstChatDecisionMessage(state),
                                )
                            )
                        }
                    }

                    MatchState.VisualPhase,
                    MatchState.ChatRejected,
                    MatchState.Expired,
                    MatchState.VisualApproved,
                    MatchState.VisualRejected -> {
                        if (decision == ChatContinueDecision.Approved) {
                            FirstChatActionResult.ReloadHome(
                                session = current.session,
                                message = firstChatExitMessage(state),
                                hideFirstChatMatchId = current.matchId,
                                autoNavigateEngagements = false,
                            )
                        } else {
                            FirstChatActionResult.ReturnHome(
                                session = current.session,
                                message = firstChatExitMessage(state),
                                hideFirstChatMatchId = current.matchId,
                                autoNavigateEngagements = false,
                            )
                        }
                    }

                    is MatchState.Unknown -> FirstChatActionResult.Show(
                        pending.copy(
                            match = result.value,
                            actionLoading = false,
                            actionLoadingLabel = null,
                            message = firstChatDecisionMessage(state),
                        )
                    )
                }
            }

            is ApiResult.Failure -> result.error.firstChatActionExpiryRoute(current)
                ?: refreshActionAfterPendingMutualCancellation(
                    error = result.error,
                    current = current,
                    pending = pending,
                )
                ?: FirstChatActionResult.Show(
                    pending.copy(
                        actionLoading = false,
                        actionLoadingLabel = null,
                        error = result.error,
                    )
                )
        }
    }

    suspend fun requestNextGuidanceQuestion(
        current: RealsRootUiState.FirstChat,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult {
        if (current.loading || current.refreshing || current.actionLoading || current.guidanceActionLoading) {
            return FirstChatActionResult.Ignore
        }
        if (current.hasPendingExitRequest()) {
            return FirstChatActionResult.Ignore
        }
        val chat = current.chat ?: return FirstChatActionResult.Ignore
        val guidance = chat.guidance ?: return FirstChatActionResult.Ignore
        if (guidance.completed || guidance.myNextRequested || !guidance.canRequestNext) {
            return FirstChatActionResult.Ignore
        }

        val pending = current.copy(
            guidanceActionLoading = true,
            error = null,
            message = null,
        )
        onPending(pending)

        return when (val result = dependencies.requestNextFirstChatGuidanceQuestion(chat.id)) {
            is ApiResult.Success -> FirstChatActionResult.Show(
                pending.copy(
                    chat = chat.copy(guidance = result.value),
                    guidanceActionLoading = false,
                    error = null,
                )
            )

            is ApiResult.Failure -> result.error.firstChatActionExpiryRoute(current)
                ?: refreshGuidanceAfterPendingMutualCancellation(
                    error = result.error,
                    pending = pending,
                    chatId = chat.id,
                )
                ?: FirstChatActionResult.Show(
                    pending.copy(
                        guidanceActionLoading = false,
                        error = result.error,
                    )
                )
        }
    }

    suspend fun requestMutualExit(
        current: RealsRootUiState.FirstChat,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult = runExitAction(
        current = current,
        successMessage = "Enviamos tu solicitud de salida consensuada.",
        loadingLabel = "Solicitando salida...",
        onPending = onPending,
    ) { chatId ->
        dependencies.requestMutualChatExit(chatId, ChatExitReason.NoLongerInterested, null)
    }

    suspend fun cancelUnilaterally(
        current: RealsRootUiState.FirstChat,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult = runExitAction(
        current = current,
        successMessage = "Cerraste el chat.",
        loadingLabel = "Cerrando chat...",
        onPending = onPending,
    ) { chatId ->
        dependencies.cancelChat(chatId, ChatExitReason.NoLongerInterested, null)
    }

    suspend fun safetyCancel(
        current: RealsRootUiState.FirstChat,
        reason: ChatExitReason,
        details: String,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
            return FirstChatActionResult.Ignore
        }
        if (current.chat == null) return FirstChatActionResult.Ignore

        val cleanDetails = normalizeSafetyReportDetails(details) ?: return FirstChatActionResult.Show(
            current.copy(
                error = invalidSafetyReportDetailsError(),
                message = null,
            )
        )

        return runExitAction(
            current = current,
            successMessage = "Reporte enviado. Cerramos ésta conversación por seguridad y no volveremos a cruzarte con ésta persona.",
            loadingLabel = "Enviando reporte...",
            onPending = onPending,
        ) { chatId ->
            dependencies.safetyCancelChat(
                chatId,
                reason,
                cleanDetails,
            )
        }
    }

    suspend fun acceptExitRequest(
        current: RealsRootUiState.FirstChat,
        exitRequestId: String,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult = runExitAction(
        current = current,
        successMessage = "Aceptaste la salida consensuada.",
        loadingLabel = "Aceptando salida...",
        onPending = onPending,
    ) { chatId ->
        dependencies.acceptChatExitRequest(chatId, exitRequestId)
    }

    suspend fun rejectExitRequest(
        current: RealsRootUiState.FirstChat,
        exitRequestId: String,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult = runExitAction(
        current = current,
        successMessage = "Rechazaste la salida consensuada.",
        loadingLabel = "Rechazando salida...",
        onPending = onPending,
    ) { chatId ->
        dependencies.rejectChatExitRequest(chatId, exitRequestId)
    }

    suspend fun timeoutExitRequest(
        current: RealsRootUiState.FirstChat,
        exitRequestId: String,
        onPending: (RealsRootUiState.FirstChat) -> Unit,
    ): FirstChatActionResult = runExitAction(
        current = current,
        successMessage = "La solicitud de salida venció.",
        loadingLabel = "Cerrando por timeout...",
        onPending = onPending,
    ) { chatId ->
        dependencies.timeoutChatExitRequest(chatId, exitRequestId)
    }

    private suspend fun runExitAction(
        current: RealsRootUiState.FirstChat,
        successMessage: String = "Actualizamos el estado del chat.",
        loadingLabel: String = "Procesando...",
        onPending: (RealsRootUiState.FirstChat) -> Unit,
        action: suspend (chatId: String) -> ApiResult<*>,
    ): FirstChatActionResult {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
            return FirstChatActionResult.Ignore
        }
        val chat = current.chat ?: return FirstChatActionResult.Ignore

        val pending = current.copy(
            actionLoading = true,
            actionLoadingLabel = loadingLabel,
            error = null,
            message = null,
        )
        onPending(pending)

        return when (val result = action(chat.id)) {
            is ApiResult.Success -> {
                val outcome = result.value as? ChatExitOutcome
                if (outcome != null && outcome.chat.status != ChatStatus.Active) {
                    return FirstChatActionResult.ReturnHome(
                        session = current.session,
                        message = successMessage,
                        hideFirstChatMatchId = current.matchId,
                    )
                }
                if (outcome?.exitRequest?.status.isResolvedExitStatus()) {
                    return FirstChatActionResult.ReturnHome(
                        session = current.session,
                        message = "El chat fue cerrado.",
                        hideFirstChatMatchId = current.matchId,
                    )
                }

                val chatResult = dependencies.getFirstChatForMatch(current.matchId)
                val exitsResult = dependencies.getChatExitRequests(chat.id)
                FirstChatActionResult.Show(
                    pending.copy(
                        chat = (chatResult as? ApiResult.Success)?.value ?: pending.chat,
                        exitRequests = (exitsResult as? ApiResult.Success)?.value
                            ?: pending.exitRequests,
                        actionLoading = false,
                        actionLoadingLabel = null,
                        message = successMessage,
                        error = (chatResult as? ApiResult.Failure)?.error
                            ?: (exitsResult as? ApiResult.Failure)?.error,
                    )
                )
            }

            is ApiResult.Failure -> FirstChatActionResult.Show(
                pending.copy(
                    actionLoading = false,
                    actionLoadingLabel = null,
                    error = result.error,
                )
            )
        }
    }

    private fun RealsRootUiState.FirstChat.hasPendingExitRequest(): Boolean =
        exitRequests.any { it.status == ChatExitRequestStatus.Pending }
}

internal sealed interface FirstChatLoadResult {
    data class Show(val state: RealsRootUiState.FirstChat) : FirstChatLoadResult
    data class RouteHome(val message: String) : FirstChatLoadResult
}

internal sealed interface FirstChatRefreshResult {
    data class Show(val state: RealsRootUiState.FirstChat) : FirstChatRefreshResult
    data class Reopen(val matchId: String, val chatId: String?) : FirstChatRefreshResult
    data class Closed(val matchState: MatchState?, val chatStatus: ChatStatus?) : FirstChatRefreshResult
    data class ExitResolved(val message: String) : FirstChatRefreshResult
}

internal sealed interface FirstChatSendResult {
    data class Show(val state: RealsRootUiState.FirstChat) : FirstChatSendResult

    data class ReturnHome(
        val session: ProvisionedSession,
        val message: String,
        val hideFirstChatMatchId: String,
    ) : FirstChatSendResult
}

internal sealed interface FirstChatActionResult {
    data object Ignore : FirstChatActionResult

    data class Show(val state: RealsRootUiState.FirstChat) : FirstChatActionResult

    data class ReturnHome(
        val session: ProvisionedSession,
        val message: String?,
        val hideFirstChatMatchId: String? = null,
        val autoNavigateEngagements: Boolean = false,
    ) : FirstChatActionResult

    data class ReloadHome(
        val session: ProvisionedSession,
        val message: String?,
        val hideFirstChatMatchId: String? = null,
        val autoNavigateEngagements: Boolean = false,
    ) : FirstChatActionResult
}

private fun ApiError.firstChatActionExpiryRoute(
    current: RealsRootUiState.FirstChat,
): FirstChatActionResult.ReturnHome? {
    val message = firstChatExpiryErrorMessage() ?: return null
    return FirstChatActionResult.ReturnHome(
        session = current.session,
        message = message,
        hideFirstChatMatchId = current.matchId,
        autoNavigateEngagements = false,
    )
}

private fun ApiError.firstChatSendExpiryRoute(
    current: RealsRootUiState.FirstChat,
): FirstChatSendResult.ReturnHome? {
    val message = firstChatExpiryErrorMessage() ?: return null
    return FirstChatSendResult.ReturnHome(
        session = current.session,
        message = message,
        hideFirstChatMatchId = current.matchId,
    )
}

private fun ApiError.firstChatExpiryErrorMessage(): String? {
    if (this !is ApiError.Backend) return null
    return when (backendErrorCode) {
        BackendErrorCode.ChatExpired -> "El chat venci\u00f3."
        BackendErrorCode.ChatAbandoned -> "La conversaci\u00f3n se cerr\u00f3 por inactividad."
        else -> null
    }
}

internal fun normalizeSafetyReportDetails(details: String): String? =
    TextSafety.normalizeMultiline(details, maxLength = 1_000)
        .takeUnless { it.isBlank() || TextSafety.containsHtmlLikeMarkup(it) }

internal fun invalidSafetyReportDetailsError(): ApiError =
    ApiError.Unexpected("El detalle del reporte no es válido.")
