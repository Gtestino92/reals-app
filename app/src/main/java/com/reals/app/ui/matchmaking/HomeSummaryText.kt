package com.reals.app.ui.matchmaking

import com.reals.app.domain.model.HomeActiveInteractionsSummary

internal fun activeExperiencesSummaryText(summary: HomeActiveInteractionsSummary): String? {
    val parts = buildList {
        if (summary.activeInitialCount > 0) {
            add(
                "${summary.activeInitialCount} " +
                    if (summary.activeInitialCount == 1) "inicial" else "iniciales"
            )
        }
        if (summary.activeConnectionCount > 0) {
            add(
                "${summary.activeConnectionCount} " +
                    if (summary.activeConnectionCount == 1) "conexion" else "conexiones"
            )
        }
    }

    if (parts.isEmpty()) return null

    return "Experiencias activas: ${parts.joinToString(", ")}."
}

internal fun passiveNoticeText(notice: HomePassiveNoticeItem): String? =
    when (notice) {
        is HomePassiveNoticeItem.SchedulingPreparing -> if (notice.count == 1) {
            "Tenes una coordinacion en preparacion. Se habilitara mas adelante."
        } else {
            "Tenes ${notice.count} coordinaciones en preparacion. Se habilitaran mas adelante."
        }

        is HomePassiveNoticeItem.Unknown -> null
    }
