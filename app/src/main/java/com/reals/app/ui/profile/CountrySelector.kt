package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reals.app.domain.model.CountryReference
import java.text.Normalizer
import java.util.Locale

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
    var dialogOpen by remember { mutableStateOf(false) }
    val countriesByCode = remember(countries) { countries.associateBy { it.code } }
    val sections = remember(countries) { countryMenuSections(countries) }
    val searchEntries = remember(countries) { buildCountrySearchEntries(countries) }
    val selectedLabel = selectedCountryLabel(
        loading = loading,
        selectedCountryCode = selectedCountryCode,
        countriesByCode = countriesByCode,
    )
    val selectorEnabled = enabled && !loading && countries.isNotEmpty()

    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { dialogOpen = true },
            enabled = selectorEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CountrySelectorMinTouchHeight)
                .testTag(CountrySelectorButtonTag),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                Text("▼")
            }
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (dialogOpen) {
        CountrySelectorDialog(
            sections = sections,
            searchEntries = searchEntries,
            selectedCountryCode = selectedCountryCode,
            onCountrySelected = { code ->
                onCountrySelected(code)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
internal fun CountrySelectorDialog(
    sections: CountryMenuSections,
    searchEntries: List<CountrySearchEntry>,
    selectedCountryCode: String,
    onCountrySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery by remember(query) {
        derivedStateOf { normalizeCountrySearchText(query) }
    }
    val displayedCountries by remember(normalizedQuery, sections, searchEntries) {
        derivedStateOf {
            if (normalizedQuery.isBlank()) {
                sections.canonicalCountries
            } else {
                filterCountrySearchEntries(searchEntries, normalizedQuery)
            }
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(normalizedQuery) {
        if (normalizedQuery.isNotBlank()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(selectedCountryCode, sections, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            val selectedIndex = sections.canonicalCountries.indexOfFirst { it.code == selectedCountryCode }
            if (selectedIndex > 0) {
                listState.scrollToItem(selectedIndex + if (sections.showPreferredSeparator) 1 else 0)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .padding(CountrySelectorDialogOuterPadding)
                .testTag(CountrySelectorDialogTag),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = CountrySelectorDialogMaxWidth)
                    .fillMaxHeight(CountrySelectorDialogMaxHeightFraction),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Seleccionar país",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CountrySelectorSearchTag),
                        label = { Text("Buscar país") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                    CountrySelectorList(
                        countries = displayedCountries,
                        selectedCountryCode = selectedCountryCode,
                        showPreferredSeparator = normalizedQuery.isBlank() && sections.showPreferredSeparator,
                        listState = listState,
                        onCountrySelected = { code ->
                            query = ""
                            onCountrySelected(code)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(CountrySelectorListTag),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                query = ""
                                onDismiss()
                            },
                            modifier = Modifier
                                .heightIn(min = CountrySelectorMinTouchHeight)
                                .testTag(CountrySelectorCancelTag),
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountrySelectorList(
    countries: List<CountryReference>,
    selectedCountryCode: String,
    showPreferredSeparator: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        if (countries.isEmpty()) {
            item(key = CountrySelectorEmptyTag) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CountrySelectorEmptyMinHeight)
                        .testTag(CountrySelectorEmptyTag)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No encontramos países con esa búsqueda.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(
            items = countries,
            key = { country -> countrySelectorRowKey(country.code) },
        ) { country ->
            CountrySelectorRow(
                country = country,
                selected = country.code == selectedCountryCode,
                onCountrySelected = onCountrySelected,
            )
            if (showPreferredSeparator && country.code == PreferredCountryCode) {
                HorizontalDivider(modifier = Modifier.testTag(CountrySelectorPreferredSeparatorTag))
            }
        }
    }
}

@Composable
private fun CountrySelectorRow(
    country: CountryReference,
    selected: Boolean,
    onCountrySelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CountrySelectorMinTouchHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onCountrySelected(country.code) },
            )
            .semantics {
                contentDescription = "País ${country.displayName}"
            }
            .testTag(countrySelectorRowTag(country.code))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (selected) {
            Text(
                text = "Seleccionado",
                modifier = Modifier.testTag(countrySelectorSelectedTag(country.code)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal data class CountryMenuSections(
    val preferredCountry: CountryReference?,
    val remainingCountries: List<CountryReference>,
) {
    val showPreferredSeparator: Boolean
        get() = preferredCountry != null && remainingCountries.isNotEmpty()

    val canonicalCountries: List<CountryReference>
        get() = if (preferredCountry == null) remainingCountries else listOf(preferredCountry) + remainingCountries
}

internal fun countryMenuSections(
    countries: List<CountryReference>,
    preferredCode: String = PreferredCountryCode,
): CountryMenuSections {
    val preferredCountry = countries.firstOrNull { it.code == preferredCode }
    return CountryMenuSections(
        preferredCountry = preferredCountry,
        remainingCountries = countries.filterNot { it.code == preferredCode },
    )
}

internal data class CountrySearchEntry(
    val country: CountryReference,
    val normalizedDisplayName: String,
    val normalizedCode: String,
)

internal fun buildCountrySearchEntries(countries: List<CountryReference>): List<CountrySearchEntry> =
    countryMenuSections(countries).canonicalCountries.map { country ->
        CountrySearchEntry(
            country = country,
            normalizedDisplayName = normalizeCountrySearchText(country.displayName),
            normalizedCode = normalizeCountrySearchText(country.code),
        )
    }

internal fun filterCountrySearchEntries(
    entries: List<CountrySearchEntry>,
    normalizedQuery: String,
): List<CountryReference> {
    if (normalizedQuery.isBlank()) return entries.map { it.country }
    return entries
        .asSequence()
        .filter { entry ->
            normalizedQuery in entry.normalizedDisplayName || normalizedQuery in entry.normalizedCode
        }
        .map { it.country }
        .toList()
}

internal fun normalizeCountrySearchText(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
    return CombiningMarksRegex.replace(decomposed, "").lowercase(Locale.ROOT)
}

internal fun selectedCountryLabel(
    loading: Boolean,
    selectedCountryCode: String,
    countriesByCode: Map<String, CountryReference>,
): String = when {
    loading -> "Cargando países..."
    selectedCountryCode.isBlank() -> "Seleccionar país"
    else -> countriesByCode[selectedCountryCode]?.displayName ?: selectedCountryCode
}

internal const val PreferredCountryCode = "AR"
internal const val CountrySelectorButtonTag = "country_selector_button"
internal const val CountrySelectorDialogTag = "country_selector_dialog"
internal const val CountrySelectorSearchTag = "country_selector_search"
internal const val CountrySelectorListTag = "country_selector_list"
internal const val CountrySelectorEmptyTag = "country_selector_empty"
internal const val CountrySelectorCancelTag = "country_selector_cancel"
internal const val CountrySelectorPreferredSeparatorTag = "country_selector_preferred_separator"

internal fun countrySelectorRowTag(code: String): String = "country_selector_row_$code"
internal fun countrySelectorSelectedTag(code: String): String = "country_selector_selected_$code"
internal fun countrySelectorRowKey(code: String): String = "country_$code"

private val CombiningMarksRegex = "\\p{Mn}+".toRegex()
private val CountrySelectorMinTouchHeight = 48.dp
private val CountrySelectorDialogOuterPadding = 24.dp
private val CountrySelectorDialogMaxWidth = 560.dp
private val CountrySelectorDialogMaxHeightFraction = 0.85f
private val CountrySelectorEmptyMinHeight = 96.dp
