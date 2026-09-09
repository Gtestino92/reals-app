package com.reals.app.ui.root

import com.reals.app.domain.model.ChatPartner
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePassiveNotice
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.ProfileStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeProfileRoutingRulesTest {
    @Test
    fun `active profile can remain in Home without interactions`() {
        assertTrue(home(ProfileStatus.Active).canRemainInHomeForProfileStatus())
    }

    @Test
    fun `draft profile with pending first chat can remain in Home`() {
        assertTrue(
            home(
                ProfileStatus.Draft,
                pendingActions = listOf(
                    HomePendingAction.FirstChat("match-1", "chat-1", partner("Alex")),
                ),
            ).canRemainInHomeForProfileStatus()
        )
    }

    @Test
    fun `draft profile with pending visual review can remain in Home`() {
        assertTrue(
            home(
                ProfileStatus.Draft,
                pendingActions = listOf(
                    HomePendingAction.VisualReview("match-visual", partner("Taylor")),
                ),
            ).canRemainInHomeForProfileStatus()
        )
    }

    @Test
    fun `draft profile with scheduling next step can remain in Home`() {
        assertTrue(
            home(
                ProfileStatus.Draft,
                nextSteps = listOf(
                    HomeNextStep.Scheduling("connection-1", "match-1", partner("Sam")),
                ),
            ).canRemainInHomeForProfileStatus()
        )
    }

    @Test
    fun `draft profile with pending scheduling passive notice can remain in Home`() {
        assertTrue(
            home(
                ProfileStatus.Draft,
                passiveNotices = listOf(HomePassiveNotice.SchedulingPreparing),
            ).canRemainInHomeForProfileStatus()
        )
    }

    @Test
    fun `draft profile with pending scheduling summary can remain in Home`() {
        assertTrue(
            home(
                ProfileStatus.Draft,
                summary = summary(hasPendingSchedulingConnection = true),
            ).canRemainInHomeForProfileStatus()
        )
    }

    @Test
    fun `draft profile without existing interaction keeps profile completion flow`() {
        assertFalse(home(ProfileStatus.Draft).canRemainInHomeForProfileStatus())
    }

    @Test
    fun `inactive profile behavior is not redefined by draft routing`() {
        assertFalse(
            home(
                ProfileStatus.Inactive,
                pendingActions = listOf(
                    HomePendingAction.FirstChat("match-1", "chat-1", partner("Alex")),
                ),
            ).canRemainInHomeForProfileStatus()
        )
    }

    private fun home(
        status: ProfileStatus,
        summary: HomeActiveInteractionsSummary = summary(),
        pendingActions: List<HomePendingAction> = emptyList(),
        nextSteps: List<HomeNextStep> = emptyList(),
        passiveNotices: List<HomePassiveNotice> = emptyList(),
    ): HomeState = HomeState(
        profileStatus = status,
        matchmaking = HomeMatchmaking(
            inQueue = false,
            canSearch = status == ProfileStatus.Active,
            blockedReason = null,
        ),
        activeInteractionsSummary = summary,
        pendingActions = pendingActions,
        nextSteps = nextSteps,
        passiveNotices = passiveNotices,
    )

    private fun summary(
        activeInitialCount: Int = 0,
        activeConnectionCount: Int = 0,
        hasPendingSchedulingConnection: Boolean = false,
        actionableConnectionCount: Int = 0,
    ): HomeActiveInteractionsSummary = HomeActiveInteractionsSummary(
        activeInitialCount = activeInitialCount,
        activeConnectionCount = activeConnectionCount,
        hasPendingSchedulingConnection = hasPendingSchedulingConnection,
        actionableConnectionCount = actionableConnectionCount,
    )

    private fun partner(name: String): ChatPartner = ChatPartner(
        userId = "user-$name",
        profileId = "profile-$name",
        displayName = name,
    )
}
