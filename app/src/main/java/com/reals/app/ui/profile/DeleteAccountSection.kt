package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
fun DeleteAccountSection(
    busy: Boolean,
    loading: Boolean,
    error: ApiError?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var confirmingDeleteAccount by rememberSaveable { mutableStateOf(false) }

    if (confirmingDeleteAccount) {
        AlertDialog(
            onDismissRequest = {
                if (!loading) confirmingDeleteAccount = false
            },
            title = { Text("Eliminar cuenta") },
            text = {
                Text("Tu cuenta quedara pendiente de eliminacion y podras recuperarla durante 30 dias.")
            },
            confirmButton = {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        confirmingDeleteAccount = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Programar eliminacion")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !loading,
                    onClick = { confirmingDeleteAccount = false },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Cuenta",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Sesion y acciones sensibles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }, enabled = !busy) {
                    Text(if (expanded) "Ocultar" else "Abrir")
                }
            }

            if (expanded) {
                Button(
                    onClick = onSignOut,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cerrar sesion")
                }

                Text(
                    text = "Eliminar la cuenta programa una eliminacion recuperable durante 30 dias y cierra la sesion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                error?.let { ApiErrorFeedbackCard(it, ErrorContext.Account) }

                OutlinedButton(
                    onClick = { confirmingDeleteAccount = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loading) "Programando eliminacion..." else "Eliminar cuenta")
                }
            }
        }
    }
}
