package com.reals.app.ui.chat

import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.isFirstChatDecisionOnly

internal data class FirstChatInteractionPolicy(
    val decisionOnly: Boolean,
    val canSendMessages: Boolean,
    val canUseOrdinaryConversationActions: Boolean,
    val canRequestGuidance: Boolean,
    val canRequestOrdinaryExit: Boolean,
    val canDecide: Boolean,
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
        chat?.myDecision == ChatDecisionState.Pending &&
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
        pollingEnabled = canChat,
        safetyAvailable = canChat,
        manualBlockAvailable = true,
    )
}
