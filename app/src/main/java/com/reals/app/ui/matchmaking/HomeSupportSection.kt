package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val CafecitoSupportUrl = "https://cafecito.app/reals-app"
internal const val SupportRealsTitle = "Apoyar Reals"
internal const val SupportRealsBody =
    "Si te gusta Reals y querés ayudar a sostener el proyecto, podés hacer un aporte voluntario. " +
        "No cambia tu experiencia ni te da beneficios dentro de la app."
internal const val SupportRealsCta = "Apoyar en Cafecito"

internal fun shouldShowSupportReals(showCafecitoSupport: Boolean): Boolean = showCafecitoSupport

@Composable
internal fun SupportRealsSection(
    enabled: Boolean,
    onSupportReals: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(SupportRealsTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                text = SupportRealsBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}
