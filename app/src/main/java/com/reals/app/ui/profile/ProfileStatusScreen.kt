@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.reals.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
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
import com.reals.app.ui.common.userDescription
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ProfileStatusScreen(
    session: ProvisionedSession,
    profileUpdateLoading: Boolean,
    profileUpdateError: ApiError?,
    profileUpdateMessage: String?,
    countriesLoading: Boolean,
    countries: List<CountryReference>,
    countriesError: ApiError?,
    matchFiltersLoading: Boolean,
    matchFiltersError: ApiError?,
    matchFiltersMessage: String?,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    photoReorderLoading: Boolean,
    photoReorderError: ApiError?,
    photoReorderMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    emailVerificationSending: Boolean,
    emailVerificationChecking: Boolean,
    emailVerificationMessage: String?,
    emailVerificationError: String?,
    emailVerificationRequired: Boolean,
    emailVerificationLocallyVerified: Boolean,
    resendEmailVerificationAvailableAtMillis: Long?,
    checkEmailVerificationAvailableAtMillis: Long?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    homeLoading: Boolean = false,
    homeError: ApiError? = null,
    showDraftAfterEditNotice: Boolean = false,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onLoadCountries: () -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
    onLoadPhotos: () -> Unit,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onMovePhoto: (photoId: String, targetPosition: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onRetryHome: () -> Unit = onRefresh,
    onBackHome: (() -> Unit)? = null,
) {
    val busy = profileUpdateLoading ||
        matchFiltersLoading ||
        photoActionLoading ||
        photoReorderLoading ||
        activationLoading ||
        emailVerificationSending ||
        emailVerificationChecking ||
        accountDeleteLoading ||
        homeLoading
    val scrollState = rememberScrollState()
    var accountExpanded by rememberSaveable(session.user.id) { mutableStateOf(false) }

    LaunchedEffect(accountExpanded) {
        if (!accountExpanded) return@LaunchedEffect
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(Unit) {
        onLoadCountries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (onBackHome == null) "Estado de Reals" else "Perfil y preferencias",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Gestioná tu perfil, tus preferencias y tus fotos.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (showDraftAfterEditNotice) {
            FeedbackCard(
                title = "Tu perfil volvió a borrador",
                message = "Como modificaste tus fotos, necesitás reactivar el perfil antes de buscar nuevas personas.",
                tone = FeedbackTone.Warning,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (homeError != null) {
            ApiErrorFeedbackCard(homeError, ErrorContext.Home)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetryHome,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (homeLoading) "Actualizando Home..." else "Reintentar Home")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        when (val snapshot = session.profileSnapshot) {
            ProfileSnapshot.Missing -> MissingProfileCard()
            is ProfileSnapshot.Found -> ProfileCard(
                profile = snapshot.profile,
                profileUpdateLoading = profileUpdateLoading,
                profileUpdateError = profileUpdateError,
                profileUpdateMessage = profileUpdateMessage,
                countriesLoading = countriesLoading,
                countries = countries,
                countriesError = countriesError,
                matchFiltersLoading = matchFiltersLoading,
                matchFiltersError = matchFiltersError,
                matchFiltersMessage = matchFiltersMessage,
                photosLoading = photosLoading,
                photos = photos,
                photosError = photosError,
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
                photoReorderLoading = photoReorderLoading,
                photoReorderError = photoReorderError,
                photoReorderMessage = photoReorderMessage,
                activationLoading = activationLoading,
                activationError = activationError,
                emailVerificationSending = emailVerificationSending,
                emailVerificationChecking = emailVerificationChecking,
                emailVerificationMessage = emailVerificationMessage,
                emailVerificationError = emailVerificationError,
                emailVerificationRequired = emailVerificationRequired,
                emailVerificationLocallyVerified = emailVerificationLocallyVerified,
                resendEmailVerificationAvailableAtMillis = resendEmailVerificationAvailableAtMillis,
                checkEmailVerificationAvailableAtMillis = checkEmailVerificationAvailableAtMillis,
                onUpdateProfile = onUpdateProfile,
                onLoadCountries = onLoadCountries,
                onUpdateMatchFilters = onUpdateMatchFilters,
                onLoadPhotos = onLoadPhotos,
                onAddPhotoFile = onAddPhotoFile,
                onReplacePhotoFile = onReplacePhotoFile,
                onDeletePhoto = onDeletePhoto,
                onMovePhoto = onMovePhoto,
                onActivateProfile = onActivateProfile,
                onResendEmailVerification = onResendEmailVerification,
                onCheckEmailVerification = onCheckEmailVerification,
            )
        }
        if (onBackHome != null) {
            Row(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onBackHome, enabled = !busy) {
                    Text("Volver a Home")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DeleteAccountSection(
            busy = busy,
            loading = accountDeleteLoading,
            error = accountDeleteError,
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = it },
            onSignOut = onSignOut,
            onDeleteAccount = onDeleteAccount,
        )
    }
}

@Composable
private fun MissingProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Todavía no tenés perfil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Tu cuenta ya está autenticada y provisionada. El proximo pasó es crear el perfil para completar el onboarding.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    profileUpdateLoading: Boolean,
    profileUpdateError: ApiError?,
    profileUpdateMessage: String?,
    countriesLoading: Boolean,
    countries: List<CountryReference>,
    countriesError: ApiError?,
    matchFiltersLoading: Boolean,
    matchFiltersError: ApiError?,
    matchFiltersMessage: String?,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    photoReorderLoading: Boolean,
    photoReorderError: ApiError?,
    photoReorderMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    emailVerificationSending: Boolean,
    emailVerificationChecking: Boolean,
    emailVerificationMessage: String?,
    emailVerificationError: String?,
    emailVerificationRequired: Boolean,
    emailVerificationLocallyVerified: Boolean,
    resendEmailVerificationAvailableAtMillis: Long?,
    checkEmailVerificationAvailableAtMillis: Long?,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onLoadCountries: () -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
    onLoadPhotos: () -> Unit,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onMovePhoto: (photoId: String, targetPosition: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
) {
    var expandedSection by rememberSaveable(profile.id) {
        mutableStateOf(if (profile.status == ProfileStatus.Draft) ProfileSection.Photos else null)
    }
    var profileSaveRequested by rememberSaveable(profile.id) { mutableStateOf(false) }
    var profileSaveInFlight by rememberSaveable(profile.id) { mutableStateOf(false) }
    var matchFiltersSaveRequested by rememberSaveable(profile.id) { mutableStateOf(false) }
    var matchFiltersSaveInFlight by rememberSaveable(profile.id) { mutableStateOf(false) }
    val busy = profileUpdateLoading ||
            matchFiltersLoading ||
            photosLoading ||
            photoActionLoading ||
            photoReorderLoading ||
            activationLoading ||
            emailVerificationSending ||
            emailVerificationChecking

    LaunchedEffect(profileUpdateLoading) {
        if (profileSaveRequested && profileUpdateLoading) {
            profileSaveInFlight = true
        }
    }
    LaunchedEffect(matchFiltersLoading) {
        if (matchFiltersSaveRequested && matchFiltersLoading) {
            matchFiltersSaveInFlight = true
        }
    }
    val profileSaveSucceeded = profileSaveInFlight && !profileUpdateLoading && profileUpdateMessage != null
    val matchFiltersSaveSucceeded = matchFiltersSaveInFlight && !matchFiltersLoading && matchFiltersMessage != null

    LaunchedEffect(profileUpdateLoading, profileUpdateMessage, profileUpdateError) {
        if (profileSaveSucceeded && expandedSection == ProfileSection.Profile) {
            expandedSection = null
        }
        if (profileSaveInFlight && !profileUpdateLoading && (profileUpdateMessage != null || profileUpdateError != null)) {
            profileSaveRequested = false
            profileSaveInFlight = false
        }
    }
    LaunchedEffect(matchFiltersLoading, matchFiltersMessage, matchFiltersError) {
        if (matchFiltersSaveSucceeded && expandedSection == ProfileSection.Filters) {
            expandedSection = null
        }
        if (matchFiltersSaveInFlight && !matchFiltersLoading && (matchFiltersMessage != null || matchFiltersError != null)) {
            matchFiltersSaveRequested = false
            matchFiltersSaveInFlight = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileDetailsCard(
            profile = profile,
            countries = countries,
            countriesLoading = countriesLoading,
            countriesError = countriesError,
            loading = profileUpdateLoading,
            busy = busy,
            error = profileUpdateError,
            message = profileUpdateMessage,
            expanded = expandedSection == ProfileSection.Profile && !profileSaveSucceeded,
            onToggleExpanded = {
                expandedSection = if (expandedSection == ProfileSection.Profile) null else ProfileSection.Profile
            },
            onUpdateProfile = {
                profileSaveRequested = true
                onUpdateProfile(it)
            },
            onLoadCountries = onLoadCountries,
        )
        MatchPreferencesCard(
            profile = profile,
            loading = matchFiltersLoading,
            busy = busy,
            error = matchFiltersError,
            message = matchFiltersMessage,
            expanded = expandedSection == ProfileSection.Filters && !matchFiltersSaveSucceeded,
            onToggleExpanded = {
                expandedSection = if (expandedSection == ProfileSection.Filters) null else ProfileSection.Filters
            },
            onUpdateMatchFilters = {
                matchFiltersSaveRequested = true
                onUpdateMatchFilters(it)
            },
        )
        PhotosCard(
            profile = profile,
            photosLoading = photosLoading,
            photos = photos,
            photosError = photosError,
            photoActionLoading = photoActionLoading,
            photoActionError = photoActionError,
            photoActionMessage = photoActionMessage,
            photoReorderLoading = photoReorderLoading,
            photoReorderError = photoReorderError,
            photoReorderMessage = photoReorderMessage,
            activationLoading = activationLoading,
            activationError = activationError,
            emailVerificationSending = emailVerificationSending,
            emailVerificationChecking = emailVerificationChecking,
            emailVerificationMessage = emailVerificationMessage,
            emailVerificationError = emailVerificationError,
            emailVerificationRequired = emailVerificationRequired,
            emailVerificationLocallyVerified = emailVerificationLocallyVerified,
            resendEmailVerificationAvailableAtMillis = resendEmailVerificationAvailableAtMillis,
            checkEmailVerificationAvailableAtMillis = checkEmailVerificationAvailableAtMillis,
            busy = busy,
            expanded = expandedSection == ProfileSection.Photos,
            onToggleExpanded = {
                expandedSection = if (expandedSection == ProfileSection.Photos) null else ProfileSection.Photos
            },
            onLoadPhotos = onLoadPhotos,
            onAddPhotoFile = onAddPhotoFile,
            onReplacePhotoFile = onReplacePhotoFile,
            onDeletePhoto = onDeletePhoto,
            onMovePhoto = onMovePhoto,
            onActivateProfile = onActivateProfile,
            onResendEmailVerification = onResendEmailVerification,
            onCheckEmailVerification = onCheckEmailVerification,
        )
    }
}

private enum class ProfileSection {
    Profile,
    Filters,
    Photos,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberExpandedSectionRequester(expanded: Boolean): BringIntoViewRequester {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return requester
}

private val IntentionOptions = listOf(
    "DATE",
    "FRIENDSHIP",
    "CASUAL",
)

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    closeEnabled: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        if (expanded) {
            TextButton(
                onClick = onClose,
                enabled = closeEnabled,
            ) {
                Text("Cerrar")
            }
        }
    }
}

@Composable
private fun ProfileDetailsCard(
    profile: Profile,
    countries: List<CountryReference>,
    countriesLoading: Boolean,
    countriesError: ApiError?,
    loading: Boolean,
    busy: Boolean,
    error: ApiError?,
    message: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onLoadCountries: () -> Unit,
) {
    val bringIntoViewRequester = rememberExpandedSectionRequester(expanded)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "Tu perfil",
                expanded = expanded,
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
private fun MatchPreferencesCard(
    profile: Profile,
    loading: Boolean,
    busy: Boolean,
    error: ApiError?,
    message: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
) {
    val bringIntoViewRequester = rememberExpandedSectionRequester(expanded)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "Preferencias de match",
                expanded = expanded,
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
private fun PhotosCard(
    profile: Profile,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    photoReorderLoading: Boolean,
    photoReorderError: ApiError?,
    photoReorderMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    emailVerificationSending: Boolean,
    emailVerificationChecking: Boolean,
    emailVerificationMessage: String?,
    emailVerificationError: String?,
    emailVerificationRequired: Boolean,
    emailVerificationLocallyVerified: Boolean,
    resendEmailVerificationAvailableAtMillis: Long?,
    checkEmailVerificationAvailableAtMillis: Long?,
    busy: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onLoadPhotos: () -> Unit,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onMovePhoto: (photoId: String, targetPosition: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
) {
    val bringIntoViewRequester = rememberExpandedSectionRequester(expanded)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "Fotos",
                expanded = expanded,
                closeEnabled = !busy,
                onClose = onToggleExpanded,
            )
            if (expanded) {
                PhotoManagerActions(
                    profile = profile,
                    photosLoading = photosLoading,
                    photos = photos,
                    photosError = photosError,
                    photoActionLoading = photoActionLoading,
                    photoActionError = photoActionError,
                    photoActionMessage = photoActionMessage,
                    photoReorderLoading = photoReorderLoading,
                    photoReorderError = photoReorderError,
                    photoReorderMessage = photoReorderMessage,
                    activationLoading = activationLoading,
                    activationError = activationError,
                    emailVerificationSending = emailVerificationSending,
                    emailVerificationChecking = emailVerificationChecking,
                    emailVerificationMessage = emailVerificationMessage,
                    emailVerificationError = emailVerificationError,
                    emailVerificationRequired = emailVerificationRequired,
                    emailVerificationLocallyVerified = emailVerificationLocallyVerified,
                    resendEmailVerificationAvailableAtMillis = resendEmailVerificationAvailableAtMillis,
                    checkEmailVerificationAvailableAtMillis = checkEmailVerificationAvailableAtMillis,
                    busy = busy,
                    onLoadPhotos = onLoadPhotos,
                    onAddPhotoFile = onAddPhotoFile,
                    onReplacePhotoFile = onReplacePhotoFile,
                    onDeletePhoto = onDeletePhoto,
                    onMovePhoto = onMovePhoto,
                    onActivateProfile = onActivateProfile,
                    onResendEmailVerification = onResendEmailVerification,
                    onCheckEmailVerification = onCheckEmailVerification,
                )
            } else {
                Text("${profile.photoCount} de 9 fotos", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Autenticidad del perfil verificada: ${yesNo(profile.authenticityVerified)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onToggleExpanded,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Administrar fotos")
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
            OutlinedTextField(displayName, { displayName = it.take(100) }, label = { Text("Nombre visible") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(1_000) }, label = { Text("Bio") }, enabled = !loading, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it.take(100) }, label = { Text("Ciudad") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
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
            text = "Definí con quién querés que te conectemos.",
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

@Composable
private fun PhotoManagerActions(
    profile: Profile,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    photoReorderLoading: Boolean,
    photoReorderError: ApiError?,
    photoReorderMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    emailVerificationSending: Boolean,
    emailVerificationChecking: Boolean,
    emailVerificationMessage: String?,
    emailVerificationError: String?,
    emailVerificationRequired: Boolean,
    emailVerificationLocallyVerified: Boolean,
    resendEmailVerificationAvailableAtMillis: Long?,
    checkEmailVerificationAvailableAtMillis: Long?,
    busy: Boolean,
    onLoadPhotos: () -> Unit,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onMovePhoto: (photoId: String, targetPosition: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
) {
    val context = LocalContext.current
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var pendingTargetKind by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var pendingTargetPosition by rememberSaveable(profile.id) { mutableStateOf<Int?>(null) }
    var pendingTargetPhotoId by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var cropSourceUriString by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var photoActionKindName by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var photoActionPosition by rememberSaveable(profile.id) { mutableStateOf<Int?>(null) }
    var photoActionPhotoId by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    val pendingTarget = remember(pendingTargetKind, pendingTargetPosition, pendingTargetPhotoId) {
        profilePhotoSelectionTarget(pendingTargetKind, pendingTargetPosition, pendingTargetPhotoId)
    }
    val pendingPhotoAction = remember(photoActionKindName, photoActionPosition, photoActionPhotoId) {
        profilePhotoActionPresentation(photoActionKindName, photoActionPosition, photoActionPhotoId)
    }
    val visiblePhotoAction = pendingPhotoAction.takeIf { photoActionLoading }
    val cropRequest = remember(cropSourceUriString, pendingTarget) {
        val sourceUri = cropSourceUriString?.let(Uri::parse)
        val target = pendingTarget
        if (sourceUri != null && target != null) ProfilePhotoCropRequest(sourceUri, target) else null
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pendingTarget != null) {
            localError = null
            cropSourceUriString = uri.toString()
        } else {
            pendingTargetKind = null
            pendingTargetPosition = null
            pendingTargetPhotoId = null
        }
    }
    LaunchedEffect(Unit) {
        deleteStaleProfilePhotoCropFiles(
            cacheDir = profilePhotoCropCacheDirectory(context),
            nowMillis = System.currentTimeMillis(),
            maxAgeMillis = 24.hours.inWholeMilliseconds,
        )
    }
    LaunchedEffect(photoActionLoading, photoActionMessage, photoActionError) {
        if (!photoActionLoading && (photoActionMessage != null || photoActionError != null)) {
            pendingTargetKind = null
            pendingTargetPosition = null
            pendingTargetPhotoId = null
            cropSourceUriString = null
            photoActionKindName = null
            photoActionPosition = null
            photoActionPhotoId = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Subí, reemplaza o borra fotos. Las miniaturas se muestran cuadradas; la foto se publica en formato vertical 4:5. Para reordenarlas, mantené presionada una foto y arrastrala.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            photosError?.let {
                ApiErrorFeedbackCard(it, ErrorContext.PhotoUpload)
                OutlinedButton(onClick = onLoadPhotos, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photosLoading) "Cargando fotos..." else "Reintentar carga de fotos")
                }
            }
            if (photoReorderLoading) {
                FeedbackCard(
                    title = "Guardando",
                    message = "Guardando orden de fotos...",
                    tone = FeedbackTone.Info,
                )
            }
            photoReorderError?.let { ApiErrorFeedbackCard(it, ErrorContext.PhotoUpload) }
            if (photoActionLoading) {
                ProfilePhotoActionProgressCard(action = visiblePhotoAction)
            }
            PhotoGrid(
                photos = photos,
                busy = busy,
                pendingAction = visiblePhotoAction,
                onPickNewFile = { position ->
                    localError = null
                    pendingTargetKind = ProfilePhotoAddTargetKind
                    pendingTargetPosition = position
                    pendingTargetPhotoId = null
                    cropSourceUriString = null
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onPickReplacementFile = { photoId, position ->
                    localError = null
                    pendingTargetKind = ProfilePhotoReplaceTargetKind
                    pendingTargetPosition = position
                    pendingTargetPhotoId = photoId
                    cropSourceUriString = null
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onDeletePhoto = { photoId, position ->
                    localError = null
                    photoActionKindName = ProfilePhotoActionKind.Delete.name
                    photoActionPosition = position
                    photoActionPhotoId = photoId
                    onDeletePhoto(photoId, position)
                },
                onMovePhoto = onMovePhoto,
            )
            cropRequest?.let { request ->
                ProfilePhotoCropDialog(
                    request = request,
                    onCancel = {
                        pendingTargetKind = null
                        pendingTargetPosition = null
                        pendingTargetPhotoId = null
                        cropSourceUriString = null
                    },
                    onCropped = { croppedUri ->
                        val action = request.target.toProfilePhotoActionPresentation()
                        photoActionKindName = action.kind.name
                        photoActionPosition = action.position
                        photoActionPhotoId = action.photoId
                        dispatchCroppedProfilePhoto(
                            target = request.target,
                            croppedUri = croppedUri,
                            onAddPhotoFile = onAddPhotoFile,
                            onReplacePhotoFile = onReplacePhotoFile,
                        )
                        cropSourceUriString = null
                    },
                )
            }
            localError?.let { ErrorFeedback("Revisá las fotos", it) }
            ProfilePhotoActionFeedback(
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
            )
            activationError?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileActivation) }
            val showEmailVerificationActions = shouldShowEmailVerificationActions(
                emailVerificationLocallyVerified = emailVerificationLocallyVerified,
                emailVerificationRequired = emailVerificationRequired,
                activationError = activationError,
            )

            if (showEmailVerificationActions) {
                EmailVerificationActions(
                    sending = emailVerificationSending,
                    checking = emailVerificationChecking,
                    message = emailVerificationMessage,
                    error = emailVerificationError,
                    busy = busy,
                    emailVerificationLocallyVerified = emailVerificationLocallyVerified,
                    resendAvailableAtMillis = resendEmailVerificationAvailableAtMillis,
                    checkAvailableAtMillis = checkEmailVerificationAvailableAtMillis,
                    onResendEmailVerification = onResendEmailVerification,
                    onCheckEmailVerification = onCheckEmailVerification,
                )
            }
            if (profile.status == ProfileStatus.Draft) {
                val activationEnabled = !busy && (!emailVerificationRequired || emailVerificationLocallyVerified)
                OutlinedButton(
                    onClick = { onActivateProfile(profile) },
                    enabled = activationEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (activationLoading) "Activando..." else "Intentar activar perfil")
                }
            }
    }
}

@Composable
private fun EmailVerificationActions(
    sending: Boolean,
    checking: Boolean,
    message: String?,
    error: String?,
    busy: Boolean,
    emailVerificationLocallyVerified: Boolean,
    resendAvailableAtMillis: Long?,
    checkAvailableAtMillis: Long?,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
) {
    var nowMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val resendCoolingDown = resendAvailableAtMillis?.let { nowMillis < it } == true
    val checkCoolingDown = checkAvailableAtMillis?.let { nowMillis < it } == true
    val nextAvailableAt = listOfNotNull(resendAvailableAtMillis, checkAvailableAtMillis)
        .filter { nowMillis < it }
        .minOrNull()

    LaunchedEffect(nextAvailableAt) {
        if (nextAvailableAt != null) {
            delay((nextAvailableAt - System.currentTimeMillis()).coerceAtLeast(0L).milliseconds)
            nowMillis = System.currentTimeMillis()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Verificá tu email antes de activar el perfil.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Te enviamos un correo de verificación. Revisá tu bandeja de entrada o spam.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            message?.let { SuccessFeedback(it) }
            error?.let { ErrorFeedback("No pudimos verificar el email", it) }
            OutlinedButton(
                onClick = onResendEmailVerification,
                enabled = !busy && !resendCoolingDown && !emailVerificationLocallyVerified,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sending) "Enviando..." else "Reenviar email")
            }
            Button(
                onClick = onCheckEmailVerification,
                enabled = !busy && !checkCoolingDown && !emailVerificationLocallyVerified,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checking) "Comprobándo..." else "Ya verifiqué")
            }
        }
    }
}

@Composable
internal fun ProfilePhotoActionFeedback(
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photoActionError?.let { ApiErrorFeedbackCard(it, ErrorContext.PhotoUpload) }
        if (!photoActionLoading && photoActionError == null) {
            photoActionMessage?.let { SuccessFeedback(it) }
        }
    }
}

@Composable
internal fun ProfilePhotoActionProgressCard(
    action: ProfilePhotoActionPresentation?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = action.slotStateDescription()
            }
            .testTag(ProfilePhotoActionProgressTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .testTag(ProfilePhotoActionProgressIndicatorTag),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = action.progressTitle(),
                    modifier = Modifier.testTag(ProfilePhotoActionProgressTitleTag),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = action.progressMessage(),
                    modifier = Modifier.testTag(ProfilePhotoActionProgressMessageTag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun PhotoGrid(
    photos: List<ProfilePhoto>,
    busy: Boolean,
    pendingAction: ProfilePhotoActionPresentation? = null,
    onPickNewFile: (position: Int) -> Unit,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onMovePhoto: (photoId: String, targetPosition: Int) -> Unit,
) {
    val photosByPosition = photos.profilePhotosByGridPosition()
    val slotBoundsByPosition = remember { mutableStateMapOf<Int, Rect>() }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    var dragState by remember { mutableStateOf<PhotoGridDragState?>(null) }

    fun targetPositionAt(pointerPosition: Offset): Int? =
        slotBoundsByPosition.entries.firstOrNull { (_, bounds) ->
            bounds.contains(pointerPosition)
        }?.key

    Box(
        modifier = Modifier
            .testTag(ProfilePhotoGridRootTag)
            .onGloballyPositioned { coordinates ->
                gridBounds = coordinates.boundsInRoot()
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfilePhotoGridPositions.chunked(3).forEach { rowPositions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowPositions.forEach { position ->
                        val currentDragState = dragState
                        PhotoSlot(
                            position = position,
                            photo = photosByPosition[position],
                            busy = busy,
                            pendingAction = pendingAction?.takeIf { it.targetsPosition(position) },
                            isDragTarget = currentDragState?.targetPosition == position,
                            isDraggingSource = currentDragState?.sourcePosition == position,
                            modifier = Modifier.weight(1f),
                            onSlotBoundsChanged = { slotPosition, bounds ->
                                slotBoundsByPosition[slotPosition] = bounds
                            },
                        onPickNewFile = onPickNewFile,
                        onPickReplacementFile = onPickReplacementFile,
                        onDeletePhoto = onDeletePhoto,
                        onDragStart = { photoId, sourcePosition, pointerPosition ->
                                dragState = PhotoGridDragState(
                                    photoId = photoId,
                                    sourcePosition = sourcePosition,
                                    currentPosition = pointerPosition,
                                    targetPosition = targetPositionAt(pointerPosition),
                                )
                            },
                            onDrag = { dragAmount ->
                                val activeDrag = dragState
                                if (activeDrag != null) {
                                    val nextPosition = activeDrag.currentPosition + dragAmount
                                    dragState = activeDrag.copy(
                                        currentPosition = nextPosition,
                                        targetPosition = targetPositionAt(nextPosition),
                                    )
                                }
                            },
                            onDragEnd = {
                                val completedDrag = dragState
                                dragState = null
                                val targetPosition = completedDrag?.targetPosition
                                if (
                                    completedDrag != null &&
                                    targetPosition != null &&
                                    targetPosition != completedDrag.sourcePosition
                                ) {
                                    onMovePhoto(completedDrag.photoId, targetPosition)
                                }
                            },
                            onDragCancel = {
                                dragState = null
                            },
                        )
                    }
                }
            }
        }
        val activeDrag = dragState
        val draggedPhoto = activeDrag?.let { photosByPosition[it.sourcePosition] }
        val sourceBounds = activeDrag?.let { slotBoundsByPosition[it.sourcePosition] }
        val currentGridBounds = gridBounds
        if (
            activeDrag != null &&
            draggedPhoto != null &&
            sourceBounds != null &&
            currentGridBounds != null
        ) {
            DraggedPhotoGhost(
                photo = draggedPhoto,
                pointerPosition = activeDrag.currentPosition,
                sourceBounds = sourceBounds,
                gridBounds = currentGridBounds,
            )
        }
    }
}

@Composable
internal fun PhotoSlot(
    position: Int,
    photo: ProfilePhoto?,
    busy: Boolean,
    pendingAction: ProfilePhotoActionPresentation? = null,
    isDragTarget: Boolean,
    isDraggingSource: Boolean,
    modifier: Modifier = Modifier,
    onSlotBoundsChanged: (position: Int, bounds: Rect) -> Unit,
    onPickNewFile: (position: Int) -> Unit,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onDragStart: (photoId: String, sourcePosition: Int, pointerPosition: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var slotBounds by remember { mutableStateOf<Rect?>(null) }
    val positionedModifier = modifier
        .testTag(profilePhotoSlotTag(position))
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            slotBounds = bounds
            onSlotBoundsChanged(position, bounds)
        }
    if (photo == null) {
        EmptyPhotoSlot(
            position = position,
            busy = busy,
            pendingAction = pendingAction,
            isDragTarget = isDragTarget,
            modifier = positionedModifier,
            onPickNewFile = onPickNewFile,
        )
    } else {
        FilledPhotoSlot(
            photo = photo,
            busy = busy,
            pendingAction = pendingAction,
            isDragTarget = isDragTarget,
            isDraggingSource = isDraggingSource,
            slotBounds = slotBounds,
            modifier = positionedModifier,
            onPickReplacementFile = onPickReplacementFile,
            onDeletePhoto = onDeletePhoto,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
        )
    }
}

@Composable
internal fun FilledPhotoSlot(
    photo: ProfilePhoto,
    busy: Boolean,
    pendingAction: ProfilePhotoActionPresentation? = null,
    isDragTarget: Boolean,
    isDraggingSource: Boolean,
    slotBounds: Rect?,
    modifier: Modifier = Modifier,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onDragStart: (photoId: String, sourcePosition: Int, pointerPosition: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val imageShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    val actionShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    val dragModifier = if (!busy) {
        Modifier.pointerInput(photo.id, slotBounds) {
            detectDragGesturesAfterLongPress(
                onDragStart = { localOffset ->
                    val bounds = slotBounds ?: return@detectDragGesturesAfterLongPress
                    onDragStart(
                        photo.id,
                        photo.position,
                        Offset(bounds.left + localOffset.x, bounds.top + localOffset.y),
                    )
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }
    } else {
        Modifier
    }
    val actionStateDescription = pendingAction.slotStateDescription()
    Column(
        modifier = modifier
            .alpha(if (isDraggingSource) 0.42f else 1f)
            .semantics {
                when {
                    pendingAction != null -> stateDescription = actionStateDescription
                    isDraggingSource -> stateDescription = "Dragging"
                    isDragTarget -> stateDescription = "Drop target"
                }
            },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(imageShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = if (isDragTarget) 2.dp else 1.dp,
                    color = if (isDragTarget) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = imageShape,
                ),
        ) {
            ProfilePhotoImage(
                photo = photo,
                contentDescription = "Foto de perfil ${photo.position}",
                modifier = Modifier
                    .fillMaxSize()
                    .then(dragModifier),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(ProfilePhotoDeleteTouchTargetSize)
                    .clickable(
                        enabled = !busy,
                        onClickLabel = "Borrar foto ${photo.position}",
                    ) { onDeletePhoto(photo.id, photo.position) }
                    .semantics { contentDescription = "Borrar foto ${photo.position}" }
                    .testTag(profilePhotoDeleteTag(photo.position)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(ProfilePhotoDeleteVisualSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.52f))
                        .testTag(profilePhotoDeleteVisualTag(photo.position)),
                ) {
                    Text(
                        text = "x",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = if (pendingAction != null) 0.58f else 0.34f,
                            ),
                        )
                        .then(
                            if (pendingAction != null) {
                                Modifier.testTag(profilePhotoActionTargetTag(photo.position))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pendingAction != null) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .testTag(profilePhotoActionTargetIndicatorTag(photo.position)),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ProfilePhotoReplaceActionMinHeight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ProfilePhotoReplaceActionMinHeight)
                    .clip(actionShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = actionShape,
                    )
                    .clickable(
                        enabled = !busy,
                        onClickLabel = "Reemplazar foto ${photo.position}",
                    ) { onPickReplacementFile(photo.id, photo.position) }
                    .semantics { contentDescription = "Reemplazar foto ${photo.position}" }
                    .testTag(profilePhotoReplaceTag(photo.position)),
            ) {
                Text(
                    text = "Cambiar",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun EmptyPhotoSlot(
    position: Int,
    busy: Boolean,
    pendingAction: ProfilePhotoActionPresentation? = null,
    isDragTarget: Boolean,
    modifier: Modifier = Modifier,
    onPickNewFile: (position: Int) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val actionStateDescription = pendingAction.slotStateDescription()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                when {
                    pendingAction != null -> stateDescription = actionStateDescription
                    isDragTarget -> stateDescription = "Drop target"
                }
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (isDragTarget) 2.dp else 1.dp,
                color = if (isDragTarget) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(
                enabled = !busy,
                onClickLabel = "Agregar foto $position",
            ) { onPickNewFile(position) }
            .semantics { contentDescription = "Agregar foto $position" }
            .testTag(profilePhotoAddTag(position)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "+",
                modifier = Modifier.testTag(profilePhotoAddPlusTag(position)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Agregar",
                modifier = Modifier.testTag(profilePhotoAddLabelTag(position)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(
                            alpha = if (pendingAction != null) 0.58f else 0.34f,
                        ),
                    )
                    .then(
                        if (pendingAction != null) {
                            Modifier.testTag(profilePhotoActionTargetTag(position))
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (pendingAction != null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .testTag(profilePhotoActionTargetIndicatorTag(position)),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DraggedPhotoGhost(
    photo: ProfilePhoto,
    pointerPosition: Offset,
    sourceBounds: Rect,
    gridBounds: Rect,
) {
    val sizePx = sourceBounds.width.coerceAtLeast(1f)
    val sizeDp = with(LocalDensity.current) { sizePx.toDp() }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (pointerPosition.x - gridBounds.left - sizePx / 2f).roundToInt(),
                    y = (pointerPosition.y - gridBounds.top - sizePx / 2f).roundToInt(),
                )
            }
            .size(sizeDp)
            .alpha(0.82f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape),
    ) {
        ProfilePhotoImage(
            photo = photo,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun ProfilePhotoImage(
    photo: ProfilePhoto,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val displayUrl = photo.url.toEmulatorReachableUrl()
    val context = LocalContext.current
    val imageRequest = remember(context, displayUrl) {
        ImageRequest.Builder(context)
            .data(displayUrl)
            .memoryCacheKey(displayUrl.stablePhotoCacheKey())
            .diskCacheKey(displayUrl.stablePhotoCacheKey())
            .build()
    }
    when {
        displayUrl.isRenderableImageUrl() -> {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        }

        else -> {
            Text(
                text = "Sin URL publica.",
                modifier = modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal val ProfilePhotoGridPositions: IntRange = 1..9

internal val ProfilePhotoDeleteTouchTargetSize = 48.dp
internal val ProfilePhotoDeleteVisualSize = 24.dp
internal val ProfilePhotoReplaceActionMinHeight = 48.dp

internal const val ProfilePhotoGridRootTag = "profile_photo_grid"
internal const val ProfilePhotoActionProgressTag = "profile_photo_action_progress"
internal const val ProfilePhotoActionProgressIndicatorTag = "profile_photo_action_progress_indicator"
internal const val ProfilePhotoActionProgressTitleTag = "profile_photo_action_progress_title"
internal const val ProfilePhotoActionProgressMessageTag = "profile_photo_action_progress_message"
internal fun profilePhotoSlotTag(position: Int): String = "profile_photo_slot_$position"
internal fun profilePhotoActionTargetTag(position: Int): String = "profile_photo_action_target_$position"
internal fun profilePhotoActionTargetIndicatorTag(position: Int): String =
    "profile_photo_action_target_indicator_$position"
internal fun profilePhotoDeleteTag(position: Int): String = "profile_photo_delete_$position"
internal fun profilePhotoDeleteVisualTag(position: Int): String = "profile_photo_delete_visual_$position"
internal fun profilePhotoReplaceTag(position: Int): String = "profile_photo_replace_$position"
internal fun profilePhotoAddTag(position: Int): String = "profile_photo_add_$position"
internal fun profilePhotoAddPlusTag(position: Int): String = "profile_photo_add_plus_$position"
internal fun profilePhotoAddLabelTag(position: Int): String = "profile_photo_add_label_$position"

internal fun List<ProfilePhoto>.profilePhotosByGridPosition(): Map<Int, ProfilePhoto> =
    filter { it.position in ProfilePhotoGridPositions }
        .associateBy { it.position }

private const val ProfilePhotoAddTargetKind = "add"
private const val ProfilePhotoReplaceTargetKind = "replace"

internal fun profilePhotoSelectionTarget(
    kind: String?,
    position: Int?,
    photoId: String?,
): ProfilePhotoSelectionTarget? =
    when (kind) {
        ProfilePhotoAddTargetKind ->
            position?.let(ProfilePhotoSelectionTarget::Add)

        ProfilePhotoReplaceTargetKind ->
            if (position != null && !photoId.isNullOrBlank()) {
                ProfilePhotoSelectionTarget.Replace(photoId = photoId, position = position)
            } else {
                null
            }

        else -> null
    }

private data class PhotoGridDragState(
    val photoId: String,
    val sourcePosition: Int,
    val currentPosition: Offset,
    val targetPosition: Int?,
)

internal fun String.toEmulatorReachableUrl(): String {
    if (isPresignedUrl()) return this
    return replace("http://localhost:", "http://10.0.2.2:")
        .replace("http://127.0.0.1:", "http://10.0.2.2:")
}

private fun String.stablePhotoCacheKey(): String = substringBefore("?")

internal fun String.isRenderableImageUrl(): Boolean {
    return startsWith("http://") || startsWith("https://")
}

private fun String.isPresignedUrl(): Boolean {
    return contains("X-Amz-Signature=")
}

@Composable
private fun ErrorFeedback(title: String, message: String) {
    FeedbackCard(title = title, message = message, tone = FeedbackTone.Error)
}

@Composable
private fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}

private fun profileNextStep(status: ProfileStatus): String = when (status) {
    else -> status.userDescription()
}

internal fun profileCountryDisplayName(
    profile: Profile,
    countries: List<CountryReference>,
): String = countries.firstOrNull { it.code == profile.countryCode }?.displayName ?: profile.countryCode

@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    optionLabel: (String) -> String = { it },
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable(label, value) { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${optionLabel(value)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

private fun yesNo(value: Boolean): String = if (value) "Sí" else "No"

internal fun shouldShowEmailVerificationActions(
    emailVerificationLocallyVerified: Boolean,
    emailVerificationRequired: Boolean,
    activationError: ApiError?,
): Boolean =
    !emailVerificationLocallyVerified &&
        (activationError.isEmailNotVerified() || emailVerificationRequired)

private fun ApiError?.isEmailNotVerified(): Boolean =
    this is ApiError.Backend &&
        backendErrorCode == BackendErrorCode.EmailNotVerified
