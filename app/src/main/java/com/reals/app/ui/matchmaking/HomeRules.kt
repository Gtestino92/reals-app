package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeMatch
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.MatchState

internal fun ApiError?.isActiveMatchLimitReached(): Boolean =
    this is ApiError.Backend && code == "ACTIVE_MATCH_LIMIT_REACHED"

internal fun HomeState?.hasBlockingEngagements(): Boolean {
    if (this == null) return false
    return activeMatches.any { it.matchState == MatchState.ChatActive }
}

internal fun HomeState?.shouldPollHome(hasLocallyHiddenInteractions: Boolean): Boolean {
    if (hasLocallyHiddenInteractions) return true
    if (this == null) return false

    return activeMatches.any {
        it.matchState == MatchState.ChatActive || it.matchState == MatchState.VisualPhase
    } || activeConnections.any {
        it.connectionState != ConnectionState.Closed
    }
}

internal fun HomeState?.pendingFirstChatMatches(): List<HomeMatch> =
    this?.activeMatches
        ?.filter { it.matchState == MatchState.ChatActive && it.firstChat != null }
        .orEmpty()

internal fun HomeState?.pendingVisualApprovals(): List<HomeMatch> =
    this?.activeMatches
        ?.filter { it.matchState == MatchState.VisualPhase }
        .orEmpty()

internal fun HomeState?.nextStepConnections(): List<HomeConnection> =
    this?.activeConnections
        ?.filter { it.connectionState == ConnectionState.SchedulingPhase }
        .orEmpty()

internal fun HomeState?.engagementCounts(): EngagementCounts {
    if (this == null) return EngagementCounts()
    return EngagementCounts(
        firstChats = activeMatches.count { it.matchState == MatchState.ChatActive },
        visualReviews = activeMatches.count { it.matchState == MatchState.VisualPhase },
        connections = activeConnections.count { it.connectionState != ConnectionState.Closed },
    )
}

internal data class EngagementCounts(
    val firstChats: Int = 0,
    val visualReviews: Int = 0,
    val connections: Int = 0,
) {
    val total: Int = firstChats + visualReviews + connections
}

internal fun HomeConnection.partnerDisplayName(): String? =
    secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
        ?: partner?.displayName?.takeIf { it.isNotBlank() }
