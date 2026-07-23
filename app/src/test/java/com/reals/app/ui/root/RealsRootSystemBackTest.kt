package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
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
            RealsRootUiState.FirstChat(session = session, matchId = "match-1"),
        ).forEach { state ->
            assertFalse(state.canHandleSystemBack())
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
}
