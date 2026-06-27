package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.userLabel

@Composable
fun VisualApprovalScreen(
    matchId: String,
    match: Match?,
    profile: VisualProfile?,
    partnerMessage: String?,
    partnerMessageLoaded: Boolean,
    readingPartnerMessage: Boolean,
    partnerMessageError: ApiError?,
    myPersonalMessageSubmitted: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    writingMessage: Boolean,
    deciding: Boolean,
    decidingLabel: String?,
    error: ApiError?,
    message: String?,
    onRefresh: () -> Unit,
    onReadPartnerMessage: () -> Unit,
    onSavePersonalMessage: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBackHome: () -> Unit,
) {
    var personalMessage by rememberSaveable(matchId) { mutableStateOf("") }
    val busy = loading || refreshing || readingPartnerMessage || writingMessage || deciding
    val decisionBlockedByUnreadPartnerMessage =
        profile?.decisionRequiresPartnerPersonalMessageRead == true
    val canMakeVisualDecision = !busy && profile != null && !decisionBlockedByUnreadPartnerMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Aprobacion visual",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Revisa el perfil visual antes de decidir si queres continuar.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            match = match,
            loading = loading,
            refreshing = refreshing,
            error = error,
            message = message,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (profile == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perfil visual", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (loading) "Cargando perfil..." else "No pudimos cargar el perfil visual todavia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (refreshing) "Actualizando..." else "Reintentar")
                    }
                }
            }
        } else {
            VisualProfileCard(profile)
        }
        Spacer(modifier = Modifier.height(16.dp))
        PartnerMessageCard(
            profile = profile,
            partnerMessage = partnerMessage,
            partnerMessageLoaded = partnerMessageLoaded,
            readingPartnerMessage = readingPartnerMessage,
            partnerMessageError = partnerMessageError,
            decisionRequiresPartnerPersonalMessageRead = decisionBlockedByUnreadPartnerMessage,
            busy = busy,
            refreshing = refreshing,
            onReadPartnerMessage = onReadPartnerMessage,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mi mensaje personal", style = MaterialTheme.typography.titleMedium)
                if (myPersonalMessageSubmitted) {
                    Text(
                        text = "Ya guardaste tu mensaje personal. No se puede modificar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = personalMessage,
                        onValueChange = { personalMessage = it.take(280) },
                        label = { Text("Mensaje personal") },
                        enabled = !busy,
                        minLines = 2,
                        supportingText = { Text("${personalMessage.length}/280") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            onSavePersonalMessage(personalMessage)
                        },
                        enabled = !busy && personalMessage.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (writingMessage) "Guardando..." else "Guardar mensaje")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Decision visual", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Si aprobas y la otra persona tambien aprueba, se crea la conexion para la siguiente etapa.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (decisionBlockedByUnreadPartnerMessage) {
                    Text(
                        text = "Lee el mensaje personal de la otra persona antes de decidir.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onApprove,
                        enabled = canMakeVisualDecision,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (deciding) decidingLabel ?: "Procesando..." else "Aprobar")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        enabled = canMakeVisualDecision,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (deciding) decidingLabel ?: "Procesando..." else "Rechazar")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a Home")
        }
    }
}

@Composable
private fun StatusCard(
    match: Match?,
    loading: Boolean,
    refreshing: Boolean,
    error: ApiError?,
    message: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado", style = MaterialTheme.typography.titleLarge)
            Text("Match: ${match?.state?.userLabel() ?: "Cargando"}")
            if (loading || refreshing) {
                Text(
                    text = if (loading) "Cargando revision visual..." else "Actualizando revision visual...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.VisualReview) }
            message?.let { SuccessFeedback(it) }
        }
    }
}

@Composable
private fun PartnerMessageCard(
    profile: VisualProfile?,
    partnerMessage: String?,
    partnerMessageLoaded: Boolean,
    readingPartnerMessage: Boolean,
    partnerMessageError: ApiError?,
    decisionRequiresPartnerPersonalMessageRead: Boolean,
    busy: Boolean,
    refreshing: Boolean,
    onReadPartnerMessage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mensaje personal de la otra persona", style = MaterialTheme.typography.titleMedium)
            val partnerPersonalMessageSubmitted = profile?.partnerPersonalMessageSubmitted == true
            val body = when {
                profile == null -> "Cargando mensaje personal..."
                !partnerPersonalMessageSubmitted -> "La otra persona todavia no dejo un mensaje personal."
                readingPartnerMessage -> "Leyendo mensaje..."
                partnerMessageError != null -> "No pudimos cargar el mensaje personal. Intenta nuevamente."
                partnerMessageLoaded -> partnerMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { TextSafety.safeDisplay(it, maxLength = 280) }
                    ?: "La otra persona todavia no dejo un mensaje personal."
                !partnerMessageLoaded && decisionRequiresPartnerPersonalMessageRead ->
                    "La otra persona dejo un mensaje personal. Tenes que leerlo antes de decidir."
                !partnerMessageLoaded -> "Cargando mensaje personal..."
                else -> "La otra persona todavia no dejo un mensaje personal."
            }
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (partnerPersonalMessageSubmitted && !partnerMessageLoaded) {
                OutlinedButton(
                    onClick = onReadPartnerMessage,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            readingPartnerMessage -> "Leyendo mensaje..."
                            refreshing || partnerMessageError != null -> "Reintentar lectura"
                            else -> "Leer mensaje"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}
