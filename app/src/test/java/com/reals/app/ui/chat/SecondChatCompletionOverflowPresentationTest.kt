package com.reals.app.ui.chat

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
