package com.reals.app.ui.matchmaking

import com.reals.app.domain.model.ProfileStatus
import com.reals.app.ui.root.MatchmakingSearchUiPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLocationPermissionRulesTest {
    @Test
    fun `auto permission request is allowed only for eligible searchable Home`() {
        assertTrue(
            shouldAutoRequestHomeLocationPermission(
                profileStatus = ProfileStatus.Active,
                screenModel = screenModel(canSearch = true, inQueue = false),
                homeLoading = false,
                matchmakingSearchPhase = MatchmakingSearchUiPhase.Idle,
                hasLocationPermission = false,
                autoLocationPermissionRequested = false,
            )
        )
    }

    @Test
    fun `auto permission request is blocked when profile is not active`() {
        assertFalse(eligible(profileStatus = ProfileStatus.Draft))
    }

    @Test
    fun `auto permission request is blocked when user cannot search`() {
        assertFalse(eligible(screenModel = screenModel(canSearch = false, inQueue = false)))
    }

    @Test
    fun `auto permission request is blocked when user is already in queue`() {
        assertFalse(eligible(screenModel = screenModel(canSearch = true, inQueue = true)))
    }

    @Test
    fun `auto permission request is blocked while Home is loading`() {
        assertFalse(eligible(homeLoading = true))
    }

    @Test
    fun `auto permission request is blocked after it was already requested`() {
        assertFalse(eligible(autoLocationPermissionRequested = true))
    }

    @Test
    fun `auto permission request is blocked when permission already exists`() {
        assertFalse(eligible(hasLocationPermission = true))
    }

    private fun eligible(
        profileStatus: ProfileStatus = ProfileStatus.Active,
        screenModel: HomeScreenModel? = screenModel(canSearch = true, inQueue = false),
        homeLoading: Boolean = false,
        matchmakingSearchPhase: MatchmakingSearchUiPhase = MatchmakingSearchUiPhase.Idle,
        hasLocationPermission: Boolean = false,
        autoLocationPermissionRequested: Boolean = false,
    ): Boolean = shouldAutoRequestHomeLocationPermission(
        profileStatus = profileStatus,
        screenModel = screenModel,
        homeLoading = homeLoading,
        matchmakingSearchPhase = matchmakingSearchPhase,
        hasLocationPermission = hasLocationPermission,
        autoLocationPermissionRequested = autoLocationPermissionRequested,
    )

    private fun screenModel(
        canSearch: Boolean,
        inQueue: Boolean,
    ): HomeScreenModel = HomeScreenModel(
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        activeInteractionsSummary = null,
        passiveNotices = emptyList(),
        matchmaking = HomeMatchmakingUiState(
            inQueue = inQueue,
            canSearch = canSearch,
            blockedReason = null,
        ),
    )
}
