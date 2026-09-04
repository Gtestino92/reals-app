package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.root.OptimisticOutgoingMessageType
import com.reals.app.ui.root.OutgoingMessageDeliveryState
import com.reals.app.ui.root.newOptimisticOutgoingMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstChatInteractionPolicyTest {
    @Test
    fun `decision-only freezes ordinary actions while keeping decision safety block and polling`() {
        val chat = TestDtos.chat(
            myDecision = "PENDING",
            partnerDecision = "APPROVED",
        ).toDomain()

        val policy = firstChatInteractionPolicy(
            chat = chat,
            canChat = true,
            exitFlowLocked = false,
            showDecisionActions = true,
            matchIsChatActive = true,
            firstChatLocallyExpired = false,
            audioInteractionBusy = false,
        )

        assertTrue(policy.decisionOnly)
        assertFalse(policy.canSendMessages)
        assertFalse(policy.canUseOrdinaryConversationActions)
        assertFalse(policy.canRequestGuidance)
        assertFalse(policy.canRequestOrdinaryExit)
        assertTrue(policy.canDecide)
        assertFalse(policy.canRetryFailedTextMessages)
        assertTrue(policy.pollingEnabled)
        assertTrue(policy.safetyAvailable)
        assertTrue(policy.manualBlockAvailable)
    }

    @Test
    fun `decision-only overflow hides mutual exit and duplicate reject while keeping safety and block`() {
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = true,
            canRequestOrdinaryExit = false,
            canDecide = true,
            canUseSafetyActions = true,
            canManualBlock = true,
        )

        assertFalse(visibility.showMutualExit)
        assertFalse(visibility.showReject)
        assertTrue(visibility.showSafety)
        assertTrue(visibility.showManualBlock)
    }

    @Test
    fun `reject remains visible when decision action is temporarily disabled`() {
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = false,
            canRequestOrdinaryExit = true,
            canDecide = false,
            canUseSafetyActions = true,
            canManualBlock = true,
        )

        assertTrue(visibility.showReject)
    }

    @Test
    fun `safety remains visible when safety action is temporarily disabled`() {
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = false,
            canRequestOrdinaryExit = true,
            canDecide = true,
            canUseSafetyActions = false,
            canManualBlock = true,
        )

        assertTrue(visibility.showSafety)
    }

    @Test
    fun `manual block remains visible when block action is temporarily disabled`() {
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = false,
            canRequestOrdinaryExit = true,
            canDecide = true,
            canUseSafetyActions = true,
            canManualBlock = false,
        )

        assertTrue(visibility.showManualBlock)
    }

    @Test
    fun `normal first-chat overflow keeps mutual exit presentation`() {
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = false,
            canRequestOrdinaryExit = true,
            canDecide = true,
            canUseSafetyActions = true,
            canManualBlock = true,
        )

        assertTrue(visibility.showMutualExit)
        assertTrue(visibility.showReject)
        assertTrue(visibility.showSafety)
        assertTrue(visibility.showManualBlock)
    }

    @Test
    fun `normal active first chat keeps approve actionable and reject in overflow`() {
        val chat = TestDtos.chat(
            myDecision = "PENDING",
            partnerDecision = "PENDING",
        ).toDomain()

        val policy = firstChatInteractionPolicy(
            chat = chat,
            canChat = true,
            exitFlowLocked = false,
            showDecisionActions = true,
            matchIsChatActive = true,
            firstChatLocallyExpired = false,
            audioInteractionBusy = false,
        )
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = false,
            canRequestOrdinaryExit = policy.canRequestOrdinaryExit,
            canDecide = policy.canDecide,
            canUseSafetyActions = policy.safetyAvailable,
            canManualBlock = policy.manualBlockAvailable,
        )

        assertTrue(policy.canDecide)
        assertTrue(visibility.showReject)
    }

    @Test
    fun `failed optimistic text retry follows first-chat retry policy`() {
        val failedText = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "hola",
            localId = "local-1",
            createdAtMillis = 1L,
        ).copy(deliveryState = OutgoingMessageDeliveryState.Failed)

        assertTrue(optimisticTextRetryAvailable(failedText, canRetryFailedTextMessages = true))
        assertFalse(optimisticTextRetryAvailable(failedText, canRetryFailedTextMessages = false))
    }

    @Test
    fun `audio optimistic messages do not expose text retry`() {
        val failedAudio = newOptimisticOutgoingMessage(
            chatId = "chat-1",
            senderId = "user-1",
            content = "",
            localId = "local-1",
            createdAtMillis = 1L,
        ).copy(
            deliveryState = OutgoingMessageDeliveryState.Failed,
            messageType = OptimisticOutgoingMessageType.Audio,
        )

        assertFalse(optimisticTextRetryAvailable(failedAudio, canRetryFailedTextMessages = true))
    }

    @Test
    fun `pending decision copy discloses partner approval with partner name`() {
        val copy = chatDecisionSummary(
            myDecision = ChatDecisionState.Pending,
            partnerDecision = ChatDecisionState.Approved,
            partnerName = "Alex",
        )

        assertEquals("Alex aprob\u00f3 el chat. Ahora te toca decidir.", copy)
    }

    @Test
    fun `pending decision copy discloses partner approval with fallback`() {
        val copy = chatDecisionSummary(
            myDecision = ChatDecisionState.Pending,
            partnerDecision = ChatDecisionState.Approved,
            partnerName = " ",
        )

        assertEquals("La otra persona aprob\u00f3 el chat. Ahora te toca decidir.", copy)
    }

    @Test
    fun `decision-only panel shows approval choices with partner name`() {
        val state = firstChatDecisionOnlyPanelState(
            chat = TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
            ).toDomain(),
            partnerName = "Alex",
        )

        assertTrue(state.visible)
        assertEquals("Alex aprob\u00f3 el chat.", state.approvalCopy)
    }

    @Test
    fun `decision-only panel shows fallback approval copy`() {
        val state = firstChatDecisionOnlyPanelState(
            chat = TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "APPROVED",
            ).toDomain(),
            partnerName = " ",
        )

        assertTrue(state.visible)
        assertEquals("La otra persona aprob\u00f3 el chat.", state.approvalCopy)
    }

    @Test
    fun `normal first chat does not show decision-only panel`() {
        val state = firstChatDecisionOnlyPanelState(
            chat = TestDtos.chat(
                myDecision = "PENDING",
                partnerDecision = "PENDING",
            ).toDomain(),
            partnerName = "Alex",
        )

        assertFalse(state.visible)
    }

    @Test
    fun `composer is hidden in decision-only when no audio interaction is active`() {
        val policy = firstChatComposerPresentationPolicy(
            canSendMessages = false,
            decisionOnlyForCurrentUser = true,
            audioInteractionBusy = false,
        )

        assertFalse(policy.visible)
        assertFalse(policy.interactive)
    }

    @Test
    fun `normal active first chat composer remains visible and interactive`() {
        val policy = firstChatComposerPresentationPolicy(
            canSendMessages = true,
            decisionOnlyForCurrentUser = false,
            audioInteractionBusy = false,
        )

        assertTrue(policy.visible)
        assertTrue(policy.interactive)
    }
}
