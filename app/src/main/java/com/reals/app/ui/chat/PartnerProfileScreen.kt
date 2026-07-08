package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.common.ApiErrorFeedbackCard

@Composable
fun PartnerProfileScreen(
    profile: VisualProfile?,
    loading: Boolean,
    refreshing: Boolean,
    error: ApiError?,
    onRefresh: () -> Unit,
    onBackHome: () -> Unit,
) {
    val busy = loading || refreshing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Perfil de la otra persona",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Ya hubo aprobacion visual mutua. Pod\u00e9s volver a ver el perfil para recordar con quien continua la experiencia.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.VisualReview)
            if (profile != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (refreshing) "Actualizando..." else "Reintentar")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (profile == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perfil visual", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (loading) {
                            "Cargando perfil..."
                        } else {
                            "No pudimos cargar el perfil visual todavia."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (refreshing) "Actualizando..." else "Reintentar")
                    }
                }
            }
        } else {
            VisualProfileCard(profile)
            if (refreshing) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Actualizando perfil...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a Home")
        }
    }
}
