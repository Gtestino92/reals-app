package com.reals.app.ui.common

import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.ProfileStatus

fun ProfileStatus.userLabel(): String = when (this) {
    ProfileStatus.Active -> "Activo"
    ProfileStatus.Draft -> "Borrador"
    ProfileStatus.Inactive -> "Pausado"
    is ProfileStatus.Unknown -> "Estado no disponible"
}

fun ProfileStatus.userDescription(): String = when (this) {
    ProfileStatus.Active -> "Tu perfil esta listo para buscar chat."
    ProfileStatus.Draft -> "Tu perfil todavia esta en borrador. Activalo cuando termines de completar tus fotos."
    ProfileStatus.Inactive -> "Tu perfil esta pausado por el momento."
    is ProfileStatus.Unknown -> "No pudimos leer el estado actual de tu perfil."
}

fun photoValidationLabel(value: String): String = when (value.uppercase()) {
    "VALIDATED", "APPROVED" -> "Aprobada"
    "PENDING", "PENDING_VALIDATION", "PROCESSING" -> "En revision"
    "REJECTED", "INVALID" -> "Necesita cambios"
    else -> "En revision"
}

fun MatchState.userLabel(): String = when (this) {
    MatchState.ChatActive -> "Chat en curso"
    MatchState.VisualPhase -> "Revision visual"
    MatchState.VisualApproved -> "Revision aprobada"
    MatchState.ChatRejected -> "Chat cerrado"
    MatchState.VisualRejected -> "Revision cerrada"
    MatchState.Expired -> "Expirado"
    is MatchState.Unknown -> "Estado no disponible"
}

fun ChatStatus.userLabel(): String = when (this) {
    ChatStatus.Available -> "Disponible"
    ChatStatus.Active -> "En curso"
    ChatStatus.Cancelled -> "Cancelado"
    ChatStatus.Expired -> "Expirado"
    ChatStatus.Abandoned -> "Abandonado"
    ChatStatus.Closed -> "Cerrado"
    ChatStatus.Finished -> "Finalizado"
    is ChatStatus.Unknown -> "Estado no disponible"
}

fun ConnectionState.userLabel(): String = when (this) {
    ConnectionState.SchedulingPhase -> "Coordinando proximo encuentro"
    ConnectionState.SecondChatScheduled -> "Segundo chat programado"
    ConnectionState.SecondChatAvailable -> "Segundo chat disponible"
    ConnectionState.SecondChat -> "Segundo chat en curso"
    ConnectionState.Closed -> "Conexion cerrada"
    is ConnectionState.Unknown -> "Estado no disponible"
}

fun ChatExitRequestType.userLabel(): String = when (this) {
    ChatExitRequestType.MutualCancel -> "Cancelacion propuesta"
    ChatExitRequestType.UnilateralCancel -> "Cancelacion"
    ChatExitRequestType.SafetyReport -> "Reporte de seguridad"
    is ChatExitRequestType.Unknown -> "Solicitud"
}

fun ChatExitRequestStatus.userLabel(): String = when (this) {
    ChatExitRequestStatus.Pending -> "Pendiente"
    ChatExitRequestStatus.Accepted -> "Aceptada"
    ChatExitRequestStatus.Rejected -> "Rechazada"
    is ChatExitRequestStatus.Unknown -> "Estado no disponible"
}

fun ChatExitReason.userLabel(): String = when (this) {
    ChatExitReason.NoLongerInterested -> "Ya no hay interes"
    ChatExitReason.InappropriateBehavior -> "Comportamiento inapropiado"
    ChatExitReason.Harassment -> "Acoso"
    ChatExitReason.Other -> "Otro motivo"
    is ChatExitReason.Unknown -> "Motivo no disponible"
}
