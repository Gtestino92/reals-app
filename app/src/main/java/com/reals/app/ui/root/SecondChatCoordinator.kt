package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.di.SecondChatFeatureDependencies
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatEndedReason
import com.reals.app.domain.model.SecondChatResolutionRequestStatus
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.domain.model.SecondChatStatus

internal class SecondChatCoordinator(
    private val dependencies: SecondChatFeatureDependencies,
) {
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
                status = statusResult.value,
                joinIfAllowed = joinIfAllowed,
                loading = false,
            )
        }
    }

    suspend fun refresh(
        current: RealsRootUiState.SecondChat,
        silent: Boolean,
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
                status = statusResult.value,
                joinIfAllowed = false,
                loading = false,
            )
        }
    }

    suspend fun createNoShowClaim(
        current: RealsRootUiState.SecondChat,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatLoadResult {
        val status = current.lifecycle.status ?: return SecondChatLoadResult.Show(current)
        if (!status.canClaimPartnerNoShow || current.lifecycle.claimingNoShow) {
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
                status = result.value,
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

    suspend fun sendMessage(
        current: RealsRootUiState.SecondChat,
        cleanContent: String,
        localId: String,
    ): RealsRootUiState.SecondChat {
        val chat = current.chat ?: return current
        val cursorBeforeSend = current.messages.lastMessageCursor()

        return when (val result = dependencies.sendChatMessage(chat.id, cleanContent)) {
            is ApiResult.Success -> {
                val statusResult = dependencies.getStatus(current.connectionId)
                val messagesResult = dependencies.getChatMessages(chat.id, cursorBeforeSend)
                val chatResult = dependencies.getChat(chat.id)
                current.copy(
                    lifecycle = current.lifecycle.copy(
                        status = (statusResult as? ApiResult.Success)?.value ?: current.lifecycle.status,
                    ),
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

    suspend fun safetyCancel(
        current: RealsRootUiState.SecondChat,
        reason: ChatExitReason,
        details: String,
        onPending: (RealsRootUiState.SecondChat) -> Unit,
    ): SecondChatActionResult {
        if (current.loading || current.refreshing || current.sending || current.actionLoading) {
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
        )) {
            is ApiResult.Success -> SecondChatActionResult.ReturnHome(
                session = current.session,
                message = "Reporte enviado. Cerramos esta conversación por seguridad y no volveremos a cruzarte con esta persona.",
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
        status: SecondChatStatus,
        joinIfAllowed: Boolean,
        loading: Boolean,
    ): SecondChatLoadResult {
        val authoritativeStatus = if (joinIfAllowed && status.canJoin) {
            val joining = current.copy(
                lifecycle = current.lifecycle.copy(status = status, joining = true),
                loading = loading,
            )
            when (val join = dependencies.join(current.connectionId)) {
                is ApiResult.Success -> join.value
                is ApiResult.Failure -> return SecondChatLoadResult.Show(
                    joining.copy(
                        lifecycle = joining.lifecycle.copy(joining = false),
                        loading = false,
                        error = join.error,
                    )
                )
            }
        } else {
            status
        }

        val lifecycle = current.lifecycle.copy(
            status = authoritativeStatus,
            statusReceivedAtMillis = System.currentTimeMillis(),
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

        if (!authoritativeStatus.isReadableNow()) {
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
        return loadChatAndMessages(withLifecycle, chatId)
    }

    private suspend fun loadChatAndMessages(
        current: RealsRootUiState.SecondChat,
        chatId: String,
    ): SecondChatLoadResult {
        val chatResult = dependencies.getChat(chatId)
        val cursor = if (current.chat?.id == chatId) current.messages.lastMessageCursor() else null
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

internal fun SecondChatStatus.isReadableNow(nowMillis: Long = System.currentTimeMillis()): Boolean =
    chatId?.isNotBlank() == true &&
        (
            chatStatus == ChatStatus.Active ||
                (
                    chatStatus in terminalReadableSecondChatStatuses &&
                        readOnlyUntil != null &&
                        remainingMillisFromServer(readOnlyUntil, nowMillis) > 0
                    )
            )

internal fun SecondChatStatus.isTerminalNoLongerReadable(nowMillis: Long = System.currentTimeMillis()): Boolean =
    chatStatus in terminalReadableSecondChatStatuses && !isReadableNow(nowMillis)

internal fun SecondChatStatus.isWaitingForPartner(): Boolean =
    chatStatus == ChatStatus.Active &&
        myAttendanceStatus in listOf(SecondChatAttendanceStatus.OnTime, SecondChatAttendanceStatus.Late) &&
        partnerAttendanceStatus == SecondChatAttendanceStatus.Pending

internal fun SecondChatStatus.hasPendingNoShowClaim(): Boolean =
    activeNoShowClaim?.type == SecondChatResolutionRequestType.PartnerNoShow &&
        activeNoShowClaim.status == SecondChatResolutionRequestStatus.Pending

internal fun SecondChatStatus.remainingMillisFromServer(
    targetTime: String,
    nowMillis: Long = System.currentTimeMillis(),
): Long = remainingMillisFromServerSnapshot(
    targetTime = targetTime,
    statusReceivedAtMillis = nowMillis,
    nowMillis = nowMillis,
) ?: 0

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
