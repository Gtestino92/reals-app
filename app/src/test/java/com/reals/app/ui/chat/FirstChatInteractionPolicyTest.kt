package com.reals.app.ui.chat

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.testutil.TestDtos
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
        assertTrue(policy.pollingEnabled)
        assertTrue(policy.safetyAvailable)
        assertTrue(policy.manualBlockAvailable)
    }

    @Test
    fun `neutral pending decision copy does not disclose partner approval`() {
        val copy = chatDecisionSummary(
            myDecision = ChatDecisionState.Pending,
            partnerDecision = ChatDecisionState.Approved,
            partnerName = "Alex",
        )

        assertEquals("Ya no se pueden enviar mensajes. Elegí cómo querés continuar.", copy)
        assertFalse(copy.orEmpty().contains("aprobó", ignoreCase = true))
        assertFalse(copy.orEmpty().contains("Alex"))
    }
}
