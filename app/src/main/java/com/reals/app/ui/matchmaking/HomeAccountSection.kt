package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.ui.common.ApiErrorFeedbackCard

@Composable
internal fun AccountSection(
    busy: Boolean,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = {
                if (!accountDeleteLoading) confirmingDelete = false
            },
            title = { Text("Eliminar cuenta") },
            text = { Text("Tu cuenta quedara pendiente de eliminacion y podras recuperarla durante 30 dias.") },
            confirmButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = {
                        confirmingDelete = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Programar eliminacion")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = { confirmingDelete = false },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Cuenta", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Sesion y acciones sensibles.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }, enabled = !busy) {
                    Text(if (expanded) "Ocultar" else "Abrir")
                }
            }
            if (expanded) {
                OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar sesion")
                }
                Text(
                    text = "Eliminar la cuenta programa una eliminacion recuperable durante 30 dias y cierra la sesion.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                accountDeleteError?.let { ApiErrorFeedbackCard(it, ErrorContext.Account) }
                OutlinedButton(onClick = { confirmingDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (accountDeleteLoading) "Eliminando..." else "Eliminar cuenta")
                }
            }
        }
    }
}
