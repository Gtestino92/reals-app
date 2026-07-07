package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.domain.model.CreateProfileInput

@Composable
fun CreateProfileScreen(
    loading: Boolean,
    error: ApiError?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onSubmit: (CreateProfileInput) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("1995-01-01") }
    var gender by rememberSaveable { mutableStateOf("MALE") }
    var lookingForGenders by rememberSaveable(saver = GenderPreferenceStateSaver) { mutableStateOf(setOf("FEMALE")) }
    var intention by rememberSaveable { mutableStateOf("DATE") }
    var city by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var preferredMinAge by rememberSaveable { mutableStateOf("18") }
    var preferredMaxAge by rememberSaveable { mutableStateOf("45") }
    var maxDistanceKm by rememberSaveable { mutableStateOf("50") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var accountExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val busy = loading || accountDeleteLoading

    LaunchedEffect(accountExpanded) {
        if (!accountExpanded) return@LaunchedEffect
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Crear perfil",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Completa tus datos principales. Despues vas a poder sumar fotos y activar tu perfil.",
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
                    onValueChange = { displayName = it.take(100) },
                    label = { Text("Nombre visible") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Fecha de nacimiento") },
                    supportingText = { Text("Formato YYYY-MM-DD") },
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = "Género",
                    value = gender,
                    options = listOf("MALE", "FEMALE", "NON_BINARY", "OTHER"),
                    enabled = !busy,
                    optionLabel = { it.genderIdentityLabel() },
                    onValueChange = { gender = it },
                )
                GenderPreferenceSelector(
                    selected = lookingForGenders,
                    enabled = !busy,
                    onSelectionChange = { lookingForGenders = it },
                )
                EnumDropdown(
                    label = "Intención",
                    value = intention,
                    options = listOf("DATE", "FRIENDSHIP", "CASUAL"),
                    enabled = !busy,
                    optionLabel = { it.intentionLabel() },
                    onValueChange = { intention = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it.take(100) },
                        label = { Text("Ciudad") },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it.take(100) },
                        label = { Text("Pais") },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(1_000) },
                    label = { Text("Bio opcional") },
                    enabled = !busy,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = preferredMinAge,
                        onValueChange = { preferredMinAge = it },
                        label = "Edad min",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = preferredMaxAge,
                        onValueChange = { preferredMaxAge = it },
                        label = "Edad max",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
                NumberField(
                    value = maxDistanceKm,
                    onValueChange = { maxDistanceKm = it },
                    label = "Distancia maxima km",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )

                localError?.let {
                    FeedbackCard(
                        title = "Revisa los datos",
                        message = it,
                        tone = FeedbackTone.Error,
                    )
                }
                if (localError == null) {
                    error?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileCreation) }
                }

                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val input = validateProfileInput(
                            displayName = displayName,
                            birthDate = birthDate,
                            gender = gender,
                            lookingForGenders = lookingForGenders,
                            intention = intention,
                            city = city,
                            country = country,
                            bio = bio,
                            preferredMinAge = preferredMinAge,
                            preferredMaxAge = preferredMaxAge,
                            maxDistanceKm = maxDistanceKm,
                        )
                        if (input == null) {
                            localError = "Revisa los campos. No uses etiquetas o formato HTML; nombre, fecha, ciudad, pais, busqueda, edades y distancia son requeridos."
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

        DeleteAccountSection(
            busy = busy,
            loading = accountDeleteLoading,
            error = accountDeleteError,
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = it },
            onSignOut = onSignOut,
            onDeleteAccount = onDeleteAccount,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    optionLabel: (String) -> String = { it },
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: ${optionLabel(value)}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
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
    lookingForGenders: Set<String>,
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

    val cleanDisplayName = TextSafety.normalizeSingleLine(displayName, maxLength = 100)
    val cleanBirthDate = birthDate.trim()
    val cleanCity = TextSafety.normalizeSingleLine(city, maxLength = 100)
    val cleanCountry = TextSafety.normalizeSingleLine(country, maxLength = 100)
    val cleanBio = TextSafety.normalizeMultiline(bio, maxLength = 1_000)

    if (cleanDisplayName.length < 2) return null
    if (!birthDatePattern.matches(cleanBirthDate)) return null
    if (cleanCity.isBlank() || cleanCountry.isBlank()) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanDisplayName)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanCity)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanCountry)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanBio)) return null
    if (minAge == null || maxAge == null || distance == null) return null
    if (minAge !in 18..99 || maxAge !in 18..99 || minAge > maxAge) return null
    if (distance !in 1..1000) return null
    if (!isValidGenderPreferenceSet(lookingForGenders)) return null

    return CreateProfileInput(
        displayName = cleanDisplayName,
        birthDate = cleanBirthDate,
        gender = gender,
        lookingForGenders = lookingForGenders,
        intention = intention,
        city = cleanCity,
        country = cleanCountry,
        bio = cleanBio.ifBlank { null },
        preferredMinAge = minAge,
        preferredMaxAge = maxAge,
        maxDistanceKm = distance,
    )
}
