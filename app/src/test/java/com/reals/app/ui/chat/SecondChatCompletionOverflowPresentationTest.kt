package com.reals.app.ui.chat

import com.reals.app.domain.model.ChatType
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.ui.root.SecondChatActiveResolutionPresentation
import com.reals.app.ui.root.SecondChatCreateResolutionPresentation
import com.reals.app.ui.root.SecondChatResolutionPresentation
import com.reals.app.ui.root.SecondChatResolutionRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondChatCompletionOverflowPresentationTest {
    @Test
    fun `first chat remains eligible for safety actions without second-chat lifecycle`() {
        assertTrue(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.FirstChat,
                attendanceStatus = null,
            )
        )
    }

    @Test
    fun `second chat on time attendance is eligible for safety actions`() {
        assertTrue(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.SecondChat,
                attendanceStatus = SecondChatAttendanceStatus.OnTime,
            )
        )
    }

    @Test
    fun `second chat late attendance is eligible for safety actions`() {
        assertTrue(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.SecondChat,
                attendanceStatus = SecondChatAttendanceStatus.Late,
            )
        )
    }

    @Test
    fun `second chat pending attendance is ineligible for safety actions`() {
        assertFalse(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.SecondChat,
                attendanceStatus = SecondChatAttendanceStatus.Pending,
            )
        )
    }

    @Test
    fun `second chat no show attendance is ineligible for safety actions`() {
        assertFalse(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.SecondChat,
                attendanceStatus = SecondChatAttendanceStatus.NoShow,
            )
        )
    }

    @Test
    fun `second chat missing or unknown attendance fails closed for safety actions`() {
        assertFalse(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.SecondChat,
                attendanceStatus = null,
            )
        )
        assertFalse(
            secondChatSafetyActionsAllowed(
                chatType = ChatType.SecondChat,
                attendanceStatus = SecondChatAttendanceStatus.Unknown("NEW_STATUS"),
            )
        )
    }

    @Test
    fun `back home action hides for genuinely active second chat before partner cutoff`() {
        assertFalse(
            shouldShowBackHomeAction(
                hasBackHomeCallback = true,
                hasSecondChatLifecycle = true,
                genuinelyActive = true,
                canReturnAfterPartnerCutoff = false,
            )
        )
    }

    @Test
    fun `back home action shows for genuinely active second chat after partner cutoff`() {
        assertTrue(
            shouldShowBackHomeAction(
                hasBackHomeCallback = true,
                hasSecondChatLifecycle = true,
                genuinelyActive = true,
                canReturnAfterPartnerCutoff = true,
            )
        )
    }

    @Test
    fun `back home action hides for genuinely active second chat when both joined`() {
        assertFalse(
            shouldShowBackHomeAction(
                hasBackHomeCallback = true,
                hasSecondChatLifecycle = true,
                genuinelyActive = true,
                canReturnAfterPartnerCutoff = false,
            )
        )
    }

    @Test
    fun `back home action shows for non-active second chat`() {
        assertTrue(
            shouldShowBackHomeAction(
                hasBackHomeCallback = true,
                hasSecondChatLifecycle = true,
                genuinelyActive = false,
                canReturnAfterPartnerCutoff = false,
            )
        )
    }

    @Test
    fun `back home action hides without callback`() {
        assertFalse(
            shouldShowBackHomeAction(
                hasBackHomeCallback = false,
                hasSecondChatLifecycle = true,
                genuinelyActive = false,
                canReturnAfterPartnerCutoff = true,
            )
        )
    }

    @Test
    fun `back home action preserves first-chat callback-driven rendering`() {
        assertTrue(
            shouldShowBackHomeAction(
                hasBackHomeCallback = true,
                hasSecondChatLifecycle = false,
                genuinelyActive = false,
                canReturnAfterPartnerCutoff = false,
            )
        )
        assertFalse(
            shouldShowBackHomeAction(
                hasBackHomeCallback = false,
                hasSecondChatLifecycle = false,
                genuinelyActive = false,
                canReturnAfterPartnerCutoff = true,
            )
        )
    }

    @Test
    fun `partner no show claim shows before partner entry cutoff when backend allows`() {
        assertTrue(
            shouldShowPartnerNoShowClaim(
                backendCanClaim = true,
                partnerEntryCutoffReached = false,
            )
        )
    }

    @Test
    fun `partner no show claim hides at local partner entry cutoff`() {
        assertFalse(
            shouldShowPartnerNoShowClaim(
                backendCanClaim = true,
                partnerEntryCutoffReached = true,
            )
        )
    }

    @Test
    fun `partner no show claim hides when backend disallows`() {
        assertFalse(
            shouldShowPartnerNoShowClaim(
                backendCanClaim = false,
                partnerEntryCutoffReached = false,
            )
        )
    }

    @Test
    fun `eligible second-chat completion creates overflow action`() {
        val action = secondChatCompletionOverflowPresentation(
            SecondChatResolutionPresentation(createCompletion = completionCreate(enabled = true))
        )

        assertTrue(action.visible)
        assertTrue(action.enabled)
        assertEquals("Finalizar de común acuerdo", action.label)
    }

    @Test
    fun `missing completion presentation creates no overflow action`() {
        val action = secondChatCompletionOverflowPresentation(
            SecondChatResolutionPresentation(createCompletion = null)
        )

        assertFalse(action.visible)
        assertFalse(action.enabled)
    }

    @Test
    fun `disabled completion presentation keeps menu item disabled`() {
        val action = secondChatCompletionOverflowPresentation(
            SecondChatResolutionPresentation(createCompletion = completionCreate(enabled = false))
        )

        assertTrue(action.visible)
        assertFalse(secondChatCompletionOverflowMenuItemEnabled(action, actionLoading = false))
    }

    @Test
    fun `completion-only overflow can open without first-chat actions`() {
        val canOpen = chatOverflowCanOpen(
            loadingChatAction = false,
            canUseExistingChatActions = false,
            canDecide = false,
            canUseSafetyActions = false,
            canManualBlock = false,
            visibility = firstChatOverflowActionVisibility(
                showMutualExitActions = false,
                showDecisionActions = false,
                decisionOnlyForCurrentUser = false,
                canRequestOrdinaryExit = false,
                canDecide = false,
                canUseSafetyActions = false,
                canManualBlock = false,
            ),
            secondChatCompletion = SecondChatCompletionOverflowPresentation(
                visible = true,
                enabled = true,
                label = "Finalizar de común acuerdo",
            ),
        )

        assertTrue(canOpen)
    }

    @Test
    fun `first-chat overflow behavior remains unchanged without second-chat completion`() {
        val visibility = firstChatOverflowActionVisibility(
            showMutualExitActions = true,
            showDecisionActions = true,
            decisionOnlyForCurrentUser = false,
            canRequestOrdinaryExit = true,
            canDecide = true,
            canUseSafetyActions = true,
            canManualBlock = true,
        )

        val canOpen = chatOverflowCanOpen(
            loadingChatAction = false,
            canUseExistingChatActions = true,
            canDecide = true,
            canUseSafetyActions = true,
            canManualBlock = true,
            visibility = visibility,
            secondChatCompletion = SecondChatCompletionOverflowPresentation(
                visible = false,
                enabled = false,
                label = "",
            ),
        )

        assertTrue(canOpen)
    }

    @Test
    fun `enabled completion overflow click closes menu and opens confirmation only`() {
        var closeMenuCalls = 0
        var confirmationCalls = 0
        var backendCalls = 0

        val handled = handleSecondChatCompletionOverflowClick(
            action = SecondChatCompletionOverflowPresentation(
                visible = true,
                enabled = true,
                label = "Finalizar de común acuerdo",
            ),
            actionLoading = false,
            onCloseMenu = { closeMenuCalls++ },
            onShowConfirmation = { confirmationCalls++ },
        )

        assertTrue(handled)
        assertEquals(1, closeMenuCalls)
        assertEquals(1, confirmationCalls)
        assertEquals(0, backendCalls)
    }

    @Test
    fun `disabled completion overflow click does not invoke anything`() {
        var closeMenuCalls = 0
        var confirmationCalls = 0

        val handled = handleSecondChatCompletionOverflowClick(
            action = SecondChatCompletionOverflowPresentation(
                visible = true,
                enabled = false,
                label = "Finalizar de común acuerdo",
            ),
            actionLoading = false,
            onCloseMenu = { closeMenuCalls++ },
            onShowConfirmation = { confirmationCalls++ },
        )

        assertFalse(handled)
        assertEquals(0, closeMenuCalls)
        assertEquals(0, confirmationCalls)
    }

    @Test
    fun `active mutual completion request remains body content and has no overflow create item`() {
        val presentation = SecondChatResolutionPresentation(
            createCompletion = null,
            activeRequest = activeMutualCompletionRequest(),
        )

        assertTrue(secondChatResolutionBodyVisible(presentation))
        assertFalse(secondChatCompletionOverflowPresentation(presentation).visible)
    }

    @Test
    fun `partner inactivity remains body content`() {
        val presentation = SecondChatResolutionPresentation(
            createInactivityClaim = completionCreate(label = "La otra persona no respondió"),
        )

        assertTrue(secondChatResolutionBodyVisible(presentation))
        assertFalse(secondChatCompletionOverflowPresentation(presentation).visible)
    }

    @Test
    fun `completion creation alone does not make resolution body visible`() {
        val presentation = SecondChatResolutionPresentation(createCompletion = completionCreate())

        assertFalse(secondChatResolutionBodyVisible(presentation))
        assertTrue(secondChatCompletionOverflowPresentation(presentation).visible)
    }

    private fun completionCreate(
        enabled: Boolean = true,
        label: String = "Finalizar de común acuerdo",
    ): SecondChatCreateResolutionPresentation =
        SecondChatCreateResolutionPresentation(
            enabled = enabled,
            label = label,
            confirmationTitle = label,
            confirmationBody = "Confirmación",
        )

    private fun activeMutualCompletionRequest(): SecondChatActiveResolutionPresentation =
        SecondChatActiveResolutionPresentation(
            requestId = "request-1",
            type = SecondChatResolutionRequestType.MutualCompletion,
            role = SecondChatResolutionRole.Requester,
            title = "Cierre propuesto",
            message = "Esperando confirmación.",
            remainingMillis = 60_000L,
            locallyExpired = false,
            showAcceptRejectControls = false,
            controlsEnabled = false,
            refreshKey = null,
        )
}
