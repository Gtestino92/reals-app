package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.toDisplayMessage

@Composable
fun DeleteAccountSection(
    busy: Boolean,
    loading: Boolean,
    error: ApiError?,
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
                Text("Esta accion soft-deletea tu usuario de Reals, cierra engagements activos y cierra la sesion local.")
            },
            confirmButton = {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        confirmingDeleteAccount = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Eliminar definitivamente")
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
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Cuenta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Text(
                text = "Eliminar la cuenta borra tu usuario y cierra la sesion.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            error?.let {
                Text(
                    text = it.toDisplayMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedButton(
                onClick = { confirmingDeleteAccount = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Eliminando cuenta..." else "Eliminar cuenta")
            }
        }
    }
}
