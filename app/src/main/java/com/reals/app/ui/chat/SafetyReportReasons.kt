package com.reals.app.ui.chat

import com.reals.app.domain.model.ChatExitReason

internal data class SafetyReportReasonOption(
    val label: String,
    val reason: ChatExitReason,
    val description: String? = null,
)

internal val safetyReportReasonOptions = listOf(
    SafetyReportReasonOption(
        label = "Comportamiento inapropiado",
        reason = ChatExitReason.InappropriateBehavior,
    ),
    SafetyReportReasonOption(
        label = "Acoso",
        reason = ChatExitReason.Harassment,
    ),
    SafetyReportReasonOption(
        label = "Seguridad de menores",
        reason = ChatExitReason.ChildSafetyConcern,
        description = "Posible situación que involucra a una persona menor de edad o un riesgo para menores.",
    ),
    SafetyReportReasonOption(
        label = "Otro",
        reason = ChatExitReason.Other,
    ),
)

internal fun safetyReportReasonFromRawValue(rawValue: String): ChatExitReason =
    safetyReportReasonOptions
        .firstOrNull { it.reason.rawValue == rawValue }
        ?.reason
        ?: ChatExitReason.InappropriateBehavior
