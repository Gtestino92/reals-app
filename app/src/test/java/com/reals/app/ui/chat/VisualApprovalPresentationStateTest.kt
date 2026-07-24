package com.reals.app.ui.chat

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualApprovalPresentationStateTest {
    @Test
    fun `initial loading with no match or profile shows only initial loading`() {
        val state = visualApprovalPresentationState(
            match = null,
            profile = null,
            loading = true,
            refreshing = false,
            error = null,
            partnerMessageError = null,
        )

        assertTrue(state.showInitialLoading)
        assertFalse(state.showProfileRetry)
        assertFalse(state.showLoadedContent)
    }

    @Test
    fun `successful loaded state shows loaded content without initial loading`() {
        val state = visualApprovalPresentationState(
            match = TestDtos.match("VISUAL_PHASE").toDomain(),
            profile = TestDtos.visualProfile().toDomain(),
            loading = false,
            refreshing = false,
            error = null,
            partnerMessageError = null,
        )

        assertTrue(state.showLoadedContent)
        assertFalse(state.showInitialLoading)
        assertFalse(state.showInitialFailure)
    }

    @Test
    fun `initial failure shows retry after actual failure`() {
        val state = visualApprovalPresentationState(
            match = null,
            profile = null,
            loading = false,
            refreshing = false,
            error = ApiError.Unexpected("failed"),
            partnerMessageError = null,
        )

        assertTrue(state.showInitialFailure)
        assertTrue(state.showProfileRetry)
        assertFalse(state.showInitialLoading)
    }

    @Test
    fun `refreshing with existing content keeps loaded content`() {
        val state = visualApprovalPresentationState(
            match = TestDtos.match("VISUAL_PHASE").toDomain(),
            profile = TestDtos.visualProfile().toDomain(),
            loading = false,
            refreshing = true,
            error = null,
            partnerMessageError = null,
        )

        assertTrue(state.showLoadedContent)
        assertTrue(state.showRefreshingIndicator)
        assertFalse(state.showInitialLoading)
    }

    @Test
    fun `partner message failure after profile loaded is message specific`() {
        val state = visualApprovalPresentationState(
            match = TestDtos.match("VISUAL_PHASE").toDomain(),
            profile = TestDtos.visualProfile(partnerPersonalMessageSubmitted = true).toDomain(),
            loading = false,
            refreshing = false,
            error = null,
            partnerMessageError = ApiError.Unexpected("message failed"),
        )

        assertTrue(state.showLoadedContent)
        assertTrue(state.showPartnerMessageRetry)
        assertFalse(state.showInitialFailure)
        assertFalse(state.showProfileRetry)
    }

    @Test
    fun `visual decision is enabled for unread partner message`() {
        val canDecide = visualApprovalCanMakeDecision(
            profile = TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = true,
                partnerPersonalMessageRead = false,
                decisionRequiresPartnerPersonalMessageRead = true,
            ).toDomain(),
            busy = false,
            lifecycle = VisualApprovalLifecycleUiState(showWarning = false, expired = false),
        )

        assertTrue(canDecide)
    }

    @Test
    fun `visual decision is disabled while conflicting request is running`() {
        val canDecide = visualApprovalCanMakeDecision(
            profile = TestDtos.visualProfile().toDomain(),
            busy = true,
            lifecycle = VisualApprovalLifecycleUiState(showWarning = false, expired = false),
        )

        assertFalse(canDecide)
    }

    @Test
    fun `visual decision is disabled after expiry`() {
        val canDecide = visualApprovalCanMakeDecision(
            profile = TestDtos.visualProfile().toDomain(),
            busy = false,
            lifecycle = VisualApprovalLifecycleUiState(showWarning = false, expired = true),
        )

        assertFalse(canDecide)
    }

    @Test
    fun `visual decision is disabled when profile is missing`() {
        val canDecide = visualApprovalCanMakeDecision(
            profile = null,
            busy = false,
            lifecycle = VisualApprovalLifecycleUiState(showWarning = false, expired = false),
        )

        assertFalse(canDecide)
    }

    @Test
    fun `unread partner message presentation shows new message emphasis and read action`() {
        val state = partnerPersonalMessagePresentationState(
            profile = TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = true,
                partnerPersonalMessageRead = false,
                decisionRequiresPartnerPersonalMessageRead = true,
            ).toDomain(),
            partnerMessage = null,
            partnerMessageLoaded = false,
            readingPartnerMessage = false,
            partnerMessageError = null,
            refreshing = false,
        )

        assertTrue(state.hasUnreadPartnerMessage)
        assertTrue(state.emphasized)
        assertEquals("Mensaje nuevo", state.badgeLabel)
        assertEquals("La otra persona dejó un mensaje personal para vos.", state.body)
        assertTrue(state.showReadAction)
        assertEquals("Leer mensaje", state.readActionLabel)
    }

    @Test
    fun `read partner message presentation removes new message emphasis`() {
        val state = partnerPersonalMessagePresentationState(
            profile = TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = true,
                partnerPersonalMessageRead = true,
            ).toDomain(),
            partnerMessage = "hola mundo",
            partnerMessageLoaded = true,
            readingPartnerMessage = false,
            partnerMessageError = null,
            refreshing = false,
        )

        assertFalse(state.hasUnreadPartnerMessage)
        assertFalse(state.emphasized)
        assertEquals(null, state.badgeLabel)
        assertEquals("hola mundo", state.body)
        assertFalse(state.showReadAction)
    }

    @Test
    fun `read error keeps retry available and visual decision allowed`() {
        val profile = TestDtos.visualProfile(
            partnerPersonalMessageSubmitted = true,
            partnerPersonalMessageRead = false,
            decisionRequiresPartnerPersonalMessageRead = true,
        ).toDomain()
        val messageState = partnerPersonalMessagePresentationState(
            profile = profile,
            partnerMessage = null,
            partnerMessageLoaded = false,
            readingPartnerMessage = false,
            partnerMessageError = ApiError.Unexpected("failed"),
            refreshing = false,
        )
        val canDecide = visualApprovalCanMakeDecision(
            profile = profile,
            busy = false,
            lifecycle = VisualApprovalLifecycleUiState(showWarning = false, expired = false),
        )

        assertTrue(messageState.hasUnreadPartnerMessage)
        assertTrue(messageState.emphasized)
        assertEquals("Mensaje nuevo", messageState.badgeLabel)
        assertEquals("Reintentar lectura", messageState.readActionLabel)
        assertTrue(messageState.showReadAction)
        assertTrue(canDecide)
    }

    @Test
    fun `no partner message presentation remains neutral`() {
        val state = partnerPersonalMessagePresentationState(
            profile = TestDtos.visualProfile(
                partnerPersonalMessageSubmitted = false,
                partnerPersonalMessageRead = true,
            ).toDomain(),
            partnerMessage = null,
            partnerMessageLoaded = false,
            readingPartnerMessage = false,
            partnerMessageError = null,
            refreshing = false,
        )

        assertFalse(state.hasUnreadPartnerMessage)
        assertFalse(state.emphasized)
        assertEquals(null, state.badgeLabel)
        assertEquals("La otra persona todavía no dejó un mensaje personal.", state.body)
        assertFalse(state.showReadAction)
    }
}
