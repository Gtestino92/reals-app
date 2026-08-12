package com.reals.app.ui.root

import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.profile.ProfileManagementSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealsAppRoutingTest {
    @Test
    fun `active profile renders Home surface unchanged`() {
        assertTrue(ready(status = "ACTIVE").shouldRenderHomeSurface())
    }

    @Test
    fun `draft operational Home loading renders Home surface before HomeState arrives`() {
        assertTrue(
            ready(
                status = "DRAFT",
                home = HomeUiState(
                    homeLoading = true,
                    allowDraftHomeWithoutInteractions = true,
                ),
            ).shouldRenderHomeSurface()
        )
    }

    @Test
    fun `draft ordinary Home loading keeps profile completion surface`() {
        assertFalse(
            ready(
                status = "DRAFT",
                home = HomeUiState(homeLoading = true),
            ).shouldRenderHomeSurface()
        )
    }

    @Test
    fun `draft empty Home renders Home surface only for operational return`() {
        assertFalse(
            ready(
                status = "DRAFT",
                home = HomeUiState(homeState = emptyHome(ProfileStatus.Draft)),
            ).shouldRenderHomeSurface()
        )
        assertTrue(
            ready(
                status = "DRAFT",
                home = HomeUiState(
                    homeState = emptyHome(ProfileStatus.Draft),
                    allowDraftHomeWithoutInteractions = true,
                ),
            ).shouldRenderHomeSurface()
        )
    }

    @Test
    fun `draft Home with existing interaction renders Home surface`() {
        assertTrue(
            ready(
                status = "DRAFT",
                home = HomeUiState(
                    homeState = emptyHome(ProfileStatus.Draft).copy(
                        pendingActions = listOf(
                            HomePendingAction.VisualReview(
                                matchId = "match-visual",
                                partner = TestDtos.partner("Visual").toDomain(),
                            ),
                        ),
                    ),
                ),
            ).shouldRenderHomeSurface()
        )
    }

    @Test
    fun `draft stale active Home does not render Home surface after loading finishes`() {
        assertFalse(
            ready(
                status = "DRAFT",
                home = HomeUiState(
                    homeState = emptyHome(ProfileStatus.Active),
                    allowDraftHomeWithoutInteractions = true,
                ),
            ).shouldRenderHomeSurface()
        )
    }

    @Test
    fun `inactive profile routing remains unchanged`() {
        assertFalse(ready(status = "INACTIVE").shouldRenderHomeSurface())
    }

    @Test
    fun `profile management destination opens profile surface`() {
        val state = ready(status = "ACTIVE").copy(
            editingActiveProfile = true,
            profileManagementDestination = ProfileManagementDestination.Profile,
        )

        assertEquals(ProfileManagementSurface.Profile, state.profileManagementSurface(homeAvailable = true))
    }

    @Test
    fun `search management destination opens search surface`() {
        val state = ready(status = "ACTIVE").copy(
            editingActiveProfile = true,
            profileManagementDestination = ProfileManagementDestination.Search,
        )

        assertEquals(ProfileManagementSurface.Search, state.profileManagementSurface(homeAvailable = true))
    }

    @Test
    fun `profile setup surface remains authoritative when Home is unavailable`() {
        val state = ready(status = "DRAFT").copy(
            editingActiveProfile = true,
            profileManagementDestination = ProfileManagementDestination.Search,
        )

        assertEquals(ProfileManagementSurface.Setup, state.profileManagementSurface(homeAvailable = false))
    }

    private fun ready(
        status: String,
        home: HomeUiState = HomeUiState(),
    ) = RealsRootUiState.Ready(
        session = TestDomain.session().copy(
            profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = status).toDomain()),
        ),
        home = home,
    )

    private fun emptyHome(status: ProfileStatus) = TestDtos.home().copy(
        profileStatus = status.rawValue,
        matchmaking = HomeMatchmakingResponseDto(
            inQueue = false,
            canSearch = status == ProfileStatus.Active,
            blockedReason = null,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummaryResponseDto(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            hasPendingSchedulingConnection = false,
            actionableConnectionCount = 0,
        ),
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    ).toDomain()
}
