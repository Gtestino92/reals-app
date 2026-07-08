package com.reals.app.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.toUserMessage
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.ui.root.LegalRequirementUiItem

@Composable
fun LegalRequirementsScreen(
    documents: List<LegalRequirementUiItem>,
    loading: Boolean,
    submittingDocumentType: LegalDocumentType?,
    error: ApiError?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onRecordRequiredAction: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onDefer: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var localOpenError by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val transitionBlocked = loading || submittingDocumentType != null || accountDeleteLoading

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Antes de continuar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Revisá los documentos vigentes. Algunas acciones de participación requieren que completes la acción indicada.",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error?.let {
            Text(
                text = it.toUserMessage(ErrorContext.Legal),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onRetryLoad, enabled = !loading && !accountDeleteLoading) {
                Text("Reintentar")
            }
        }

        localOpenError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        documents.forEach { document ->
            LegalDocumentCard(
                document = document,
                actionInProgress = submittingDocumentType != null,
                submitting = submittingDocumentType?.rawValue == document.type.rawValue,
                onOpenDocument = {
                    localOpenError = null
                    try {
                        uriHandler.openUri(document.url)
                    } catch (exception: Exception) {
                        localOpenError = "No pudimos abrir el documento. Intentá nuevamente."
                    }
                },
                onRecordRequiredAction = { onRecordRequiredAction(document.key) },
            )
        }

        accountDeleteError?.let {
            Text(
                text = it.toUserMessage(ErrorContext.Account),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDefer,
                enabled = !transitionBlocked,
                modifier = Modifier.weight(1f),
            ) {
                Text("Ahora no")
            }
            OutlinedButton(
                onClick = onSignOut,
                enabled = !transitionBlocked,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cerrar sesión")
            }
            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                enabled = !transitionBlocked,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (accountDeleteLoading) "Eliminando..." else "Eliminar cuenta")
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!accountDeleteLoading) showDeleteConfirmation = false },
            title = { Text("Eliminar cuenta") },
            text = { Text("Vamos a programar la eliminación de tu cuenta. Podés recuperarla durante el plazo disponible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteAccount()
                    },
                    enabled = !accountDeleteLoading,
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !accountDeleteLoading,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun LegalDocumentCard(
    document: LegalRequirementUiItem,
    actionInProgress: Boolean,
    submitting: Boolean,
    onOpenDocument: () -> Unit,
    onRecordRequiredAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = document.type.displayLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Versión ${document.version}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (document.satisfied) "Completado" else "Pendiente",
                color = if (document.satisfied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onOpenDocument) {
                    Text("Ver documento")
                }
                if (!document.satisfied) {
                    when (document.requiredAction) {
                        is LegalDocumentAction.Accepted,
                        is LegalDocumentAction.Acknowledged -> Button(
                            onClick = onRecordRequiredAction,
                            enabled = !actionInProgress,
                        ) {
                            Text(if (submitting) "Enviando..." else document.requiredAction.actionLabel())
                        }

                        is LegalDocumentAction.Unknown -> Text(
                            text = "Esta versión de la app no puede completar esta acción. Actualizá la app para continuar.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun LegalDocumentType.displayLabel(): String = when (this) {
    LegalDocumentType.TermsOfUse -> "Términos de uso"
    LegalDocumentType.PrivacyNotice -> "Aviso de privacidad"
    LegalDocumentType.CommunityGuidelines -> "Normas de la comunidad"
    is LegalDocumentType.Unknown -> rawValue
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.titlecase() }
}

private fun LegalDocumentAction.actionLabel(): String = when (this) {
    LegalDocumentAction.Accepted -> "Aceptar"
    LegalDocumentAction.Acknowledged -> "Confirmar lectura"
    is LegalDocumentAction.Unknown -> ""
}
