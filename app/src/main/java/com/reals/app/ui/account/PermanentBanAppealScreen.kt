package com.reals.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.PermanentBanAppealState
import com.reals.app.domain.model.PermanentBanAppealStatus
import com.reals.app.ui.common.ApiErrorFeedbackCard

private const val MaxAppealStatementLength = 1000

@Composable
fun PermanentBanAppealScreen(
    appeal: PermanentBanAppealState?,
    loading: Boolean,
    submitting: Boolean,
    error: ApiError?,
    normalBootstrapError: ApiError?,
    onSubmit: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryApprovedBootstrap: () -> Unit,
    onSignOut: () -> Unit,
) {
    var statement by rememberSaveable { mutableStateOf("") }
    val status = appeal?.status

    LaunchedEffect(status) {
        if (status != PermanentBanAppealStatus.Available) {
            statement = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = permanentBanAppealTitle(appeal, loading, normalBootstrapError),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = permanentBanAppealBody(appeal, loading, normalBootstrapError),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (status == PermanentBanAppealStatus.Available && appeal.banActive) {
            OutlinedTextField(
                value = statement,
                onValueChange = { statement = it.take(MaxAppealStatementLength) },
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                enabled = !loading && !submitting,
                label = {
                    Text("Contanos por qué considerás que deberíamos revisar la decisión.")
                },
                supportingText = {
                    Text("${statement.length}/$MaxAppealStatementLength")
                },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }

        error?.let {
            ApiErrorFeedbackCard(
                error = it,
                context = ErrorContext.Account,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        normalBootstrapError?.let {
            ApiErrorFeedbackCard(
                error = it,
                context = ErrorContext.Account,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        PermanentBanAppealActions(
            appeal = appeal,
            loading = loading,
            submitting = submitting,
            statement = statement,
            normalBootstrapError = normalBootstrapError,
            onSubmit = { onSubmit(statement.trim()) },
            onRefresh = onRefresh,
            onRetryApprovedBootstrap = onRetryApprovedBootstrap,
            onSignOut = onSignOut,
        )
    }
}

@Composable
private fun PermanentBanAppealActions(
    appeal: PermanentBanAppealState?,
    loading: Boolean,
    submitting: Boolean,
    statement: String,
    normalBootstrapError: ApiError?,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onRetryApprovedBootstrap: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (appeal?.status) {
            PermanentBanAppealStatus.Available -> {
                Button(
                    enabled = canSubmitPermanentBanAppeal(appeal, statement, loading, submitting),
                    onClick = onSubmit,
                ) {
                    Text(if (submitting) "Enviando..." else "Solicitar revisión")
                }
                OutlinedButton(
                    enabled = !loading && !submitting,
                    onClick = onSignOut,
                ) {
                    Text("Cerrar sesión")
                }
            }

            PermanentBanAppealStatus.Pending,
            PermanentBanAppealStatus.Rejected,
            is PermanentBanAppealStatus.Unknown,
            null -> {
                Button(
                    enabled = !loading && !submitting,
                    onClick = onRefresh,
                ) {
                    Text(if (loading) "Actualizando..." else "Actualizar estado")
                }
                OutlinedButton(
                    enabled = !loading && !submitting,
                    onClick = onSignOut,
                ) {
                    Text("Cerrar sesión")
                }
            }

            PermanentBanAppealStatus.Approved -> {
                Button(
                    enabled = normalBootstrapError != null && !loading && !submitting,
                    onClick = onRetryApprovedBootstrap,
                ) {
                    Text(if (loading) "Actualizando..." else "Reintentar")
                }
                OutlinedButton(
                    enabled = !loading && !submitting,
                    onClick = onSignOut,
                ) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}

internal fun canSubmitPermanentBanAppeal(
    appeal: PermanentBanAppealState?,
    statement: String,
    loading: Boolean,
    submitting: Boolean,
): Boolean =
    appeal?.status == PermanentBanAppealStatus.Available &&
        appeal.banActive &&
        !loading &&
        !submitting &&
        statement.trim().isNotBlank() &&
        statement.length <= MaxAppealStatementLength

internal fun permanentBanAppealTitle(
    appeal: PermanentBanAppealState?,
    loading: Boolean,
    normalBootstrapError: ApiError?,
): String = when {
    appeal?.status == PermanentBanAppealStatus.Pending -> "Revisión solicitada"
    appeal?.status == PermanentBanAppealStatus.Rejected -> "Revisión finalizada"
    appeal?.status == PermanentBanAppealStatus.Approved && normalBootstrapError != null -> "Revisión aprobada"
    appeal?.status == PermanentBanAppealStatus.Approved || loading -> "Actualizando estado"
    else -> "Cuenta suspendida"
}

internal fun permanentBanAppealBody(
    appeal: PermanentBanAppealState?,
    loading: Boolean,
    normalBootstrapError: ApiError?,
): String = when (appeal?.status) {
    PermanentBanAppealStatus.Available ->
        "Tu cuenta fue suspendida permanentemente.\n\n" +
            "Si considerás que la decisión fue incorrecta, podés solicitar una revisión."

    PermanentBanAppealStatus.Pending ->
        "Recibimos tu solicitud de revisión.\n\n" +
            "Tu cuenta continuará suspendida mientras evaluamos la decisión."

    PermanentBanAppealStatus.Rejected ->
        "Revisamos tu solicitud y la suspensión permanente continúa vigente."

    PermanentBanAppealStatus.Approved -> if (normalBootstrapError != null) {
        "Tu solicitud fue aprobada, pero no pudimos actualizar tu sesión. Intentá nuevamente."
    } else {
        "Tu solicitud fue aprobada. Estamos actualizando tu sesión."
    }

    is PermanentBanAppealStatus.Unknown -> "No pudimos confirmar el estado de tu solicitud. Intentá actualizarlo."
    null -> if (loading) {
        "Estamos confirmando el estado de tu suspensión."
    } else {
        "No pudimos confirmar el estado de tu suspensión. Intentá actualizarlo."
    }
}
