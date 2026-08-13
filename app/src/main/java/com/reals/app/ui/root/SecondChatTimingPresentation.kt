package com.reals.app.ui.root

import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.SecondChatAttendanceStatus
import com.reals.app.domain.model.SecondChatResolutionRequest
import com.reals.app.domain.model.SecondChatResolutionRequestStatus
import com.reals.app.domain.model.SecondChatResolutionRequestType
import com.reals.app.domain.model.SecondChatStatus

internal const val SECOND_CHAT_ABSOLUTE_EXPIRY_WARNING_MILLIS = 10 * 60 * 1000L

internal data class ReceivedSecondChatStatus(
    val status: SecondChatStatus,
    val receivedAtMillis: Long,
)

internal data class SecondChatTimingPresentation(
    val joined: Boolean,
    val lifecycleActive: Boolean,
    val remainingMillis: Long?,
    val genuinelyActive: Boolean,
    val locallyExpired: Boolean,
    val showAbsoluteExpiryWarning: Boolean,
)

internal enum class SecondChatEntryAvailabilityState {
    BeforeStart,
    Joinable,
    EntryClosed,
    Unavailable,
    Joined,
}

internal data class SecondChatEntryAvailabilityPresentation(
    val state: SecondChatEntryAvailabilityState,
    val title: String,
    val message: String,
)

internal data class SecondChatResolutionPresentation(
    val createCompletion: SecondChatCreateResolutionPresentation? = null,
    val completionCooldown: SecondChatCooldownPresentation? = null,
    val createInactivityClaim: SecondChatCreateResolutionPresentation? = null,
    val activeRequest: SecondChatActiveResolutionPresentation? = null,
)

internal data class SecondChatCreateResolutionPresentation(
    val enabled: Boolean,
    val label: String,
    val confirmationTitle: String,
    val confirmationBody: String,
)

internal data class SecondChatCooldownPresentation(
    val remainingMillis: Long,
    val message: String,
    val refreshKey: String?,
)

internal data class SecondChatActiveResolutionPresentation(
    val requestId: String,
    val type: SecondChatResolutionRequestType,
    val role: SecondChatResolutionRole,
    val title: String,
    val message: String,
    val remainingMillis: Long?,
    val locallyExpired: Boolean,
    val showAcceptRejectControls: Boolean,
    val controlsEnabled: Boolean,
    val refreshKey: String?,
)

internal enum class SecondChatResolutionRole {
    Requester,
    Responder,
    Other,
}

internal fun SecondChatLifecycleUiState.timingPresentation(
    nowMillis: Long = System.currentTimeMillis(),
): SecondChatTimingPresentation {
    val currentStatus = status ?: return emptySecondChatTimingPresentation()
    val receivedAtMillis = statusReceivedAtMillis ?: return emptySecondChatTimingPresentation(
        joined = currentStatus.isJoinedSecondChat(),
        lifecycleActive = currentStatus.chatStatus == ChatStatus.Active,
    )
    return currentStatus.secondChatTimingPresentation(
        statusReceivedAtMillis = receivedAtMillis,
        nowMillis = nowMillis,
    )
}

internal fun SecondChatStatus.secondChatTimingPresentation(
    statusReceivedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): SecondChatTimingPresentation {
    val joined = isJoinedSecondChat()
    val lifecycleActive = chatStatus == ChatStatus.Active
    val remainingMillis = remainingAbsoluteMillis(
        statusReceivedAtMillis = statusReceivedAtMillis,
        nowMillis = nowMillis,
    )
    val genuinelyActive = joined && lifecycleActive && remainingMillis != null && remainingMillis > 0
    val locallyExpired = joined && lifecycleActive && remainingMillis != null && remainingMillis <= 0
    return SecondChatTimingPresentation(
        joined = joined,
        lifecycleActive = lifecycleActive,
        remainingMillis = remainingMillis,
        genuinelyActive = genuinelyActive,
        locallyExpired = locallyExpired,
        showAbsoluteExpiryWarning = genuinelyActive &&
            remainingMillis <= SECOND_CHAT_ABSOLUTE_EXPIRY_WARNING_MILLIS,
    )
}

internal fun SecondChatStatus.entryAvailabilityPresentation(
    statusReceivedAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): SecondChatEntryAvailabilityPresentation? {
    if (myAttendanceStatus != SecondChatAttendanceStatus.Pending) {
        return SecondChatEntryAvailabilityPresentation(
            state = SecondChatEntryAvailabilityState.Joined,
            title = "",
            message = "",
        )
    }
    if (canJoin) {
        return SecondChatEntryAvailabilityPresentation(
            state = SecondChatEntryAvailabilityState.Joinable,
            title = "",
            message = "",
        )
    }

    val synchronizedNow = synchronizedNowMillis(
        statusReceivedAtMillis = statusReceivedAtMillis,
        nowMillis = nowMillis,
    )
    val scheduledAtMillis = backendInstantOrNull(scheduledAt)?.toEpochMilli()
    val entryClosesAtMillis = backendInstantOrNull(entryClosesAt)?.toEpochMilli()

    return when {
        synchronizedNow != null &&
            scheduledAtMillis != null &&
            synchronizedNow < scheduledAtMillis ->
            SecondChatEntryAvailabilityPresentation(
                state = SecondChatEntryAvailabilityState.BeforeStart,
                title = "Todavía no está disponible",
                message = "El segundo chat abre a las ${com.reals.app.ui.common.formatBackendTime(scheduledAt)}.",
            )
        synchronizedNow != null &&
            entryClosesAtMillis != null &&
            synchronizedNow >= entryClosesAtMillis ->
            SecondChatEntryAvailabilityPresentation(
                state = SecondChatEntryAvailabilityState.EntryClosed,
                title = "Segundo chat vencido",
                message = "La ventana para entrar terminó.",
            )
        else ->
            SecondChatEntryAvailabilityPresentation(
                state = SecondChatEntryAvailabilityState.Unavailable,
                title = "Segundo chat no disponible",
                message = "No se puede entrar en este momento.",
            )
    }
}

internal fun SecondChatLifecycleUiState.resolutionPresentation(
    currentUserId: String,
    nowMillis: Long = System.currentTimeMillis(),
    actionLoading: Boolean = false,
): SecondChatResolutionPresentation {
    val currentStatus = status ?: return SecondChatResolutionPresentation()
    val receivedAtMillis = statusReceivedAtMillis ?: return SecondChatResolutionPresentation()
    val timing = timingPresentation(nowMillis)
    if (!timing.genuinelyActive) return SecondChatResolutionPresentation()

    currentStatus.actionableResolutionRequest(
        currentUserId = currentUserId,
        statusReceivedAtMillis = receivedAtMillis,
        nowMillis = nowMillis,
        actionLoading = actionLoading,
    )?.let { active ->
        return SecondChatResolutionPresentation(activeRequest = active)
    }

    val cooldownRemainingMillis = currentStatus.mutualCompletionCooldownUntil
        ?.let { cooldownUntil ->
            currentStatus.remainingMillisFromServerSnapshot(
                targetTime = cooldownUntil,
                statusReceivedAtMillis = receivedAtMillis,
                nowMillis = nowMillis,
            )
        }
    val cooldown = cooldownRemainingMillis
        ?.takeIf { it > 0 }
        ?.let { remaining ->
            SecondChatCooldownPresentation(
                remainingMillis = remaining,
                message = "Podr\u00e1s volver a proponer el cierre en unos segundos.",
                refreshKey = null,
            )
        }
    val completedCooldownKey = cooldownRemainingMillis
        ?.takeIf { it <= 0 }
        ?.let { "completion-cooldown:${currentStatus.mutualCompletionCooldownUntil}" }

    return SecondChatResolutionPresentation(
        createCompletion = if (
            currentStatus.canRequestMutualCompletion &&
            cooldown == null &&
            completedCooldownKey == null
        ) {
            SecondChatCreateResolutionPresentation(
                enabled = !actionLoading,
                label = "Finalizar de com\u00fan acuerdo",
                confirmationTitle = "Finalizar de com\u00fan acuerdo",
                confirmationBody = "La otra persona tendr\u00e1 un minuto para confirmar. Pueden seguir conversando; si alguien env\u00eda un mensaje, la solicitud se cancela.",
            )
        } else {
            null
        },
        completionCooldown = cooldown ?: completedCooldownKey?.let {
            SecondChatCooldownPresentation(
                remainingMillis = 0,
                message = "Podr\u00e1s volver a proponer el cierre en unos segundos.",
                refreshKey = it,
            )
        },
        createInactivityClaim = if (currentStatus.canClaimPartnerInactivity) {
            SecondChatCreateResolutionPresentation(
                enabled = !actionLoading,
                label = "La otra persona no respondi\u00f3",
                confirmationTitle = "Reclamar falta de respuesta",
                confirmationBody = "La otra persona tendr\u00e1 un minuto para responder. Si alguien env\u00eda un nuevo mensaje, el reclamo se cancela.",
            )
        } else {
            null
        },
    )
}

internal fun SecondChatLifecycleUiState.withStatusSnapshot(
    snapshot: ReceivedSecondChatStatus,
): SecondChatLifecycleUiState = copy(
    status = snapshot.status,
    statusReceivedAtMillis = snapshot.receivedAtMillis,
)

internal fun SecondChatStatus.remainingMillisFromServerSnapshot(
    targetTime: String,
    statusReceivedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val target = backendInstantOrNull(targetTime) ?: return null
    val synchronizedNow = synchronizedNowMillis(
        statusReceivedAtMillis = statusReceivedAtMillis,
        nowMillis = nowMillis,
    ) ?: return null
    return target.toEpochMilli() - synchronizedNow
}

private fun SecondChatStatus.synchronizedNowMillis(
    statusReceivedAtMillis: Long?,
    nowMillis: Long,
): Long? {
    val receivedAtMillis = statusReceivedAtMillis ?: return null
    val server = backendInstantOrNull(serverTime) ?: return null
    return server.toEpochMilli() + (nowMillis - receivedAtMillis)
}

internal fun ReceivedSecondChatStatus.remainingMillisAtReceipt(targetTime: String): Long? =
    status.remainingMillisFromServerSnapshot(
        targetTime = targetTime,
        statusReceivedAtMillis = receivedAtMillis,
        nowMillis = receivedAtMillis,
    )

private fun SecondChatStatus.remainingAbsoluteMillis(
    statusReceivedAtMillis: Long,
    nowMillis: Long,
): Long? = remainingMillisFromServerSnapshot(
    targetTime = absoluteExpiresAt,
    statusReceivedAtMillis = statusReceivedAtMillis,
    nowMillis = nowMillis,
)

private fun SecondChatStatus.isJoinedSecondChat(): Boolean =
    chatId?.isNotBlank() == true &&
        (
            myAttendanceStatus == SecondChatAttendanceStatus.OnTime ||
                myAttendanceStatus == SecondChatAttendanceStatus.Late
            )

private fun SecondChatStatus.actionableResolutionRequest(
    currentUserId: String,
    statusReceivedAtMillis: Long,
    nowMillis: Long,
    actionLoading: Boolean,
): SecondChatActiveResolutionPresentation? {
    val request = activeResolutionRequest ?: return null
    if (request.status != SecondChatResolutionRequestStatus.Pending) return null
    if (
        request.type != SecondChatResolutionRequestType.MutualCompletion &&
        request.type != SecondChatResolutionRequestType.PartnerInactivity
    ) {
        return null
    }
    val remainingMillis = request.expiresAt?.let {
        remainingMillisFromServerSnapshot(
            targetTime = it,
            statusReceivedAtMillis = statusReceivedAtMillis,
            nowMillis = nowMillis,
        )
    }
    val locallyExpired = remainingMillis?.let { it <= 0 } == true
    val role = request.roleFor(currentUserId)
    val mutualResponder = request.type == SecondChatResolutionRequestType.MutualCompletion &&
        role == SecondChatResolutionRole.Responder
    return SecondChatActiveResolutionPresentation(
        requestId = request.id,
        type = request.type,
        role = role,
        title = request.presentationTitle(role),
        message = request.presentationMessage(role),
        remainingMillis = remainingMillis,
        locallyExpired = locallyExpired,
        showAcceptRejectControls = mutualResponder,
        controlsEnabled = mutualResponder && !locallyExpired && !actionLoading,
        refreshKey = if (locallyExpired) {
            "resolution:${request.id}:${request.expiresAt}"
        } else {
            null
        },
    )
}

private fun SecondChatResolutionRequest.roleFor(currentUserId: String): SecondChatResolutionRole =
    when (currentUserId) {
        requesterUserId -> SecondChatResolutionRole.Requester
        responderUserId -> SecondChatResolutionRole.Responder
        else -> SecondChatResolutionRole.Other
    }

private fun SecondChatResolutionRequest.presentationTitle(role: SecondChatResolutionRole): String =
    when (type) {
        SecondChatResolutionRequestType.MutualCompletion -> when (role) {
            SecondChatResolutionRole.Requester -> "Cierre propuesto"
            SecondChatResolutionRole.Responder -> "Te propusieron finalizar"
            SecondChatResolutionRole.Other -> "Cierre pendiente"
        }
        SecondChatResolutionRequestType.PartnerInactivity -> when (role) {
            SecondChatResolutionRole.Responder -> "Respond\u00e9 para mantener el chat"
            else -> "Reclamo por falta de respuesta"
        }
        else -> "Solicitud pendiente"
    }

private fun SecondChatResolutionRequest.presentationMessage(role: SecondChatResolutionRole): String =
    when (type) {
        SecondChatResolutionRequestType.MutualCompletion -> when (role) {
            SecondChatResolutionRole.Requester ->
                "Esperando confirmaci\u00f3n. Pueden seguir conversando; un nuevo mensaje cancela ésta solicitud."
            SecondChatResolutionRole.Responder ->
                "La otra persona propuso terminar el chat de com\u00fan acuerdo."
            SecondChatResolutionRole.Other ->
                "Hay una propuesta de cierre pendiente."
        }
        SecondChatResolutionRequestType.PartnerInactivity -> when (role) {
            SecondChatResolutionRole.Responder ->
                "Respond\u00e9 antes de que termine el tiempo para mantener activa la conversaci\u00f3n."
            else ->
                "Esperando respuesta. Si no responde a tiempo, el chat puede finalizar por inactividad."
        }
        else -> "Hay una solicitud pendiente."
    }

private fun emptySecondChatTimingPresentation(
    joined: Boolean = false,
    lifecycleActive: Boolean = false,
): SecondChatTimingPresentation = SecondChatTimingPresentation(
    joined = joined,
    lifecycleActive = lifecycleActive,
    remainingMillis = null,
    genuinelyActive = false,
    locallyExpired = false,
    showAbsoluteExpiryWarning = false,
)
