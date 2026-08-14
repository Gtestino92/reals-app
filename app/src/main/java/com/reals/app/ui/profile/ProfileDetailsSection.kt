@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.reals.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.reals.app.core.media.ProfilePhotoPipelineTiming
import com.reals.app.core.media.ProfilePhotoTimingFields
import com.reals.app.core.media.deleteOwnedProfilePhotoCropFile
import com.reals.app.core.media.deleteStaleProfilePhotoCropFiles
import com.reals.app.core.media.profilePhotoCropCacheDirectory
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.realsOutlinedTextFieldColors
import com.reals.app.ui.common.userDescription
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.UpdateProfileInput

@Composable
internal fun ProfileDetailsCard(
    profile: Profile,
    countries: List<CountryReference>,
    countriesLoading: Boolean,
    countriesError: ApiError?,
    loading: Boolean,
    busy: Boolean,
    error: ApiError?,
    message: String?,
    expanded: Boolean,
    collapsible: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onLoadCountries: () -> Unit,
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
                title = "Tu perfil",
                expanded = expanded,
                collapsible = collapsible,
                closeEnabled = !loading && !busy,
                onClose = onToggleExpanded,
            )
            if (expanded) {
                ProfileEditActions(
                    profile = profile,
                    countries = countries,
                    countriesLoading = countriesLoading,
                    countriesError = countriesError,
                    loading = loading,
                    error = error,
                    message = message,
                    onUpdateProfile = onUpdateProfile,
                    onLoadCountries = onLoadCountries,
                )
            } else {
                Text(
                    text = TextSafety.safeDisplay(profile.displayName, maxLength = 100),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${profile.age} años · ${
                        TextSafety.safeDisplay(profile.city, maxLength = 100)
                    }, ${TextSafety.safeDisplay(profileCountryDisplayName(profile, countries), maxLength = 100)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                profile.bio?.takeIf { it.isNotBlank() }?.let {
                    Text(TextSafety.safeDisplay(it, maxLength = 1_000))
                }
                Text(
                    text = profileNextStep(profile.status),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onToggleExpanded,
                    enabled = !loading && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Editar perfil")
                }
            }
        }
    }
}

@Composable
private fun ProfileEditActions(
    profile: Profile,
    countries: List<CountryReference>,
    countriesLoading: Boolean,
    countriesError: ApiError?,
    loading: Boolean,
    error: ApiError?,
    message: String?,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onLoadCountries: () -> Unit,
) {
    var displayName by rememberSaveable(profile.id, profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable(profile.id, profile.bio) { mutableStateOf(profile.bio.orEmpty()) }
    var city by rememberSaveable(profile.id, profile.city) { mutableStateOf(profile.city) }
    var selectedCountryCode by rememberSaveable(profile.id, profile.countryCode) { mutableStateOf(profile.countryCode) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Campos editables: nombre, bio, ciudad y país.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(displayName, { displayName = it.take(100) }, label = { Text("Nombre visible") }, enabled = !loading, singleLine = true, shape = RoundedCornerShape(RealsRadii.Button), colors = realsOutlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(1_000) }, label = { Text("Bio") }, enabled = !loading, minLines = 3, shape = RoundedCornerShape(RealsRadii.Button), colors = realsOutlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it.take(100) }, label = { Text("Ciudad") }, enabled = !loading, singleLine = true, shape = RoundedCornerShape(RealsRadii.Button), colors = realsOutlinedTextFieldColors(), modifier = Modifier.fillMaxWidth())
            CountrySelector(
                countries = countries,
                selectedCountryCode = selectedCountryCode,
                loading = countriesLoading,
                enabled = !loading,
                onCountrySelected = { selectedCountryCode = it },
                modifier = Modifier.fillMaxWidth(),
            )
            countriesError?.let {
                ApiErrorFeedbackCard(it, ErrorContext.ProfileUpdate)
                TextButton(
                    onClick = onLoadCountries,
                    enabled = !loading && !countriesLoading,
                ) {
                    Text("Reintentar carga de países")
                }
            }
            localError?.let { ErrorFeedback("Revisá los datos", it) }
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileUpdate) }
            Button(
                onClick = {
                    val input = validateUpdateProfileInput(displayName, bio, city, selectedCountryCode)
                    if (input == null) {
                        localError = "Revisá nombre, ciudad, país y bio. No uses etiquetas o formato HTML."
                    } else {
                        localError = null
                        onUpdateProfile(input)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Guardando..." else "Guardar cambios")
            }
    }
}

private fun profileNextStep(status: ProfileStatus): String = when (status) {
    else -> status.userDescription()
}

internal fun profileCountryDisplayName(
    profile: Profile,
    countries: List<CountryReference>,
): String = countries.firstOrNull { it.code == profile.countryCode }?.displayName ?: profile.countryCode

internal fun validateUpdateProfileInput(
    displayName: String,
    bio: String,
    city: String,
    countryCode: String,
): UpdateProfileInput? {
    val cleanDisplayName = TextSafety.normalizeSingleLine(displayName, maxLength = 100)
    val cleanBio = TextSafety.normalizeMultiline(bio, maxLength = 1_000)
    val cleanCity = TextSafety.normalizeSingleLine(city, maxLength = 100)
    val cleanCountryCode = countryCode.trim()

    if (cleanDisplayName.length !in 2..100) return null
    if (cleanBio.length > 1000) return null
    if (cleanCity.isBlank() || cleanCity.length > 100) return null
    if (cleanCountryCode.isBlank()) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanDisplayName)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanBio)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanCity)) return null

    return UpdateProfileInput(
        displayName = cleanDisplayName,
        bio = cleanBio.ifBlank { null },
        city = cleanCity,
        countryCode = cleanCountryCode,
    )
}
