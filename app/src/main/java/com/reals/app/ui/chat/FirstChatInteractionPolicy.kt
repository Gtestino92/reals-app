package com.reals.app.ui.chat

import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.isFirstChatDecisionOnly
import com.reals.app.domain.model.isFirstChatDecisionOnlyForCurrentUser

internal data class FirstChatInteractionPolicy(
    val decisionOnly: Boolean,
    val canSendMessages: Boolean,
    val canUseOrdinaryConversationActions: Boolean,
    val canRequestGuidance: Boolean,
    val canRequestOrdinaryExit: Boolean,
    val canDecide: Boolean,
    val canRetryFailedTextMessages: Boolean,
    val pollingEnabled: Boolean,
    val safetyAvailable: Boolean,
    val manualBlockAvailable: Boolean,
)

internal fun firstChatInteractionPolicy(
    chat: Chat?,
    canChat: Boolean,
    exitFlowLocked: Boolean,
    showDecisionActions: Boolean,
    matchIsChatActive: Boolean,
    firstChatLocallyExpired: Boolean,
    audioInteractionBusy: Boolean,
): FirstChatInteractionPolicy {
    val decisionOnly = chat?.isFirstChatDecisionOnly() == true
    val chatActive = chat?.status == ChatStatus.Active
    val ordinaryActionsAvailable = canChat && !decisionOnly
    val canDecide = showDecisionActions &&
        matchIsChatActive &&
        chatActive &&
        chat.myDecision == ChatDecisionState.Pending &&
        !exitFlowLocked &&
        !firstChatLocallyExpired &&
        !audioInteractionBusy

    return FirstChatInteractionPolicy(
        decisionOnly = decisionOnly,
        canSendMessages = ordinaryActionsAvailable && !exitFlowLocked,
        canUseOrdinaryConversationActions = ordinaryActionsAvailable,
        canRequestGuidance = ordinaryActionsAvailable && !exitFlowLocked,
        canRequestOrdinaryExit = ordinaryActionsAvailable && !exitFlowLocked,
        canDecide = canDecide,
        canRetryFailedTextMessages = !decisionOnly,
        pollingEnabled = canChat,
        safetyAvailable = canChat,
        manualBlockAvailable = true,
    )
}

internal data class FirstChatOverflowActionVisibility(
    val showMutualExit: Boolean,
    val showReject: Boolean,
    val showSafety: Boolean,
    val showManualBlock: Boolean,
)

@Suppress("UNUSED_PARAMETER")
internal fun firstChatOverflowActionVisibility(
    showMutualExitActions: Boolean,
    showDecisionActions: Boolean,
    decisionOnlyForCurrentUser: Boolean,
    canRequestOrdinaryExit: Boolean,
    canDecide: Boolean,
    canUseSafetyActions: Boolean,
    canManualBlock: Boolean,
): FirstChatOverflowActionVisibility =
    FirstChatOverflowActionVisibility(
        showMutualExit = showMutualExitActions && canRequestOrdinaryExit,
        showReject = showDecisionActions && !decisionOnlyForCurrentUser,
        showSafety = true,
        showManualBlock = true,
    )

internal data class FirstChatDecisionOnlyPanelState(
    val visible: Boolean,
    val approvalCopy: String?,
    val prompt: String,
)

internal fun firstChatDecisionOnlyPanelState(
    chat: Chat?,
    partnerName: String?,
): FirstChatDecisionOnlyPanelState {
    val visible = chat?.isFirstChatDecisionOnlyForCurrentUser() == true
    val partnerLabel = partnerName
        ?.takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it) }
        ?: "La otra persona"
    return FirstChatDecisionOnlyPanelState(
        visible = visible,
        approvalCopy = if (visible) "$partnerLabel aprobó el chat." else null,
        prompt = "El chat está pausado mientras decidís. ¿Querés aprobar también?",
    )
}

internal data class FirstChatComposerPresentationPolicy(
    val visible: Boolean,
    val interactive: Boolean,
)

internal fun firstChatComposerPresentationPolicy(
    canSendMessages: Boolean,
    audioInteractionBusy: Boolean,
): FirstChatComposerPresentationPolicy =
    FirstChatComposerPresentationPolicy(
        visible = canSendMessages || audioInteractionBusy,
        interactive = canSendMessages,
    )
