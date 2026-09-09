package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ManualLocationFallback(
    latitude: String,
    longitude: String,
    accuracy: String,
    enabled: Boolean,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onAccuracyChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        text = "Solo para desarrollo/emulador cuando no hay ubicación disponible.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberTextField(latitude, onLatitudeChange, "Latitud", enabled, Modifier.weight(1f))
        NumberTextField(longitude, onLongitudeChange, "Longitud", enabled, Modifier.weight(1f))
    }
    NumberTextField(accuracy, onAccuracyChange, "Precision metros", enabled, Modifier.fillMaxWidth())
    OutlinedButton(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("Buscar con fallback manual")
    }
}

@Composable
private fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
