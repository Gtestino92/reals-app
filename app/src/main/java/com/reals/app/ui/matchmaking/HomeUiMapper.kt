package com.reals.app.ui.matchmaking

import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.HomeMatchmaking
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePassiveNotice
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.ProfileStatus

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
        val pendingActionItems = home.pendingActionItems(localHidden)
        val nextStepItems = home.nextStepItems()
        val activeNextStepCount = nextStepItems.count { it !is HomeNextStepItem.SecondChatExpired }
        val displayedSummary = home?.activeInteractionsSummary?.copy(
            activeInitialCount = pendingActionItems.size,
            activeConnectionCount = activeNextStepCount,
            actionableConnectionCount = activeNextStepCount,
        )

        return HomeScreenModel(
            pendingActions = pendingActionItems,
            nextSteps = nextStepItems,
            activeInteractionsSummary = displayedSummary,
            passiveNotices = home.passiveNoticeItems(),
            matchmaking = home.matchmakingUiState(localMatchmakingBlockedReason),
            draftProfileWarning = home.draftProfileWarning(),
        )
    }

    private fun HomeState?.draftProfileWarning(): DraftProfileHomeWarning? {
        if (this?.profileStatus != ProfileStatus.Draft) return null
        return DraftProfileHomeWarning(
            title = "Tu perfil está en borrador",
            message = "Podés continuar tus interacciones actuales. " +
                "Completá y reactivá tu perfil para buscar nuevas personas.",
            actionLabel = "Completar perfil",
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
                            visualStartedAt = action.visualStartedAt,
                            visualExpiresAt = action.visualExpiresAt,
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
                    createdAt = nextStep.createdAt,
                    schedulingExpiresAt = nextStep.schedulingExpiresAt,
                )

                is HomeNextStep.SecondChatScheduled ->
                    if (nextStep.secondChat?.chatStatus.isDismissedSecondChatStatus()) {
                        null
                    } else {
                        HomeNextStepItem.SecondChatScheduled(
                            connectionId = nextStep.connectionId,
                            matchId = nextStep.matchId,
                            partnerDisplayName = nextStep.partnerDisplayName(),
                            chatId = nextStep.secondChat?.chatId,
                            chatStatus = nextStep.secondChat?.chatStatus?.rawValue,
                            availableAt = nextStep.secondChat?.availableAt,
                            entryClosesAt = nextStep.secondChat?.entryClosesAt,
                            expiresAt = nextStep.secondChat?.expiresAt,
                            durationMinutes = nextStep.secondChat?.durationMinutes,
                            myAttendanceStatus = nextStep.secondChat?.myAttendanceStatus?.rawValue,
                        )
                    }

                is HomeNextStep.SecondChatAvailable ->
                    if (nextStep.secondChat?.chatStatus.isDismissedSecondChatStatus()) {
                        null
                    } else {
                        HomeNextStepItem.SecondChatAvailable(
                            connectionId = nextStep.connectionId,
                            matchId = nextStep.matchId,
                            partnerDisplayName = nextStep.partnerDisplayName(),
                            chatId = nextStep.secondChat?.chatId,
                            chatStatus = nextStep.secondChat?.chatStatus?.rawValue,
                            availableAt = nextStep.secondChat?.availableAt,
                            entryClosesAt = nextStep.secondChat?.entryClosesAt,
                            expiresAt = nextStep.secondChat?.expiresAt,
                            durationMinutes = nextStep.secondChat?.durationMinutes,
                            myAttendanceStatus = nextStep.secondChat?.myAttendanceStatus?.rawValue,
                        )
                    }

                is HomeNextStep.SecondChatExpired ->
                    if (nextStep.secondChat?.chatStatus.isDismissedSecondChatStatus()) {
                        null
                    } else {
                        HomeNextStepItem.SecondChatExpired(
                            connectionId = nextStep.connectionId,
                            matchId = nextStep.matchId,
                            partnerDisplayName = nextStep.partnerDisplayName(),
                            chatId = nextStep.secondChat?.chatId,
                            chatStatus = nextStep.secondChat?.chatStatus?.rawValue,
                            availableAt = nextStep.secondChat?.availableAt,
                            entryClosesAt = nextStep.secondChat?.entryClosesAt,
                            expiresAt = nextStep.secondChat?.expiresAt,
                            durationMinutes = nextStep.secondChat?.durationMinutes,
                            myAttendanceStatus = nextStep.secondChat?.myAttendanceStatus?.rawValue,
                        )
                    }

                is HomeNextStep.SecondChatReadOnly ->
                    if (nextStep.secondChat?.chatStatus.isDismissedSecondChatStatus()) {
                        null
                    } else {
                        HomeNextStepItem.SecondChatReadOnly(
                            connectionId = nextStep.connectionId,
                            matchId = nextStep.matchId,
                            partnerDisplayName = nextStep.partnerDisplayName(),
                            chatId = nextStep.secondChat?.chatId,
                            chatStatus = nextStep.secondChat?.chatStatus?.rawValue,
                            availableAt = nextStep.secondChat?.availableAt,
                            entryClosesAt = nextStep.secondChat?.entryClosesAt,
                            expiresAt = nextStep.secondChat?.expiresAt,
                            readOnlyUntil = nextStep.secondChat?.readOnlyUntil,
                            durationMinutes = nextStep.secondChat?.durationMinutes,
                            myAttendanceStatus = nextStep.secondChat?.myAttendanceStatus?.rawValue,
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
                HomePassiveNotice.SchedulingPreparing ->
                    HomePassiveNoticeItem.SchedulingPreparing

                is HomePassiveNotice.Unknown ->
                    HomePassiveNoticeItem.Unknown(
                        rawType = notice.rawType,
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

    private fun HomeNextStep.SecondChatExpired.partnerDisplayName(): String? =
        secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
            ?: partner?.displayName?.takeIf { it.isNotBlank() }

    private fun HomeNextStep.SecondChatReadOnly.partnerDisplayName(): String? =
        secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
            ?: partner?.displayName?.takeIf { it.isNotBlank() }

    private fun ChatStatus?.isDismissedSecondChatStatus(): Boolean =
        this == ChatStatus.Cancelled ||
            this == ChatStatus.Closed
}
