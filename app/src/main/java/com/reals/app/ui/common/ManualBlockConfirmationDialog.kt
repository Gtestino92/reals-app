package com.reals.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext

@Composable
fun ManualBlockConfirmationDialog(
    loading: Boolean,
    error: ApiError?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!loading) onDismiss()
        },
        title = { Text("¿Bloquear a ésta persona?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Esta acción es definitiva y no se puede deshacer. " +
                        "Se cerrará la interacción actual y no volverán a ser emparejados. " +
                        "Bloquear no envía un reporte."
                )
                error?.let { ApiErrorFeedbackCard(it, ErrorContext.ManualBlock) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !loading,
            ) {
                Text(if (loading) "Bloqueando..." else "Bloquear definitivamente")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !loading,
            ) {
                Text("Cancelar")
            }
        },
    )
}
