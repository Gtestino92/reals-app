package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeQueueState
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.MatchState

data class LocalHiddenInteractions(
    val hiddenFirstChatMatchIds: Set<String>,
    val hiddenVisualMatchIds: Set<String>,
)

class HomeUiMapper {
    fun toScreenModel(
        home: HomeState?,
        localHidden: LocalHiddenInteractions,
        matchmakingBlockedReason: ApiError?,
    ): HomeScreenModel {
        val queue = home?.queue ?: HomeQueueState(inQueue = false)

        return HomeScreenModel(
            queue = queue,
            pendingActions = home.pendingActions(localHidden),
            nextSteps = home.nextSteps(),
            activeInteractionsSummary = home?.activeInteractionsSummary,
            passiveNotices = home.passiveNotices(),
            matchmaking = HomeMatchmakingUiState(
                inQueue = queue.inQueue,
                canSearch = matchmakingBlockedReason == null,
                blockedReason = matchmakingBlockedReason,
            ),
        )
    }

    private fun HomeState?.pendingActions(
        localHidden: LocalHiddenInteractions,
    ): List<HomeActionItem> {
        if (this == null) return emptyList()

        return activeMatches.mapNotNull { match ->
            when {
                match.matchState == MatchState.ChatActive &&
                    match.firstChat != null &&
                    match.matchId !in localHidden.hiddenFirstChatMatchIds -> {
                    HomeActionItem.FirstChat(
                        matchId = match.matchId,
                        chatId = match.firstChat.chatId,
                        partnerDisplayName = match.firstChat.partner?.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: match.partnerDisplayName?.takeIf { it.isNotBlank() },
                    )
                }

                match.matchState == MatchState.VisualPhase &&
                    match.matchId !in localHidden.hiddenVisualMatchIds -> {
                    HomeActionItem.VisualReview(
                        matchId = match.matchId,
                        partnerDisplayName = match.partnerDisplayName?.takeIf { it.isNotBlank() },
                    )
                }

                else -> null
            }
        }
    }

    private fun HomeState?.nextSteps(): List<HomeNextStepItem> {
        if (this == null) return emptyList()

        return activeConnections.mapNotNull { connection ->
            when (connection.connectionState) {
                ConnectionState.SchedulingPending,
                ConnectionState.Closed -> null

                ConnectionState.SchedulingPhase -> HomeNextStepItem.Scheduling(
                    connectionId = connection.connectionId,
                    matchId = connection.matchId,
                    partnerDisplayName = connection.partnerName(),
                )

                ConnectionState.SecondChatScheduled -> HomeNextStepItem.SecondChatScheduled(
                    connectionId = connection.connectionId,
                    matchId = connection.matchId,
                    partnerDisplayName = connection.partnerName(),
                )

                ConnectionState.SecondChatAvailable,
                ConnectionState.SecondChat -> HomeNextStepItem.SecondChatAvailable(
                    connectionId = connection.connectionId,
                    matchId = connection.matchId,
                    partnerDisplayName = connection.partnerName(),
                )

                is ConnectionState.Unknown -> HomeNextStepItem.Unknown(
                    connectionId = connection.connectionId,
                    matchId = connection.matchId,
                    rawState = connection.connectionState.rawValue,
                    partnerDisplayName = connection.partnerName(),
                )
            }
        }
    }

    private fun HomeState?.passiveNotices(): List<HomePassiveNotice> {
        val count = this?.activeInteractionsSummary?.pendingSchedulingConnectionCount ?: 0
        return if (count > 0) {
            listOf(HomePassiveNotice.SchedulingPreparing(count))
        } else {
            emptyList()
        }
    }

    private fun HomeConnection.partnerName(): String? =
        secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
            ?: partner?.displayName?.takeIf { it.isNotBlank() }
}

