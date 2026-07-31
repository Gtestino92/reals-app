package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.foreground.ForegroundDestination
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Test

class RealsForegroundDestinationMapperTest {
    @Test
    fun `Ready Home maps to Home`() {
        assertEquals(
            ForegroundDestination.Home,
            RealsRootUiState.Ready(session = TestDomain.session()).foregroundDestination(),
        )
    }

    @Test
    fun `First chat maps to FirstChat`() {
        val state = RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
        )

        assertEquals(ForegroundDestination.FirstChat("match-1", "chat-1"), state.foregroundDestination())
    }

    @Test
    fun `Second chat maps to SecondChat connection`() {
        val state = RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
        )

        assertEquals(ForegroundDestination.SecondChat("connection-1"), state.foregroundDestination())
    }

    @Test
    fun `Visual review scheduling and partner profile map to destinations`() {
        val session = TestDomain.session()

        assertEquals(
            ForegroundDestination.VisualReview("match-visual"),
            RealsRootUiState.VisualApproval(session, "match-visual").foregroundDestination(),
        )
        assertEquals(
            ForegroundDestination.Scheduling("connection-1"),
            RealsRootUiState.Scheduling(session, "connection-1", "match-1").foregroundDestination(),
        )
        assertEquals(
            ForegroundDestination.PartnerProfile("match-1"),
            RealsRootUiState.PartnerProfile(session, "match-1").foregroundDestination(),
        )
    }

    @Test
    fun `Profile management and non-content states map consistently`() {
        val profile = TestDtos.profile(status = "DRAFT").toDomain()
        val draftSession = ProvisionedSession(
            user = TestDtos.user().toDomain(),
            profileSnapshot = ProfileSnapshot.Found(profile),
        )

        assertEquals(
            ForegroundDestination.ProfileManagement,
            RealsRootUiState.Ready(session = draftSession).foregroundDestination(),
        )
        assertEquals(ForegroundDestination.Other, RealsRootUiState.Checking.foregroundDestination())
    }
}
