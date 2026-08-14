package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.realsOutlinedTextFieldColors
import com.reals.app.ui.theme.RealsRadii

@Composable
internal fun ChatSafetyDialogs(
    showingSafetyDialog: Boolean,
    showingManualBlockDialog: Boolean,
    showExitActions: Boolean,
    canUseSafetyActions: Boolean,
    safetyDetails: String,
    selectedSafetyReason: ChatExitReason,
    actionLoading: Boolean,
    manualBlockLoading: Boolean,
    manualBlockError: ApiError?,
    onSafetyDetailsChange: (String) -> Unit,
    onSafetyReasonChange: (ChatExitReason) -> Unit,
    onDismissSafetyDialog: () -> Unit,
    onConfirmSafetyReport: () -> Unit,
    onDismissManualBlockDialog: () -> Unit,
    onConfirmManualBlock: () -> Unit,
) {
    if (showingSafetyDialog && showExitActions && canUseSafetyActions) {
        SafetyReportDialog(
            details = safetyDetails,
            selectedReason = selectedSafetyReason,
            actionLoading = actionLoading,
            onDetailsChange = onSafetyDetailsChange,
            onReasonChange = onSafetyReasonChange,
            onDismiss = onDismissSafetyDialog,
            onConfirm = onConfirmSafetyReport,
        )
    }

    if (showingManualBlockDialog) {
        ManualBlockConfirmationDialog(
            loading = manualBlockLoading,
            error = manualBlockError,
            onConfirm = onConfirmManualBlock,
            onDismiss = onDismissManualBlockDialog,
        )
    }
}

@Composable
private fun SafetyReportDialog(
    details: String,
    selectedReason: ChatExitReason,
    actionLoading: Boolean,
    onDetailsChange: (String) -> Unit,
    onReasonChange: (ChatExitReason) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var reasonMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedOption = safetyReportReasonOptions.first { it.reason == selectedReason }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar y cerrar chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Describí que pasó. Este reporte cerrará el chat por seguridad y será revisado.")
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { reasonMenuExpanded = true },
                        enabled = !actionLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(selectedOption.label)
                    }
                    DropdownMenu(
                        expanded = reasonMenuExpanded,
                        onDismissRequest = { reasonMenuExpanded = false },
                    ) {
                        safetyReportReasonOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onReasonChange(option.reason)
                                    reasonMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                selectedOption.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = onDetailsChange,
                    label = { Text("Detalle") },
                    enabled = !actionLoading,
                    minLines = 3,
                    shape = RoundedCornerShape(RealsRadii.Button),
                    colors = realsOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !actionLoading && details.isNotBlank(),
                onClick = onConfirm,
            ) {
                Text("Enviar reporte")
            }
        },
        dismissButton = {
            TextButton(enabled = !actionLoading, onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
