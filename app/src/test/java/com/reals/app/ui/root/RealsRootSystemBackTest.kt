package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealsRootSystemBackTest {
    @Test
    fun `system back is handled for supported non root screens`() {
        val session = TestDomain.session()

        listOf(
            RealsRootUiState.SecondChat(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
            ),
            RealsRootUiState.VisualApproval(session = session, matchId = "match-1"),
            RealsRootUiState.Scheduling(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
            ),
            RealsRootUiState.PartnerProfile(session = session, matchId = "match-1"),
            RealsRootUiState.PendingEngagement(
                session = session,
                title = "Pendiente",
                body = "Hay una acción pendiente.",
            ),
            RealsRootUiState.ActivationComplete(
                session = session,
                result = ProfileActivationResult(
                    profile = TestDtos.profile().toDomain(),
                    addedPhotoCount = 0,
                    totalPhotoCount = 0,
                    generatedUrls = emptyList(),
                ),
            ),
        ).forEach { state ->
            assertTrue(state.canHandleSystemBack())
        }
    }

    @Test
    fun `system back is not handled for root onboarding first chat or loading states`() {
        val session = TestDomain.session()

        listOf(
            RealsRootUiState.Login(),
            RealsRootUiState.Checking,
            RealsRootUiState.LoadingSession(email = "alex@example.com"),
            RealsRootUiState.MissingFirebase("missing"),
            RealsRootUiState.Ready(session = session),
            firstChat(chat = TestDtos.chat(status = "ACTIVE").toDomain()),
            firstChat(loading = true),
        ).forEach { state ->
            assertFalse(state.canHandleSystemBack())
        }
    }

    @Test
    fun `completed partial first chat can recover to Home`() {
        val state = firstChat()

        assertTrue(state.canRecoverFirstChatToHome())
        assertTrue(state.canHandleSystemBack())
    }

    @Test
    fun `first chat recovery is blocked while work is active`() {
        listOf(
            firstChat(loading = true),
            firstChat(refreshing = true),
            firstChat(sending = true),
            firstChat(actionLoading = true),
            firstChat(guidanceActionLoading = true),
            firstChat(manualBlock = ManualBlockUiState(loading = true)),
        ).forEach { state ->
            assertFalse(state.canRecoverFirstChatToHome())
            assertFalse(state.canHandleSystemBack())
        }
    }

    @Test
    fun `first chat visible and system back recovery share one rule`() {
        listOf(
            firstChat(),
            firstChat(loading = true),
            firstChat(chat = TestDtos.chat(status = "ACTIVE").toDomain()),
            firstChat(actionLoading = true),
        ).forEach { state ->
            assertEquals(state.canRecoverFirstChatToHome(), state.canHandleSystemBack())
        }
    }

    @Test
    fun `system back is handled for profile management even after draft photo mutation`() {
        listOf(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
            ),
            RealsRootUiState.Ready(
                session = draftSession(),
                editingActiveProfile = true,
            ),
        ).forEach { state ->
            assertTrue(state.canHandleSystemBack())
        }
    }

    @Test
    fun `system back is not handled while guarded operations are active`() {
        val session = TestDomain.session()

        assertFalse(
            RealsRootUiState.SecondChat(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
                sending = true,
            ).canHandleSystemBack()
        )
        assertFalse(
            RealsRootUiState.VisualApproval(
                session = session,
                matchId = "match-1",
                deciding = true,
            ).canHandleSystemBack()
        )
        assertFalse(
            RealsRootUiState.Scheduling(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
                submitting = true,
            ).canHandleSystemBack()
        )
    }

    private fun draftSession() = TestDomain.session().copy(
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = "DRAFT").toDomain()),
    )

    private fun firstChat(
        chat: com.reals.app.domain.model.Chat? = null,
        loading: Boolean = false,
        refreshing: Boolean = false,
        sending: Boolean = false,
        actionLoading: Boolean = false,
        guidanceActionLoading: Boolean = false,
        manualBlock: ManualBlockUiState = ManualBlockUiState(),
    ) = RealsRootUiState.FirstChat(
        session = TestDomain.session(),
        matchId = "match-1",
        chatId = chat?.id,
        chat = chat,
        loading = loading,
        refreshing = refreshing,
        sending = sending,
        actionLoading = actionLoading,
        guidanceActionLoading = guidanceActionLoading,
        manualBlock = manualBlock,
    )
}
