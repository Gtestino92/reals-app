package com.reals.app.ui.chat

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.ManualBlockConfirmationDialog
import com.reals.app.ui.common.ManualBlockOverflowMenu

@Composable
fun PartnerProfileScreen(
    profile: VisualProfile?,
    loading: Boolean,
    refreshing: Boolean,
    manualBlockLoading: Boolean,
    manualBlockError: ApiError?,
    error: ApiError?,
    onRefresh: () -> Unit,
    onManualBlock: () -> Unit,
    onClearManualBlockError: () -> Unit,
    onBackHome: () -> Unit,
) {
    var showingManualBlockDialog by rememberSaveable { mutableStateOf(false) }
    val busy = loading || refreshing || manualBlockLoading
    val headerTitle = profile
        ?.let { "${TextSafety.safeDisplay(it.displayName, maxLength = 100)}, ${it.age}" }
        ?: "Perfil"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ManualBlockOverflowMenu(
                enabled = !busy,
                onRequestBlock = {
                    onClearManualBlockError()
                    showingManualBlockDialog = true
                },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.VisualReview)
            if (profile != null && !loading) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (refreshing) "Actualizándo..." else "Reintentar")
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
                    Text(
                        text = if (loading) {
                            "Cargando perfil..."
                        } else {
                            "No pudimos cargar el perfil visual todavía."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!loading) {
                        OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (refreshing) "Actualizándo..." else "Reintentar")
                        }
                    }
                }
            }
        } else {
            VisualProfileCard(profile, showHeader = false)
            if (refreshing) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Actualizándo perfil...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (profile != null || !loading) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBackHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Volver a Home")
            }
        }
    }

    if (showingManualBlockDialog) {
        ManualBlockConfirmationDialog(
            loading = manualBlockLoading,
            error = manualBlockError,
            onConfirm = onManualBlock,
            onDismiss = {
                if (!manualBlockLoading) {
                    onClearManualBlockError()
                    showingManualBlockDialog = false
                }
            },
        )
    }
}
