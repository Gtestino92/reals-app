package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isUserPairBlocked
import com.reals.app.core.network.map
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatMessageType
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.CounterpartAudioAccess
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.ui.chat.firstChatLifecycleUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// Confined to the coordinator caller (Main). Each chat entry owns a new instance.
// Playback listeners are attached only for the lifetime of the composed session.
class ChatHistoryOwner {
    internal var access = CounterpartAudioAccess.UNKNOWN
        private set
    private val withdrawalListeners = mutableSetOf<() -> Unit>()
    private var generation = 0L
    private var participant: Pair<String, String>? = null
    private var needsBoundedRead = false
    internal var lifecycleRefreshScheduled = false

    internal fun observeWithdrawal(listener: () -> Unit): () -> Unit {
        withdrawalListeners.add(listener)
        return { withdrawalListeners.remove(listener) }
    }

    internal fun recheckPlayback() {
        withdrawalListeners.toList().forEach { it() }
    }

    internal fun withholdUntilFreshHistory() {
        if (access == CounterpartAudioAccess.RESTRICTED) return
        generation++
        access = CounterpartAudioAccess.UNKNOWN
        needsBoundedRead = true
        recheckPlayback()
    }

    internal fun restrict() {
        if (access == CounterpartAudioAccess.RESTRICTED) return
        access = CounterpartAudioAccess.RESTRICTED
        recheckPlayback()
    }

    internal suspend fun <T> observeResult(result: ApiResult<T>, onChanged: (ChatHistoryOwner) -> Unit): ApiResult<T> {
        currentCoroutineContext().ensureActive()
        if (result is ApiResult.Failure && result.error.isBlockedMediaConflict()) {
            restrict()
            onChanged(this)
        }
        return result
    }

    internal suspend fun read(
        getMessages: GetChatMessagesUseCase,
        onChanged: (ChatHistoryOwner) -> Unit,
        onMediaConflict: (ChatHistoryOwner) -> Unit,
        userId: String,
        chatId: String,
        afterMessageId: String? = null,
    ): ApiResult<List<ChatMessage>> {
        val identity = userId to chatId
        if (participant == null) participant = identity
        if (participant != identity) throw CancellationException("Obsolete chat history")
        val started = generation
        var authorizedGeneration: Long? = null
        val cursor = afterMessageId.takeUnless { needsBoundedRead && access == CounterpartAudioAccess.ALLOWED }
        val result = getMessages(chatId, cursor) { observed ->
            if (access != CounterpartAudioAccess.RESTRICTED) {
                val previous = access
                when (observed) {
                    CounterpartAudioAccess.UNKNOWN -> {
                        withholdUntilFreshHistory()
                    }
                    CounterpartAudioAccess.RESTRICTED -> restrict()
                    CounterpartAudioAccess.ALLOWED -> if (started == generation) {
                        access = observed
                        authorizedGeneration = generation
                    }
                }
                if (previous != access) onChanged(this)
            }
        }
        observeResult(result, onChanged)
        if (result is ApiResult.Failure && result.error.isBlockedMediaConflict()) onMediaConflict(this)
        return result.map { rows ->
            if (authorizedGeneration == generation && cursor == null) needsBoundedRead = false
            rows.filter { it.chatSessionId == chatId }.map { row ->
                if (row.senderId == userId || row.messageType != ChatMessageType.Audio) row
                else protect(row.copy(historyGeneration = authorizedGeneration), userId)
            }
        }
    }

    internal fun protect(row: ChatMessage, userId: String): ChatMessage {
        if (row.messageType != ChatMessageType.Audio || row.senderId == userId) return row
        if (access == CounterpartAudioAccess.ALLOWED && row.historyGeneration == generation) return row
        return ChatMessage(
            id = row.id, senderId = row.senderId, chatSessionId = row.chatSessionId,
            messageType = row.messageType, sentAt = row.sentAt, withdrawn = true,
        )
    }
}

internal fun RealsRootUiState.FirstChat.canPlayHistory(): Boolean =
    chat?.status?.isOpenFirstChatStatus() == true &&
        firstChatLifecycleUiState(chat)?.expired != true

internal fun RealsRootUiState.SecondChat.canPlayHistory(): Boolean {
    val current = chat ?: return false
    val status = lifecycle.status ?: return false
    if (status.chatId != current.id || current.status != status.chatStatus) return false
    if (current.status == ChatStatus.Active) return !lifecycle.timingPresentation().locallyExpired
    return lifecycle.remainingReadOnlyMillis()?.let { it > 0 } == true
}

internal fun SecondChatLifecycleUiState.remainingReadOnlyMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
    val current = status ?: return null
    if (current.chatStatus !in setOf(ChatStatus.Finished, ChatStatus.Expired, ChatStatus.Abandoned)) return null
    val until = current.readOnlyUntil ?: return null
    return current.remainingMillisFromServerSnapshot(until, statusReceivedAtMillis ?: return null, nowMillis)
}

internal fun ApiError.isBlockedMediaConflict(): Boolean =
    this is ApiError.Backend && statusCode == 409 && isUserPairBlocked()

internal val RealsRootUiState.chatHistoryOwner: ChatHistoryOwner?
    get() = when (this) {
        is RealsRootUiState.FirstChat -> historyOwner
        is RealsRootUiState.SecondChat -> historyOwner
        else -> null
    }

// Sender and message type are immutable for a confirmed ID. In particular an
// acknowledgement cannot reclassify a withdrawn partner row as our own audio.
private fun ChatHistoryOwner.protectRows(
    rows: List<ChatMessage>,
    displayed: List<ChatMessage>,
    userId: String,
): List<ChatMessage> {
    val previous = displayed.associateBy { it.id }
    return rows.map { row ->
        val known = previous[row.id]
        val candidate = if (known != null && (row.senderId != known.senderId || row.messageType != known.messageType)) {
            known
        } else row
        val protected = protect(candidate, userId)
        // Preserve a freshly recovered source when a coordinator carries an older generation.
        if (protected.withdrawn && known?.audio != null) protect(known, userId) else protected
    }
}

internal fun RealsRootUiState.protectChatHistory(displayed: RealsRootUiState? = null): RealsRootUiState = when (this) {
    is RealsRootUiState.FirstChat -> copy(
        counterpartAudioAccess = historyOwner.access,
        messages = historyOwner.protectRows(messages, (displayed as? RealsRootUiState.FirstChat)?.messages.orEmpty(), session.user.id),
    )
    is RealsRootUiState.SecondChat -> copy(
        counterpartAudioAccess = historyOwner.access,
        messages = historyOwner.protectRows(messages, (displayed as? RealsRootUiState.SecondChat)?.messages.orEmpty(), session.user.id),
    )
    else -> this
}
