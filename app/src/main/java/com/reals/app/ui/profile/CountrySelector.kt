package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.domain.model.CountryReference

@Composable
fun CountrySelector(
    countries: List<CountryReference>,
    selectedCountryCode: String,
    loading: Boolean,
    enabled: Boolean,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sections = countryMenuSections(countries)
    val selectedLabel = when {
        loading -> "Cargando países..."
        selectedCountryCode.isBlank() -> "Seleccionár país ▼"
        else -> countries.firstOrNull { it.code == selectedCountryCode }?.displayName ?: selectedCountryCode
    }
    val selectorEnabled = enabled && !loading && countries.isNotEmpty()

    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = selectorEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedLabel)
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                sections.preferredCountry?.let { country ->
                    CountryMenuItem(
                        country = country,
                        onCountrySelected = onCountrySelected,
                        onDismiss = { expanded = false },
                    )
                    if (sections.showPreferredSeparator) {
                        HorizontalDivider()
                    }
                }
                sections.remainingCountries.forEach { country ->
                    CountryMenuItem(
                        country = country,
                        onCountrySelected = onCountrySelected,
                        onDismiss = { expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryMenuItem(
    country: CountryReference,
    onCountrySelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(country.displayName) },
        onClick = {
            onCountrySelected(country.code)
            onDismiss()
        },
    )
}

internal data class CountryMenuSections(
    val preferredCountry: CountryReference?,
    val remainingCountries: List<CountryReference>,
) {
    val showPreferredSeparator: Boolean
        get() = preferredCountry != null && remainingCountries.isNotEmpty()
}

internal fun countryMenuSections(
    countries: List<CountryReference>,
    preferredCode: String = "AR",
): CountryMenuSections {
    val preferredCountry = countries.firstOrNull { it.code == preferredCode }
    return CountryMenuSections(
        preferredCountry = preferredCountry,
        remainingCountries = countries.filterNot { it.code == preferredCode },
    )
}
