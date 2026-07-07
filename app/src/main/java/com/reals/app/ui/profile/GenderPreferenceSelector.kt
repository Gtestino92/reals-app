package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

internal val GenderPreferenceOptions = listOf(
    "MALE",
    "FEMALE",
    "NON_BINARY",
    "OTHER",
)

internal val GenderPreferenceStateSaver = Saver<MutableState<Set<String>>, List<String>>(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) },
)

@Composable
internal fun GenderPreferenceSelector(
    selected: Set<String>,
    enabled: Boolean,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Busco",
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: ${genderPreferenceSummary(selected)}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            GenderPreferenceOptions.forEach { option ->
                val checked = option in selected
                DropdownMenuItem(
                    text = { Text(option.genderPreferenceLabel()) },
                    leadingIcon = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                        )
                    },
                    onClick = {
                        val next = if (checked) {
                            if (selected.size == 1) selected else selected - option
                        } else {
                            selected + option
                        }
                        onSelectionChange(next)
                    },
                )
            }
        }
    }
}

private fun genderPreferenceSummary(selected: Set<String>): String {
    val ordered = GenderPreferenceOptions.filter { it in selected }
    return if (ordered.size == GenderPreferenceOptions.size) {
        "Todos"
    } else {
        ordered.joinToString(", ") { it.genderPreferenceLabel() }
    }
}

internal fun isValidGenderPreferenceSet(selected: Set<String>): Boolean =
    selected.isNotEmpty() &&
        selected.size <= GenderPreferenceOptions.size &&
        selected.all { it in GenderPreferenceOptions }

internal fun String.genderIdentityLabel(): String = when (this) {
    "MALE" -> "Hombre"
    "FEMALE" -> "Mujer"
    "NON_BINARY" -> "No binario"
    "OTHER" -> "Otra identidad"
    else -> this
}

internal fun String.genderPreferenceLabel(): String = when (this) {
    "MALE" -> "Hombres"
    "FEMALE" -> "Mujeres"
    "NON_BINARY" -> "Personas no binarias"
    "OTHER" -> "Otras identidades"
    else -> this
}

internal fun String.intentionLabel(): String = when (this) {
    "DATE" -> "Citas"
    "FRIENDSHIP" -> "Amistad"
    "CASUAL" -> "Algo casual"
    else -> this
}
