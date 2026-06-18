package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePassiveNotice
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState

data class LocalHiddenInteractions(
    val hiddenFirstChatMatchIds: Set<String>,
    val hiddenVisualMatchIds: Set<String>,
)

class HomeUiMapper {
    fun toScreenModel(
        home: HomeState?,
        localHidden: LocalHiddenInteractions,
        localMatchmakingBlockedReason: ApiError?,
    ): HomeScreenModel {
        return HomeScreenModel(
            pendingActions = home.pendingActionItems(localHidden),
            nextSteps = home.nextStepItems(),
            activeInteractionsSummary = home?.activeInteractionsSummary,
            passiveNotices = home.passiveNoticeItems(),
            matchmaking = home.matchmakingUiState(localMatchmakingBlockedReason),
        )
    }

    private fun HomeState?.pendingActionItems(
        localHidden: LocalHiddenInteractions,
    ): List<HomeActionItem> {
        if (this == null) return emptyList()

        return pendingActions.mapNotNull { action ->
            when (action) {
                is HomePendingAction.FirstChat ->
                    if (action.matchId in localHidden.hiddenFirstChatMatchIds) {
                        null
                    } else {
                        HomeActionItem.FirstChat(
                            matchId = action.matchId,
                            chatId = action.chatId,
                            partnerDisplayName = action.partner?.displayName?.takeIf { it.isNotBlank() },
                        )
                    }

                is HomePendingAction.VisualReview ->
                    if (action.matchId in localHidden.hiddenVisualMatchIds) {
                        null
                    } else {
                        HomeActionItem.VisualReview(
                            matchId = action.matchId,
                            partnerDisplayName = action.partner?.displayName?.takeIf { it.isNotBlank() },
                        )
                    }

                is HomePendingAction.Unknown -> null
            }
        }
    }

    private fun HomeState?.nextStepItems(): List<HomeNextStepItem> {
        if (this == null) return emptyList()

        return nextSteps.mapNotNull { nextStep ->
            when (nextStep) {
                is HomeNextStep.Scheduling -> HomeNextStepItem.Scheduling(
                    connectionId = nextStep.connectionId,
                    matchId = nextStep.matchId,
                    partnerDisplayName = nextStep.partner?.displayName?.takeIf { it.isNotBlank() },
                )

                is HomeNextStep.SecondChatScheduled ->
                    if (nextStep.secondChat?.chatStatus.isClosedSecondChatStatus()) {
                        null
                    } else {
                        HomeNextStepItem.SecondChatScheduled(
                            connectionId = nextStep.connectionId,
                            matchId = nextStep.matchId,
                            partnerDisplayName = nextStep.partnerDisplayName(),
                        )
                    }

                is HomeNextStep.SecondChatAvailable ->
                    if (nextStep.secondChat?.chatStatus.isClosedSecondChatStatus()) {
                        null
                    } else {
                        HomeNextStepItem.SecondChatAvailable(
                            connectionId = nextStep.connectionId,
                            matchId = nextStep.matchId,
                            partnerDisplayName = nextStep.partnerDisplayName(),
                        )
                    }

                is HomeNextStep.Unknown -> HomeNextStepItem.Unknown(
                    connectionId = nextStep.connectionId.orEmpty(),
                    matchId = nextStep.matchId,
                    rawState = nextStep.rawType,
                    partnerDisplayName = nextStep.partner?.displayName?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    private fun HomeState?.passiveNoticeItems(): List<HomePassiveNoticeItem> {
        if (this == null) return emptyList()

        return passiveNotices.map { notice ->
            when (notice) {
                is HomePassiveNotice.SchedulingPreparing ->
                    HomePassiveNoticeItem.SchedulingPreparing(notice.count)

                is HomePassiveNotice.Unknown ->
                    HomePassiveNoticeItem.Unknown(
                        rawType = notice.rawType,
                        count = notice.count,
                    )
            }
        }
    }

    private fun HomeState?.matchmakingUiState(
        localMatchmakingBlockedReason: ApiError?,
    ): HomeMatchmakingUiState {
        val matchmaking = this?.matchmaking ?: HomeMatchmaking(
            inQueue = false,
            canSearch = localMatchmakingBlockedReason == null,
            blockedReason = null,
        )

        return HomeMatchmakingUiState(
            inQueue = matchmaking.inQueue,
            canSearch = matchmaking.canSearch && localMatchmakingBlockedReason == null,
            blockedReason = localMatchmakingBlockedReason
                ?: matchmaking.blockedReason?.let {
                    ApiError.Backend(
                        statusCode = 409,
                        code = it.code,
                        error = it.code,
                        message = it.message,
                    )
                },
        )
    }

    private fun HomeNextStep.SecondChatScheduled.partnerDisplayName(): String? =
        secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
            ?: partner?.displayName?.takeIf { it.isNotBlank() }

    private fun HomeNextStep.SecondChatAvailable.partnerDisplayName(): String? =
        secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
            ?: partner?.displayName?.takeIf { it.isNotBlank() }

    private fun ChatStatus?.isClosedSecondChatStatus(): Boolean =
        this == ChatStatus.Cancelled ||
            this == ChatStatus.Expired ||
            this == ChatStatus.Abandoned ||
            this == ChatStatus.Closed ||
            this == ChatStatus.Finished
}
