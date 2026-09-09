package com.reals.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.reals.app.core.network.isAccountDeletionFinalized
import com.reals.app.domain.model.BackendUser
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.formatBackendDate

@Composable
fun AccountDeletionRecoveryScreen(
    user: BackendUser,
    reactivating: Boolean,
    finalizingDeletion: Boolean,
    error: ApiError?,
    onReactivate: () -> Unit,
    onKeepDeletion: () -> Unit,
    onFinalizeDeletion: () -> Unit,
) {
    var confirmingFinalization by rememberSaveable { mutableStateOf(false) }
    val busy = accountDeletionRecoveryActionsBusy(reactivating, finalizingDeletion)

    if (confirmingFinalization) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) confirmingFinalization = false
            },
            title = { Text("Eliminar definitivamente la cuenta") },
            text = {
                Text(
                    "Esta acción no se puede deshacer y perderás la posibilidad de recuperar esta cuenta. " +
                        "Después podrás crear una cuenta nueva y elegir nuevamente el método de inicio de sesión."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmingFinalization = false
                        onFinalizeDeletion()
                    },
                ) {
                    Text("Eliminar definitivamente")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmingFinalization = false },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Cuenta pendiente de eliminación",
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
            if (it.isAccountDeletionFinalized()) {
                FeedbackCard(
                    title = "La cuenta ya no puede recuperarse",
                    message = "Podés crear una cuenta nueva.",
                    tone = FeedbackTone.Error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                ApiErrorFeedbackCard(
                    error = it,
                    context = ErrorContext.Account,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                enabled = !busy,
                onClick = onReactivate,
            ) {
                Text(if (reactivating) "Reactivando..." else "Reactivar cuenta")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = onKeepDeletion,
            ) {
                Text("Mantener eliminación")
            }
        }
        OutlinedButton(
            enabled = !busy,
            onClick = { confirmingFinalization = true },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(permanentDeletionButtonText(finalizingDeletion))
        }
    }
}

internal fun accountDeletionRecoveryActionsBusy(
    reactivating: Boolean,
    finalizingDeletion: Boolean,
): Boolean = reactivating || finalizingDeletion

internal fun permanentDeletionButtonText(finalizingDeletion: Boolean): String =
    if (finalizingDeletion) "Eliminando definitivamente..." else "Eliminar definitivamente ahora"

private fun recoveryMessage(deletionFinalizesAt: String?): String {
    val dateText = deletionFinalizesAt?.let { " hasta el ${formatBackendDate(it)}" }
        ?: " durante 30 días"
    return "Esta cuenta está pendiente de eliminación$dateText. Si la recuperás, conservarás tu perfil y fotos, " +
        "pero deberás activar el perfil nuevamente. Tus conexiones activas anteriores no se restaurarán."
}

