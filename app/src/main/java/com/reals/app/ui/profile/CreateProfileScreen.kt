package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.toDisplayMessage
import com.reals.app.domain.model.CreateProfileInput

@Composable
fun CreateProfileScreen(
    loading: Boolean,
    error: ApiError?,
    onSubmit: (CreateProfileInput) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("1995-01-01") }
    var gender by rememberSaveable { mutableStateOf("MALE") }
    var lookingForGender by rememberSaveable { mutableStateOf("WOMEN") }
    var intention by rememberSaveable { mutableStateOf("DATE") }
    var city by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var preferredMinAge by rememberSaveable { mutableStateOf("18") }
    var preferredMaxAge by rememberSaveable { mutableStateOf("45") }
    var maxDistanceKm by rememberSaveable { mutableStateOf("50") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Crear perfil",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Estos datos crean tu perfil en estado DRAFT. La activacion, fotos y matchmaking quedan para los siguientes pasos.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nombre visible") },
                    enabled = !loading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Fecha de nacimiento") },
                    supportingText = { Text("Formato YYYY-MM-DD") },
                    enabled = !loading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = "Genero",
                    value = gender,
                    options = listOf("MALE", "FEMALE", "NON_BINARY", "OTHER"),
                    enabled = !loading,
                    onValueChange = { gender = it },
                )
                EnumDropdown(
                    label = "Busco",
                    value = lookingForGender,
                    options = listOf("MEN", "WOMEN", "EVERYONE", "OTHER"),
                    enabled = !loading,
                    onValueChange = { lookingForGender = it },
                )
                EnumDropdown(
                    label = "Intencion",
                    value = intention,
                    options = listOf("DATE", "FRIENDSHIP", "CASUAL"),
                    enabled = !loading,
                    onValueChange = { intention = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Ciudad") },
                        enabled = !loading,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Pais") },
                        enabled = !loading,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio opcional") },
                    enabled = !loading,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = preferredMinAge,
                        onValueChange = { preferredMinAge = it },
                        label = "Edad min",
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = preferredMaxAge,
                        onValueChange = { preferredMaxAge = it },
                        label = "Edad max",
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    )
                }
                NumberField(
                    value = maxDistanceKm,
                    onValueChange = { maxDistanceKm = it },
                    label = "Distancia maxima km",
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )

                val visibleError = localError ?: error?.toDisplayMessage()
                if (visibleError != null) {
                    Text(
                        text = visibleError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Button(
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val input = validateProfileInput(
                            displayName = displayName,
                            birthDate = birthDate,
                            gender = gender,
                            lookingForGender = lookingForGender,
                            intention = intention,
                            city = city,
                            country = country,
                            bio = bio,
                            preferredMinAge = preferredMinAge,
                            preferredMaxAge = preferredMaxAge,
                            maxDistanceKm = maxDistanceKm,
                        )
                        if (input == null) {
                            localError = "Revisa los campos: nombre, fecha, ciudad, pais, edades y distancia son requeridos."
                        } else {
                            localError = null
                            onSubmit(input)
                        }
                    },
                ) {
                    Text(if (loading) "Creando perfil..." else "Crear perfil")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading) {
                Text("Refrescar")
            }
            OutlinedButton(onClick = onSignOut, enabled = !loading) {
                Text("Cerrar sesion")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $value")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() }) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun validateProfileInput(
    displayName: String,
    birthDate: String,
    gender: String,
    lookingForGender: String,
    intention: String,
    city: String,
    country: String,
    bio: String,
    preferredMinAge: String,
    preferredMaxAge: String,
    maxDistanceKm: String,
): CreateProfileInput? {
    val minAge = preferredMinAge.toIntOrNull()
    val maxAge = preferredMaxAge.toIntOrNull()
    val distance = maxDistanceKm.toIntOrNull()
    val birthDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    if (displayName.trim().length < 2) return null
    if (!birthDatePattern.matches(birthDate.trim())) return null
    if (city.trim().isBlank() || country.trim().isBlank()) return null
    if (minAge == null || maxAge == null || distance == null) return null
    if (minAge !in 18..99 || maxAge !in 18..99 || minAge > maxAge) return null
    if (distance !in 1..1000) return null

    return CreateProfileInput(
        displayName = displayName.trim(),
        birthDate = birthDate.trim(),
        gender = gender,
        lookingForGender = lookingForGender,
        intention = intention,
        city = city.trim(),
        country = country.trim(),
        bio = bio.trim().ifBlank { null },
        preferredMinAge = minAge,
        preferredMaxAge = maxAge,
        maxDistanceKm = distance,
    )
}
