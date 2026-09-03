package com.reals.app.ui.chat

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
    blockUser: Boolean,
    actionLoading: Boolean,
    manualBlockLoading: Boolean,
    manualBlockError: ApiError?,
    onSafetyDetailsChange: (String) -> Unit,
    onSafetyReasonChange: (ChatExitReason) -> Unit,
    onBlockUserChange: (Boolean) -> Unit,
    onDismissSafetyDialog: () -> Unit,
    onConfirmSafetyReport: () -> Unit,
    onDismissManualBlockDialog: () -> Unit,
    onConfirmManualBlock: () -> Unit,
) {
    if (showingSafetyDialog && showExitActions && canUseSafetyActions) {
        SafetyReportDialog(
            details = safetyDetails,
            selectedReason = selectedSafetyReason,
            blockUser = blockUser,
            actionLoading = actionLoading,
            onDetailsChange = onSafetyDetailsChange,
            onReasonChange = onSafetyReasonChange,
            onBlockUserChange = onBlockUserChange,
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
    blockUser: Boolean,
    actionLoading: Boolean,
    onDetailsChange: (String) -> Unit,
    onReasonChange: (ChatExitReason) -> Unit,
    onBlockUserChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var reasonMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedOption = safetyReportReasonOptions.first { it.reason == selectedReason }

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text("Reportar y cerrar chat") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Describí qué pasó. Este reporte cerrará el chat por seguridad y será revisado.")
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { reasonMenuExpanded = true },
                        enabled = !actionLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Motivo: ${selectedOption.label}",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                            )
                            Text("▼")
                        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = blockUser,
                            enabled = !actionLoading,
                            role = Role.Checkbox,
                            onValueChange = onBlockUserChange,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = blockUser,
                        onCheckedChange = null,
                        enabled = !actionLoading,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("También bloquear a esta persona")
                        Text(
                            text = "Si la bloqueás, no volverán a ser emparejados. El bloqueo es definitivo.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
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
