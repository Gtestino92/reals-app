package com.reals.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.isAccountDeletionFinalized
import com.reals.app.core.network.toDisplayMessage
import com.reals.app.domain.model.BackendUser

@Composable
fun AccountDeletionRecoveryScreen(
    user: BackendUser,
    reactivating: Boolean,
    error: ApiError?,
    onReactivate: () -> Unit,
    onKeepDeletion: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Cuenta pendiente de eliminacion",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = recoveryMessage(user.deletionFinalizesAt),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Text(
                text = if (it.isAccountDeletionFinalized()) {
                    "La cuenta ya no puede recuperarse. Podes crear una cuenta nueva."
                } else {
                    it.toDisplayMessage()
                },
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                enabled = !reactivating,
                onClick = onReactivate,
            ) {
                Text(if (reactivating) "Reactivando..." else "Reactivar cuenta")
            }
            OutlinedButton(
                enabled = !reactivating,
                onClick = onKeepDeletion,
            ) {
                Text("Mantener eliminacion")
            }
        }
    }
}

private fun recoveryMessage(deletionFinalizesAt: String?): String {
    val dateText = deletionFinalizesAt?.let { " hasta el $it" }.orEmpty()
    return "Esta cuenta esta pendiente de eliminacion$dateText. Si la recuperas, conservaras tu perfil y fotos, " +
        "pero deberas activar el perfil nuevamente. Tus conexiones activas anteriores no se restauraran."
}
