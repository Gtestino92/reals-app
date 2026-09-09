package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone

@Composable
fun CreateProfileScreen(
    loading: Boolean,
    error: ApiError?,
    countriesLoading: Boolean,
    countries: List<CountryReference>,
    countriesError: ApiError?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onSubmit: (CreateProfileInput) -> Unit,
    onLoadCountries: () -> Unit,
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
    var selectedCountryCode by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var preferredMinAge by rememberSaveable { mutableStateOf(ProfileMinAge) }
    var preferredMaxAge by rememberSaveable { mutableStateOf(45) }
    var maxDistanceKm by rememberSaveable { mutableStateOf(50) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf(emptySet<CreateProfileField>()) }
    var accountExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val busy = loading || accountDeleteLoading

    LaunchedEffect(Unit) {
        onLoadCountries()
    }

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
                    isError = CreateProfileField.DisplayName in fieldErrors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Fecha de nacimiento") },
                    supportingText = { Text("Formato YYYY-MM-DD") },
                    enabled = !busy,
                    singleLine = true,
                    isError = CreateProfileField.BirthDate in fieldErrors,
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
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it.take(100) },
                    label = { Text("Ciudad") },
                    enabled = !busy,
                    singleLine = true,
                    isError = CreateProfileField.City in fieldErrors,
                    modifier = Modifier.fillMaxWidth(),
                )
                CountrySelector(
                    countries = countries,
                    selectedCountryCode = selectedCountryCode,
                    loading = countriesLoading,
                    enabled = !busy,
                    onCountrySelected = { selectedCountryCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    error = if (CreateProfileField.Country in fieldErrors) {
                        "Seleccioná un país."
                    } else {
                        null
                    },
                )
                countriesError?.let {
                    ApiErrorFeedbackCard(it, ErrorContext.ProfileCreation)
                    TextButton(
                        onClick = onLoadCountries,
                        enabled = !busy && !countriesLoading,
                    ) {
                        Text("Reintentar carga de países")
                    }
                }
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(1_000) },
                    label = { Text("Bio opcional") },
                    enabled = !busy,
                    isError = CreateProfileField.Bio in fieldErrors,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                AgeRangePreferenceControl(
                    minAge = preferredMinAge,
                    maxAge = preferredMaxAge,
                    enabled = !busy,
                    onAgeRangeChange = { minAge, maxAge ->
                        preferredMinAge = minAge
                        preferredMaxAge = maxAge
                    },
                    modifier = Modifier.fillMaxWidth(),
                    error = if (CreateProfileField.AgeRange in fieldErrors) {
                        "Elegí edades entre 18 y 99, con mínima menor o igual a máxima."
                    } else {
                        null
                    },
                )
                DistancePreferenceControl(
                    distanceKm = maxDistanceKm,
                    enabled = !busy,
                    onDistanceChange = { maxDistanceKm = it },
                    modifier = Modifier.fillMaxWidth(),
                    error = if (CreateProfileField.Distance in fieldErrors) {
                        "Elegí una distancia entre 1 y 100 km."
                    } else {
                        null
                    },
                )

                localError?.let {
                    FeedbackCard(
                        title = "Revisá los datos",
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
                        val validation = validateProfileInputDetailed(
                            displayName = displayName,
                            birthDate = birthDate,
                            gender = gender,
                            lookingForGenders = lookingForGenders,
                            intention = intention,
                            city = city,
                            countryCode = selectedCountryCode,
                            bio = bio,
                            preferredMinAge = preferredMinAge,
                            preferredMaxAge = preferredMaxAge,
                            maxDistanceKm = maxDistanceKm,
                        )
                        if (validation.input == null) {
                            fieldErrors = validation.errorFields
                            localError = validation.errorMessage
                        } else {
                            fieldErrors = emptySet()
                            localError = null
                            onSubmit(validation.input)
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

internal fun validateProfileInput(
    displayName: String,
    birthDate: String,
    gender: String,
    lookingForGenders: Set<String>,
    intention: String,
    city: String,
    countryCode: String,
    bio: String,
    preferredMinAge: String,
    preferredMaxAge: String,
    maxDistanceKm: String,
): CreateProfileInput? {
    val minAge = preferredMinAge.toIntOrNull()
    val maxAge = preferredMaxAge.toIntOrNull()
    val distance = maxDistanceKm.toIntOrNull()
    if (minAge == null || maxAge == null || distance == null) return null
    return validateProfileInputDetailed(
        displayName = displayName,
        birthDate = birthDate,
        gender = gender,
        lookingForGenders = lookingForGenders,
        intention = intention,
        city = city,
        countryCode = countryCode,
        bio = bio,
        preferredMinAge = minAge,
        preferredMaxAge = maxAge,
        maxDistanceKm = distance,
    ).input
}

internal data class CreateProfileValidationResult(
    val input: CreateProfileInput?,
    val errorFields: Set<CreateProfileField> = emptySet(),
    val errorMessage: String? = null,
)

internal enum class CreateProfileField {
    DisplayName,
    BirthDate,
    City,
    Country,
    Bio,
    GenderPreference,
    AgeRange,
    Distance,
}

internal fun validateProfileInputDetailed(
    displayName: String,
    birthDate: String,
    gender: String,
    lookingForGenders: Set<String>,
    intention: String,
    city: String,
    countryCode: String,
    bio: String,
    preferredMinAge: Int,
    preferredMaxAge: Int,
    maxDistanceKm: Int,
): CreateProfileValidationResult {
    val birthDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    val cleanDisplayName = TextSafety.normalizeSingleLine(displayName, maxLength = 100)
    val cleanBirthDate = birthDate.trim()
    val cleanCity = TextSafety.normalizeSingleLine(city, maxLength = 100)
    val cleanCountryCode = countryCode.trim()
    val cleanBio = TextSafety.normalizeMultiline(bio, maxLength = 1_000)

    if (cleanDisplayName.length < 2) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.DisplayName),
            errorMessage = "El nombre visible debe tener al menos 2 caracteres.",
        )
    }
    if (!birthDatePattern.matches(cleanBirthDate)) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.BirthDate),
            errorMessage = "La fecha de nacimiento debe usar formato YYYY-MM-DD.",
        )
    }
    if (cleanCity.isBlank()) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.City),
            errorMessage = "La ciudad es requerida.",
        )
    }
    if (cleanCountryCode.isBlank()) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.Country),
            errorMessage = "Seleccioná un país.",
        )
    }
    if (TextSafety.containsHtmlLikeMarkup(cleanDisplayName)) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.DisplayName),
            errorMessage = "No uses etiquetas o formato HTML en el nombre visible.",
        )
    }
    if (TextSafety.containsHtmlLikeMarkup(cleanCity)) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.City),
            errorMessage = "No uses etiquetas o formato HTML en la ciudad.",
        )
    }
    if (TextSafety.containsHtmlLikeMarkup(cleanBio)) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.Bio),
            errorMessage = "No uses etiquetas o formato HTML en la bio.",
        )
    }
    if (preferredMinAge !in ProfileMinAge..ProfileMaxAge || preferredMaxAge !in ProfileMinAge..ProfileMaxAge) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.AgeRange),
            errorMessage = "Las edades deben estar entre 18 y 99 años.",
        )
    }
    if (preferredMinAge > preferredMaxAge) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.AgeRange),
            errorMessage = "La edad mínima no puede ser mayor que la máxima.",
        )
    }
    if (maxDistanceKm !in ProfileMinDistanceKm..ProfileMaxDistanceKm) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.Distance),
            errorMessage = "La distancia debe estar entre 1 y 100 km.",
        )
    }
    if (!isValidGenderPreferenceSet(lookingForGenders)) {
        return CreateProfileValidationResult(
            input = null,
            errorFields = setOf(CreateProfileField.GenderPreference),
            errorMessage = "Elegí al menos una preferencia de género.",
        )
    }

    return CreateProfileValidationResult(
        input = CreateProfileInput(
            displayName = cleanDisplayName,
            birthDate = cleanBirthDate,
            gender = gender,
            lookingForGenders = lookingForGenders,
            intention = intention,
            city = cleanCity,
            countryCode = cleanCountryCode,
            bio = cleanBio.ifBlank { null },
            preferredMinAge = preferredMinAge,
            preferredMaxAge = preferredMaxAge,
            maxDistanceKm = maxDistanceKm,
        ),
    )
}
