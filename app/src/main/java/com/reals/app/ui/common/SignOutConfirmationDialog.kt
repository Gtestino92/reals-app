package com.reals.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SignOutConfirmationDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = { Text("Cerrar sesión") },
        text = { Text(SignOutConfirmationBody) },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                modifier = Modifier.testTag(SignOutConfirmButtonTag),
            ) {
                Text("Cerrar sesión")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
                modifier = Modifier.testTag(SignOutCancelButtonTag),
            ) {
                Text("Cancelar")
            }
        },
    )
}

const val SignOutCancelButtonTag = "sign_out_cancel_button"
const val SignOutConfirmButtonTag = "sign_out_confirm_button"
const val SignOutConfirmationBody = "Vas a cerrar tu sesión en este dispositivo."
