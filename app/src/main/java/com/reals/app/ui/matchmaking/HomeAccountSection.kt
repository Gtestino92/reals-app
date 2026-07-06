package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.ui.common.ApiErrorFeedbackCard

@Composable
internal fun AccountSection(
    busy: Boolean,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    changePasswordLoading: Boolean,
    changePasswordError: String?,
    changePasswordMessage: String?,
    canChangePassword: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var localChangePasswordError by remember { mutableStateOf<String?>(null) }

    fun clearChangePasswordDialog() {
        currentPassword = ""
        newPassword = ""
        confirmNewPassword = ""
        localChangePasswordError = null
    }

    LaunchedEffect(changePasswordMessage) {
        if (changePasswordMessage == changePasswordSuccessMessage && changingPassword) {
            changingPassword = false
            clearChangePasswordDialog()
        }
    }

    LaunchedEffect(changePasswordError) {
        if (changePasswordError == wrongCurrentPasswordMessage) {
            currentPassword = ""
        }
    }

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

    if (changingPassword && canChangePassword) {
        ChangePasswordDialog(
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmNewPassword = confirmNewPassword,
            localError = localChangePasswordError,
            operationError = changePasswordError,
            loading = changePasswordLoading,
            onCurrentPasswordChange = {
                currentPassword = it
                localChangePasswordError = null
            },
            onNewPasswordChange = {
                newPassword = it
                localChangePasswordError = null
            },
            onConfirmNewPasswordChange = {
                confirmNewPassword = it
                localChangePasswordError = null
            },
            onDismiss = {
                if (!changePasswordLoading) {
                    changingPassword = false
                    clearChangePasswordDialog()
                }
            },
            onSubmit = {
                val validationError = changePasswordValidationError(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    confirmNewPassword = confirmNewPassword,
                )
                if (validationError != null) {
                    localChangePasswordError = validationError
                } else {
                    localChangePasswordError = null
                    onChangePassword(currentPassword, newPassword)
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
                if (canChangePassword) {
                    OutlinedButton(
                        onClick = {
                            clearChangePasswordDialog()
                            changingPassword = true
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cambiar contraseña")
                    }
                }
                changePasswordMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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

@Composable
private fun ChangePasswordDialog(
    currentPassword: String,
    newPassword: String,
    confirmNewPassword: String,
    localError: String?,
    operationError: String?,
    loading: Boolean,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingresá tu contraseña actual y elegí una nueva.")
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    label = { Text("Contraseña actual") },
                    enabled = !loading,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewPasswordChange,
                    label = { Text("Nueva contraseña") },
                    enabled = !loading,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = onConfirmNewPasswordChange,
                    label = { Text("Repetir nueva contraseña") },
                    enabled = !loading,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                (localError ?: operationError)?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading,
                onClick = onSubmit,
            ) {
                Text(if (loading) "Guardando..." else "Guardar contraseña")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !loading,
                onClick = onDismiss,
            ) {
                Text("Cancelar")
            }
        },
    )
}

internal fun changePasswordValidationError(
    currentPassword: String,
    newPassword: String,
    confirmNewPassword: String,
): String? = when {
    currentPassword.isBlank() -> "Ingresá tu contraseña actual."
    newPassword.isBlank() -> "Ingresá una nueva contraseña."
    confirmNewPassword.isBlank() -> "Repetí la nueva contraseña."
    newPassword != confirmNewPassword -> "Las contraseñas nuevas no coinciden."
    newPassword.length < 6 -> "La nueva contraseña debe tener al menos 6 caracteres."
    newPassword == currentPassword -> "La nueva contraseña debe ser distinta de la actual."
    else -> null
}

internal const val changePasswordSuccessMessage = "Contraseña actualizada."
internal const val wrongCurrentPasswordMessage = "La contraseña actual no es correcta."

internal fun expandedAccountActionLabels(canChangePassword: Boolean): List<String> {
    return buildList {
        add("Cerrar sesion")
        if (canChangePassword) add("Cambiar contraseña")
        add("Eliminar cuenta")
    }
}
