package com.reals.app.ui.chat

import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.testutil.TestDtos
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
}
