package com.reals.app.ui.matchmaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.theme.RealsRadii

@Composable
internal fun AccountSection(
    busy: Boolean,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    changePasswordLoading: Boolean,
    changePasswordError: String?,
    changePasswordMessage: String?,
    canChangePassword: Boolean,
    showSupportReals: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onOpenNotifications: () -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onSupportReals: () -> Unit,
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
            text = { Text("Tu cuenta quedará pendiente de eliminación y podrás recuperarla durante 30 días.") },
            confirmButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = {
                        confirmingDelete = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Programar eliminación")
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
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountSectionHeader(
                expanded = expanded,
                enabled = !busy,
                onToggle = { onExpandedChange(!expanded) },
            )
            if (expanded) {
                OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar sesión")
                }
                OutlinedButton(
                    onClick = onOpenNotifications,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Notificaciones")
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
                        Text("Cambiar contraseña de Reals")
                    }
                }
                changePasswordMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (showSupportReals) {
                    SupportRealsAccountSubsection(
                        enabled = !busy,
                        onSupportReals = onSupportReals,
                    )
                }
                Text(
                    text = "Eliminar la cuenta programa una eliminación recuperable durante 30 días y cierra la sesión.",
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
internal fun AccountSectionHeader(
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        when (accountSectionHeaderLayout(maxWidth, fontScale)) {
            AccountSectionHeaderLayout.Normal -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AccountSectionHeaderNormalTag),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountSectionHeaderText(modifier = Modifier.weight(1f))
                    AccountSectionToggleButton(
                        expanded = expanded,
                        enabled = enabled,
                        onToggle = onToggle,
                    )
                }
            }

            AccountSectionHeaderLayout.Constrained -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AccountSectionHeaderConstrainedTag),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccountSectionHeaderText(modifier = Modifier.fillMaxWidth())
                    AccountSectionToggleButton(
                        expanded = expanded,
                        enabled = enabled,
                        onToggle = onToggle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSectionHeaderText(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(AccountSectionHeaderTextTag)) {
        Text("Cuenta", style = MaterialTheme.typography.titleMedium)
        Text(
            text = AccountSectionSubtitle,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SupportRealsAccountSubsection(
    enabled: Boolean,
    onSupportReals: () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(SupportRealsTitle, style = MaterialTheme.typography.titleSmall)
        Text(
            text = SupportRealsBody,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = onSupportReals,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(SupportRealsCta)
        }
    }
    HorizontalDivider()
}

@Composable
private fun AccountSectionToggleButton(
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onToggle,
        enabled = enabled,
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
        modifier = modifier
            .heightIn(min = AccountSectionToggleMinHeight)
            .testTag(AccountSectionToggleTag),
    ) {
        Text(
            text = if (expanded) "Ocultar" else "Abrir",
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

internal enum class AccountSectionHeaderLayout {
    Normal,
    Constrained,
}

internal fun accountSectionHeaderLayout(maxWidth: Dp, fontScale: Float): AccountSectionHeaderLayout {
    val constrained = maxWidth < 300.dp ||
        (maxWidth < 340.dp && fontScale >= 1.3f) ||
        fontScale >= 1.8f
    return if (constrained) AccountSectionHeaderLayout.Constrained else AccountSectionHeaderLayout.Normal
}

internal fun shouldShowSupportReals(showCafecitoSupport: Boolean): Boolean = showCafecitoSupport

internal val AccountSectionToggleMinHeight = 48.dp
internal const val AccountSectionHeaderNormalTag = "account_section_header_normal"
internal const val AccountSectionHeaderConstrainedTag = "account_section_header_constrained"
internal const val AccountSectionHeaderTextTag = "account_section_header_text"
internal const val AccountSectionToggleTag = "account_section_toggle"
internal const val AccountSectionSubtitle = "Sesión y otras opciones."
internal const val SupportRealsTitle = "Apoyar Reals"
internal const val SupportRealsBody =
    "Si te gusta Reals y querés ayudar a sostener el proyecto, podés hacer un aporte voluntario. " +
        "No te da beneficios dentro de la app."
internal const val SupportRealsCta = "Apoyar en Cafecito"

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
        title = { Text("Cambiar contraseña de Reals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Actualizá la contraseña que usás para iniciar sesión en Reals con tu email. " +
                        "Tu contraseña de Google no cambia."
                )
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
        add("Cerrar sesión")
        add("Notificaciones")
        if (canChangePassword) add("Cambiar contraseña de Reals")
        add("Eliminar cuenta")
    }
}
