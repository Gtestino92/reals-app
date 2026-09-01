package com.reals.app.ui.root

internal fun safetyReportSuccessMessage(blockUser: Boolean): String =
    if (blockUser) {
        "Reporte enviado. Cerramos esta conversación y bloqueamos a esta persona. No volverán a ser emparejados."
    } else {
        "Reporte enviado. Cerramos esta conversación por seguridad y será revisado."
    }
