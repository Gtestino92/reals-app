@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.reals.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.theme.RealsRadii

private val IntentionOptions = listOf(
    "DATE",
    "FRIENDSHIP",
    "CASUAL",
)

@Composable
internal fun MatchPreferencesCard(
    profile: Profile,
    loading: Boolean,
    busy: Boolean,
    error: ApiError?,
    message: String?,
    expanded: Boolean,
    collapsible: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
) {
    val bringIntoViewRequester = rememberExpandedSectionRequester(expanded)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "Preferencias",
                expanded = expanded,
                collapsible = collapsible,
                closeEnabled = !loading && !busy,
                onClose = onToggleExpanded,
            )
            if (expanded) {
                MatchPreferencesEditor(
                    profile = profile,
                    loading = loading,
                    error = error,
                    message = message,
                    onUpdateMatchFilters = onUpdateMatchFilters,
                )
            } else {
                Text(profile.intention.intentionLabel(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = genderPreferenceSummary(profile.lookingForGenders),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${profile.preferredMinAge}–${profile.preferredMaxAge} años · hasta ${profile.maxDistanceKm} km",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onToggleExpanded,
                    enabled = !loading && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Editar preferencias")
                }
            }
        }
    }
}

@Composable
private fun MatchPreferencesEditor(
    profile: Profile,
    loading: Boolean,
    error: ApiError?,
    message: String?,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
) {
    val profileGenderPreferenceKey = profile.lookingForGenders.sorted().joinToString("|")
    var intention by rememberSaveable(profile.id, profile.intention) {
        mutableStateOf(profile.intention)
    }
    var lookingForGenders by rememberSaveable(
        profile.id,
        profileGenderPreferenceKey,
        saver = GenderPreferenceStateSaver,
    ) {
        mutableStateOf(profile.lookingForGenders)
    }
    var minAge by rememberSaveable(profile.id, profile.preferredMinAge) {
        mutableStateOf(profile.preferredMinAge)
    }
    var maxAge by rememberSaveable(profile.id, profile.preferredMaxAge) {
        mutableStateOf(profile.preferredMaxAge)
    }
    var distance by rememberSaveable(profile.id, profile.maxDistanceKm) {
        mutableStateOf(profile.maxDistanceKm.coerceIn(ProfileMinDistanceKm, ProfileMaxDistanceKm))
    }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf(emptySet<MatchFiltersField>()) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Definí qué personas querés que Reals tenga en cuenta al buscar un chat.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        PreferenceGroup(title = "Qué buscás") {
            IntentionChips(
                selected = intention,
                enabled = !loading,
                onSelectionChange = { intention = it },
            )
        }
        PreferenceGroup(title = "A quién querés conocer") {
            GenderPreferenceChips(
                selected = lookingForGenders,
                enabled = !loading,
                onSelectionChange = { lookingForGenders = it },
            )
        }
        PreferenceGroup(title = "Edad") {
            AgeRangePreferenceControl(
                minAge = minAge,
                maxAge = maxAge,
                enabled = !loading,
                onAgeRangeChange = { nextMin, nextMax ->
                    minAge = nextMin
                    maxAge = nextMax
                },
                error = if (MatchFiltersField.AgeRange in fieldErrors) {
                    "Elegí edades entre 18 y 99, con mínima menor o igual a máxima."
                } else {
                    null
                },
            )
        }
        PreferenceGroup(title = "Distancia máxima") {
            DistancePreferenceControl(
                distanceKm = distance,
                enabled = !loading,
                onDistanceChange = { distance = it },
                modifier = Modifier.fillMaxWidth(),
                error = if (MatchFiltersField.Distance in fieldErrors) {
                    "Elegí una distancia entre 1 y 100 km."
                } else {
                    null
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            localError?.let { ErrorFeedback("Revisá las preferencias", it) }
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.MatchFilters) }
            Button(
                onClick = {
                    val validation = validateMatchFiltersInputDetailed(
                        intention = intention,
                        lookingForGenders = lookingForGenders,
                        minAge = minAge,
                        maxAge = maxAge,
                        distance = distance,
                    )
                    if (validation.input == null) {
                        fieldErrors = validation.errorFields
                        localError = validation.errorMessage
                    } else {
                        fieldErrors = emptySet()
                        localError = null
                        onUpdateMatchFilters(validation.input)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Guardando preferencias..." else "Guardar preferencias")
            }
        }
    }
}

@Composable
private fun PreferenceGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun IntentionChips(
    selected: String,
    enabled: Boolean,
    onSelectionChange: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IntentionOptions.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelectionChange(option) },
                enabled = enabled,
                label = { Text(option.intentionLabel()) },
            )
        }
    }
}

@Composable
private fun GenderPreferenceChips(
    selected: Set<String>,
    enabled: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GenderPreferenceOptions.forEach { option ->
            val checked = option in selected
            FilterChip(
                selected = checked,
                onClick = {
                    val next = if (checked) {
                        if (selected.size == 1) selected else selected - option
                    } else {
                        selected + option
                    }
                    onSelectionChange(next)
                },
                enabled = enabled,
                label = { Text(option.genderPreferenceLabel()) },
            )
        }
    }
}

internal data class MatchFiltersValidationResult(
    val input: UpdateMatchFiltersInput?,
    val errorFields: Set<MatchFiltersField> = emptySet(),
    val errorMessage: String? = null,
)

internal enum class MatchFiltersField {
    Intention,
    GenderPreference,
    AgeRange,
    Distance,
}

internal fun validateMatchFiltersInputDetailed(
    intention: String,
    lookingForGenders: Set<String>,
    minAge: Int,
    maxAge: Int,
    distance: Int,
): MatchFiltersValidationResult {
    if (intention !in listOf("DATE", "FRIENDSHIP", "CASUAL")) {
        return MatchFiltersValidationResult(
            input = null,
            errorFields = setOf(MatchFiltersField.Intention),
            errorMessage = "Elegí qué estás buscando.",
        )
    }
    if (!isValidGenderPreferenceSet(lookingForGenders)) {
        return MatchFiltersValidationResult(
            input = null,
            errorFields = setOf(MatchFiltersField.GenderPreference),
            errorMessage = "Elegí al menos una preferencia de género.",
        )
    }
    if (minAge !in ProfileMinAge..ProfileMaxAge || maxAge !in ProfileMinAge..ProfileMaxAge) {
        return MatchFiltersValidationResult(
            input = null,
            errorFields = setOf(MatchFiltersField.AgeRange),
            errorMessage = "Las edades deben estar entre 18 y 99 años.",
        )
    }
    if (minAge > maxAge) {
        return MatchFiltersValidationResult(
            input = null,
            errorFields = setOf(MatchFiltersField.AgeRange),
            errorMessage = "La edad mínima no puede ser mayor que la máxima.",
        )
    }
    if (distance !in ProfileMinDistanceKm..ProfileMaxDistanceKm) {
        return MatchFiltersValidationResult(
            input = null,
            errorFields = setOf(MatchFiltersField.Distance),
            errorMessage = "La distancia debe estar entre 1 y 100 km.",
        )
    }
    return MatchFiltersValidationResult(
        input = UpdateMatchFiltersInput(
            intention = intention,
            lookingForGenders = lookingForGenders,
            preferredMinAge = minAge,
            preferredMaxAge = maxAge,
            maxDistanceKm = distance,
        ),
    )
}
