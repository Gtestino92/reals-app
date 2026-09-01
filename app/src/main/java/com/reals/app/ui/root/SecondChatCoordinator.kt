package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.di.SecondChatFeatureDependencies
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageReactionType
import com.reals.app.domain.model.ChatReplyTarget
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatCompletionDecision
import com.reals.app.domain.model.SecondChatEndedReason
import com.reals.app.domain.model.SecondChatResolutionRequestStatus
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.domain.model.SecondChatStatus
import java.io.File

internal class SecondChatCoordinator(
    private val dependencies: SecondChatFeatureDependencies,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val silentMessagePollingCursor = SilentChatMessagePollingCursor()

    suspend fun load(
        session: ProvisionedSession,
        connectionId: String,
        matchId: String,
        partnerName: String?,
        joinIfAllowed: Boolean = true,
    ): SecondChatLoadResult {
        val initial = RealsRootUiState.SecondChat(
            session = session,
            connectionId = connectionId,
            matchId = matchId,
            partnerName = partnerName,
            loading = true,
        )
        return when (val statusResult = dependencies.getStatus(connectionId)) {
            is ApiResult.Failure -> SecondChatLoadResult.Show(
                initial.copy(
                    loading = false,
                    error = statusResult.error,
                )
            )
            is ApiResult.Success -> openFromStatus(
                current = initial,
                statusSnapshot = statusResult.value.receivedNow(),
                joinIfAllowed = joinIfAllowed,
                loading = false,
            )
        }
    }

    suspend fun refresh(
        current: RealsRootUiState.SecondChat,
        silent: Boolean,
        useReactionReconciliationAlternation: Boolean = false,
    ): SecondChatLoadResult {
        val pending = current.copy(
            refreshing = true,
            error = if (silent) current.error else null,
            message = if (silent) current.message else null,
        )
        return when (val statusResult = dependencies.getStatus(current.connectionId)) {
            is ApiResult.Failure -> SecondChatLoadResult.Show(
                pending.copy(
                    refreshing = false,
                    error = if (silent) pending.error else statusResult.error,
                )
            )
            is ApiResult.Success -> openFromStatus(
                current = pending,
                statusSnapshot = statusResult.value.receivedNow(),
                joinIfAllowed = false,
                loading = false,
                useReactionReconciliationAlternation = silent && useReactionReconciliationAlternation,
            )
        }
    }

    suspend fun refreshMessagesForAudioPlayback(
        current: RealsRootUiState.SecondChat,
    ): RealsRootUiState.SecondChat {
        val chat = current.chat ?: return current
        val statusResult = dependencies.getStatus(current.connectionId)
        val statusSnapshot = (statusResult as? ApiResult.Success)?.value?.receivedNow()
        val messagesResult = dependencies.getChatMessages(chat.id, afterMessageId = null)
        val chatResult = dependencies.getChat(chat.id)
        return current.copy(
            lifecycle = statusSnapshot?.let(current.lifecycle::withStatusSnapshot)
                ?: current.lifecycle,
            chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
            messages = (messagesResult as? ApiResult.Success)?.value
                ?.let { current.messages.appendUnique(it) }
                ?: current.messages,
            error = (statusResult as? ApiResult.Failure)?.error
                ?: (messagesResult as? ApiResult.Failure)?.error
                ?: (chatResult as? ApiResult.Failure)?.error
                ?: current.error,
        )
    }

    suspend fun loadFullMessagesForAudioPlayback(
        current: RealsRootUiState.SecondChat,
    ): ApiResult<List<ChatMessage>> {
        val chat = current.chat ?: return ApiResult.Success(current.messages)
        return dependencies.getChatMessages(chat.id, afterMessageId = null)
    }

    suspend fun putMessageReaction(
        chatId: String,
        messageId: String,
        reactionType: ChatMessageReactionType,
    ): ApiResult<ChatMessage> =
        dependencies.putMessageReaction(chatId, messageId, reactionType)

    suspend fun reconcileMessagesForReactions(
        current: RealsRootUiState.SecondChat,
    ): ApiResult<List<ChatMessage>> {
        val chat = current.chat ?: return ApiResult.Success(emptyList())
        return dependencies.getChatMessages(chat.id, current.messages.reactionReconciliationCursor())
    }

    suspend fun createNoShowClaim(
        current: RealsRootUiState.SecondChat,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatLoadResult {
        val status = current.lifecycle.status ?: return SecondChatLoadResult.Show(current)
        if (!status.canClaimPartnerNoShow || current.lifecycle.claimingNoShow || current.audioUpload.uploading) {
            return SecondChatLoadResult.Show(current)
        }
        val pending = current.copy(
            lifecycle = current.lifecycle.copy(claimingNoShow = true),
            actionLoading = true,
            actionLoadingLabel = "Enviando solicitud...",
            error = null,
            message = null,
        )
        onPending(pending)
        return when (val result = dependencies.createNoShowClaim(current.connectionId)) {
            is ApiResult.Success -> openFromStatus(
                current = pending.copy(
                    lifecycle = pending.lifecycle.copy(claimingNoShow = false),
                    actionLoading = false,
                    actionLoadingLabel = null,
                ),
                statusSnapshot = result.value.receivedNow(),
                joinIfAllowed = false,
                loading = false,
            )
            is ApiResult.Failure -> {
                val refreshed = refresh(
                    pending.copy(
                        lifecycle = pending.lifecycle.copy(claimingNoShow = false),
                        actionLoading = false,
                        actionLoadingLabel = null,
                        error = result.error,
                    ),
                    silent = true,
                )
                refreshed
            }
        }
    }

    suspend fun createCompletionRequest(
        current: RealsRootUiState.SecondChat,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatLoadResult {
        val status = current.lifecycle.status ?: return SecondChatLoadResult.Show(current)
        if (!canRunSecondChatResolutionAction(current) ||
            !status.canRequestMutualCompletion ||
            status.hasPendingCompletionOrInactivityRequest()
        ) {
            return SecondChatLoadResult.Show(current)
        }
        val pending = current.pendingSecondChatAction("Enviando solicitud...")
        onPending(pending)
        return when (val result = dependencies.createCompletionRequest(current.connectionId)) {
            is ApiResult.Success -> openFromStatus(
                current = pending.withoutSecondChatActionLoading(),
                statusSnapshot = result.value.receivedNow(),
                joinIfAllowed = false,
                loading = false,
            )
            is ApiResult.Failure -> refreshAfterSecondChatActionFailure(pending, result.error)
        }
    }

    suspend fun decideCompletionRequest(
        current: RealsRootUiState.SecondChat,
        requestId: String,
        decision: SecondChatCompletionDecision,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatLoadResult {
        val activeRequest = current.lifecycle
            .resolutionPresentation(
                currentUserId = current.session.user.id,
                nowMillis = nowMillis(),
            )
            .activeRequest
        if (
            !canRunSecondChatResolutionAction(current) ||
            activeRequest?.requestId != requestId ||
            activeRequest.type != SecondChatResolutionRequestType.MutualCompletion ||
            !activeRequest.showAcceptRejectControls ||
            !activeRequest.controlsEnabled
        ) {
            return SecondChatLoadResult.Show(current)
        }
        val pending = current.pendingSecondChatAction(
            when (decision) {
                SecondChatCompletionDecision.Accepted -> "Finalizando chat..."
                SecondChatCompletionDecision.Rejected -> "Continuando chat..."
            }
        )
        onPending(pending)
        return when (val result = dependencies.decideCompletionRequest(
            current.connectionId,
            requestId,
            decision,
        )) {
            is ApiResult.Success -> openFromStatus(
                current = pending.withoutSecondChatActionLoading(),
                statusSnapshot = result.value.receivedNow(),
                joinIfAllowed = false,
                loading = false,
            )
            is ApiResult.Failure -> refreshAfterSecondChatActionFailure(pending, result.error)
        }
    }

    suspend fun createInactivityClaim(
        current: RealsRootUiState.SecondChat,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatLoadResult {
        val status = current.lifecycle.status ?: return SecondChatLoadResult.Show(current)
        if (!canRunSecondChatResolutionAction(current) ||
            !status.canClaimPartnerInactivity ||
            status.hasPendingCompletionOrInactivityRequest()
        ) {
            return SecondChatLoadResult.Show(current)
        }
        val pending = current.pendingSecondChatAction("Enviando reclamo...")
        onPending(pending)
        return when (val result = dependencies.createInactivityClaim(current.connectionId)) {
            is ApiResult.Success -> openFromStatus(
                current = pending.withoutSecondChatActionLoading(),
                statusSnapshot = result.value.receivedNow(),
                joinIfAllowed = false,
                loading = false,
            )
            is ApiResult.Failure -> refreshAfterSecondChatActionFailure(pending, result.error)
        }
    }

    suspend fun sendMessage(
        current: RealsRootUiState.SecondChat,
        cleanContent: String,
        localId: String,
        replyTo: ChatReplyTarget? = null,
    ): RealsRootUiState.SecondChat {
        val chat = current.chat ?: return current
        val cursorBeforeSend = current.messages.lastMessageCursor()

        return when (val result = dependencies.sendChatMessage(chat.id, cleanContent, localId, replyTo)) {
            is ApiResult.Success -> {
                val statusResult = dependencies.getStatus(current.connectionId)
                val statusSnapshot = (statusResult as? ApiResult.Success)?.value?.receivedNow()
                val messagesResult = dependencies.getChatMessages(chat.id, cursorBeforeSend)
                val chatResult = dependencies.getChat(chat.id)
                current.copy(
                    lifecycle = statusSnapshot?.let(current.lifecycle::withStatusSnapshot)
                        ?: current.lifecycle,
                    chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
                    messages = current.messages.appendUnique(
                        (messagesResult as? ApiResult.Success)?.value.orEmpty() + result.value
                    ),
                    optimisticMessages = current.optimisticMessages.filterNot { it.localId == localId },
                    sending = false,
                    error = (statusResult as? ApiResult.Failure)?.error
                        ?: (messagesResult as? ApiResult.Failure)?.error
                        ?: (chatResult as? ApiResult.Failure)?.error,
                )
            }

            is ApiResult.Failure -> {
                val refreshed = refresh(
                    current.copy(
                        optimisticMessages = current.optimisticMessages.markOptimisticMessageFailed(localId),
                        sending = false,
                        error = result.error,
                    ),
                    silent = true,
                )
                (refreshed as? SecondChatLoadResult.Show)?.state
                    ?: current.copy(
                        optimisticMessages = current.optimisticMessages.markOptimisticMessageFailed(localId),
                        sending = false,
                        error = result.error,
                    )
            }
        }
    }

    suspend fun sendAudioMessage(
        current: RealsRootUiState.SecondChat,
        file: File,
        clientMessageId: String,
        replyTo: ChatReplyTarget? = null,
    ): RealsRootUiState.SecondChat {
        val chat = current.chat ?: return current
        val cursorBeforeSend = current.messages.lastMessageCursor()

        return when (val result = dependencies.sendChatAudioMessage(
            chat.id,
            file,
            clientMessageId,
            replyTo,
        )) {
            is ApiResult.Success -> {
                val statusResult = dependencies.getStatus(current.connectionId)
                val statusSnapshot = (statusResult as? ApiResult.Success)?.value?.receivedNow()
                val messagesResult = dependencies.getChatMessages(chat.id, cursorBeforeSend)
                val chatResult = dependencies.getChat(chat.id)
                current.copy(
                    lifecycle = statusSnapshot?.let(current.lifecycle::withStatusSnapshot)
                        ?: current.lifecycle,
                    chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
                    messages = current.messages.appendUnique(
                        (messagesResult as? ApiResult.Success)?.value.orEmpty() + result.value
                    ),
                    optimisticMessages = current.optimisticMessages.withoutOptimisticMessage(clientMessageId),
                    audioUpload = ChatAudioUploadUiState(completedClientMessageId = clientMessageId),
                    error = (statusResult as? ApiResult.Failure)?.error
                        ?: (messagesResult as? ApiResult.Failure)?.error
                        ?: (chatResult as? ApiResult.Failure)?.error,
                )
            }

            is ApiResult.Failure -> {
                val failed = current.copy(
                    optimisticMessages = current.optimisticMessages.withoutOptimisticMessage(clientMessageId),
                    audioUpload = ChatAudioUploadUiState(
                        uploading = false,
                        error = result.error,
                        nonRetryable = result.error.isAudioIdempotencyConflict(),
                    ),
                    error = null,
                )
                val refreshed = if (result.error.isSecondChatAudioLifecycleConflict()) {
                    refresh(failed, silent = true)
                } else {
                    null
                }
                (refreshed as? SecondChatLoadResult.Show)
                    ?.state
                    ?.withTerminalAudioDraftDiscardedIfNeeded(file)
                    ?: failed
            }
        }
    }

    suspend fun safetyCancel(
        current: RealsRootUiState.SecondChat,
        reason: ChatExitReason,
        details: String,
        blockUser: Boolean,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatActionResult {
        if (current.loading || current.refreshing || current.sending || current.audioUpload.uploading || current.actionLoading) {
            return SecondChatActionResult.Ignore
        }
        if (current.lifecycle.status != null && !current.lifecycle.timingPresentation().genuinelyActive) {
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
            reason,
            cleanDetails,
            blockUser,
        )) {
            is ApiResult.Success -> SecondChatActionResult.ReturnHome(
                session = current.session,
                message = safetyReportSuccessMessage(blockUser),
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

    private suspend fun openFromStatus(
        current: RealsRootUiState.SecondChat,
        statusSnapshot: ReceivedSecondChatStatus,
        joinIfAllowed: Boolean,
        loading: Boolean,
        useReactionReconciliationAlternation: Boolean = false,
    ): SecondChatLoadResult {
        val status = statusSnapshot.status
        val authoritativeSnapshot = if (joinIfAllowed && status.canJoin) {
            val joining = current.copy(
                lifecycle = current.lifecycle.withStatusSnapshot(statusSnapshot).copy(joining = true),
                loading = loading,
            )
            when (val join = dependencies.join(current.connectionId)) {
                is ApiResult.Success -> join.value.receivedNow()
                is ApiResult.Failure -> return SecondChatLoadResult.Show(
                    joining.copy(
                        lifecycle = joining.lifecycle.copy(joining = false),
                        loading = false,
                        error = join.error,
                    )
                )
            }
        } else {
            statusSnapshot
        }
        val authoritativeStatus = authoritativeSnapshot.status

        val lifecycle = current.lifecycle.withStatusSnapshot(authoritativeSnapshot).copy(
            joining = false,
            joinCompletedInThisSession = current.lifecycle.joinCompletedInThisSession ||
                (joinIfAllowed && status.canJoin),
        )
        val withLifecycle = current.copy(
            lifecycle = lifecycle,
            chatId = authoritativeStatus.chatId ?: current.chatId,
            loading = loading,
            refreshing = false,
        )

        if (!authoritativeSnapshot.isReadableAtReceipt()) {
            return SecondChatLoadResult.Show(
                withLifecycle.copy(
                    chat = null,
                    messages = emptyList(),
                    loading = false,
                )
            )
        }

        val chatId = authoritativeStatus.chatId ?: return SecondChatLoadResult.Show(
            withLifecycle.copy(loading = false)
        )
        return loadChatAndMessages(
            current = withLifecycle,
            chatId = chatId,
            useReactionReconciliationAlternation = useReactionReconciliationAlternation,
        )
    }

    private fun SecondChatStatus.receivedNow(): ReceivedSecondChatStatus =
        ReceivedSecondChatStatus(
            status = this,
            receivedAtMillis = nowMillis(),
        )

    private fun canRunSecondChatResolutionAction(current: RealsRootUiState.SecondChat): Boolean =
        !current.loading &&
            !current.refreshing &&
            !current.sending &&
            !current.audioUpload.uploading &&
            !current.actionLoading &&
            !current.manualBlock.loading &&
            current.lifecycle.timingPresentation(nowMillis()).genuinelyActive

    private suspend fun refreshAfterSecondChatActionFailure(
        pending: RealsRootUiState.SecondChat,
        error: ApiError,
    ): SecondChatLoadResult = refresh(
        pending.withoutSecondChatActionLoading().copy(error = error),
        silent = true,
    )

    private suspend fun loadChatAndMessages(
        current: RealsRootUiState.SecondChat,
        chatId: String,
        useReactionReconciliationAlternation: Boolean = false,
    ): SecondChatLoadResult {
        val chatResult = dependencies.getChat(chatId)
        val cursor = when {
            current.chat?.id != chatId -> {
                silentMessagePollingCursor.reset(chatId)
                null
            }
            useReactionReconciliationAlternation -> silentMessagePollingCursor.nextCursor(chatId, current.messages)
            else -> current.messages.lastMessageCursor()
        }
        val messagesResult = dependencies.getChatMessages(chatId, cursor)
        return SecondChatLoadResult.Show(
            current.copy(
                chat = (chatResult as? ApiResult.Success)?.value ?: current.chat,
                messages = (messagesResult as? ApiResult.Success)?.value
                    ?.let { current.messages.appendUnique(it) }
                    ?: current.messages,
                loading = false,
                refreshing = false,
                error = (chatResult as? ApiResult.Failure)?.error
                    ?: (messagesResult as? ApiResult.Failure)?.error
                    ?: current.error,
            )
        )
    }
}

private fun RealsRootUiState.SecondChat.pendingSecondChatAction(
    label: String,
): RealsRootUiState.SecondChat = copy(
    actionLoading = true,
    actionLoadingLabel = label,
    error = null,
    message = null,
)

private fun RealsRootUiState.SecondChat.withoutSecondChatActionLoading(): RealsRootUiState.SecondChat =
    copy(
        actionLoading = false,
        actionLoadingLabel = null,
    )

internal fun ReceivedSecondChatStatus.isReadableAtReceipt(): Boolean =
    status.chatId?.isNotBlank() == true &&
        (
            status.chatStatus == ChatStatus.Active ||
                (
                    status.chatStatus in terminalReadableSecondChatStatuses &&
                        status.readOnlyUntil != null &&
                        remainingMillisAtReceipt(status.readOnlyUntil)?.let { it > 0 } == true
                    )
            )

internal fun SecondChatStatus.isWaitingForPartner(): Boolean =
    chatStatus == ChatStatus.Active &&
        myAttendanceStatus in listOf(SecondChatAttendanceStatus.OnTime, SecondChatAttendanceStatus.Late) &&
        partnerAttendanceStatus == SecondChatAttendanceStatus.Pending

internal fun SecondChatStatus.hasPendingNoShowClaim(): Boolean =
    activeNoShowClaim?.type == SecondChatResolutionRequestType.PartnerNoShow &&
        activeNoShowClaim.status == SecondChatResolutionRequestStatus.Pending

internal fun SecondChatStatus.hasPendingCompletionOrInactivityRequest(): Boolean =
    activeResolutionRequest?.status == SecondChatResolutionRequestStatus.Pending &&
        (
            activeResolutionRequest.type == SecondChatResolutionRequestType.MutualCompletion ||
                activeResolutionRequest.type == SecondChatResolutionRequestType.PartnerInactivity
            )

internal fun SecondChatEndedReason?.secondChatResultCopy(): String = when (this) {
    SecondChatEndedReason.NoShow -> "La cita terminó porque una de las personas no llegó."
    SecondChatEndedReason.MutualCompletion -> "La cita terminó de común acuerdo."
    SecondChatEndedReason.PartnerInactivity -> "La cita terminó por falta de respuesta."
    SecondChatEndedReason.NoConversationStarted -> "La cita terminó porque no se inició la conversación."
    SecondChatEndedReason.AbsoluteTimeout -> "La cita alcanzó su tiempo máximo."
    is SecondChatEndedReason.Unknown,
    null -> "La cita terminó."
}

internal fun ChatStatus.isOpenSecondChatStatus(): Boolean = this == ChatStatus.Active

private val terminalReadableSecondChatStatuses = setOf(
    ChatStatus.Finished,
    ChatStatus.Abandoned,
    ChatStatus.Expired,
)

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

private fun ApiError.isSecondChatAudioLifecycleConflict(): Boolean =
    isAudioPolicyConflict() ||
        this is ApiError.Backend &&
        backendErrorCode in setOf(
            BackendErrorCode.ChatNotAvailable,
            BackendErrorCode.SecondChatExpired,
            BackendErrorCode.SecondChatAlreadyResolved,
            BackendErrorCode.SecondChatConversationAlreadyResolved,
            BackendErrorCode.SecondChatJoinRequired,
        )

private fun RealsRootUiState.SecondChat.withTerminalAudioDraftDiscardedIfNeeded(
    file: File,
): RealsRootUiState.SecondChat {
    val writable = chat?.status == ChatStatus.Active &&
        lifecycle.timingPresentation().genuinelyActive
    if (writable) return this
    runCatching { file.delete() }
    return copy(
        audioDraft = null,
        audioUpload = ChatAudioUploadUiState(),
    )
}
