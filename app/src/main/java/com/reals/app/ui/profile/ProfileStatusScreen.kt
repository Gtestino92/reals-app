@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.reals.app.ui.profile

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType

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
    managementSurface: ProfileManagementSurface = ProfileManagementSurface.Setup,
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
    onOpenProfileQuestions: () -> Unit,
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
    val presentationPolicy = profileManagementPresentationPolicy(
        managementSurface = managementSurface,
        profileStatus = (session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.status,
    )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = managementSurface.screenTitle(onBackHome == null),
                modifier = Modifier.weight(1f),
                style = RealsType.ScreenTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            onBackHome?.let { backHome ->
                TextButton(
                    onClick = backHome,
                    enabled = !busy,
                ) {
                    Text("Volver")
                }
            }
        }
        Text(
            text = managementSurface.screenBody(),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RealsBrandDivider(modifier = Modifier.padding(top = 18.dp))
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
                Text(if (homeLoading) "Actualizando Inicio..." else "Reintentar Inicio")
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
                managementSurface = managementSurface,
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
                onOpenProfileQuestions = onOpenProfileQuestions,
            )
        }
        if (onBackHome != null) {
            Row(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onBackHome, enabled = !busy) {
                    Text("Volver a Inicio")
                }
            }
        }

        if (presentationPolicy.showAccountManagement) {
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
}

enum class ProfileManagementSurface {
    Setup,
    Profile,
    Search,
}

private fun ProfileManagementSurface.screenTitle(defaultSetup: Boolean): String = when (this) {
    ProfileManagementSurface.Setup -> if (defaultSetup) "Estado de Reals" else "Perfil y preferencias"
    ProfileManagementSurface.Profile -> "Tu perfil"
    ProfileManagementSurface.Search -> "Preferencias"
}

private fun ProfileManagementSurface.screenBody(): String = when (this) {
    ProfileManagementSurface.Setup -> "Completá tu presentación y tus preferencias para activar Reals."
    ProfileManagementSurface.Profile -> "Gestioná cómo te presentás ante otras personas."
    ProfileManagementSurface.Search ->
        "Definí qué personas querés que Reals tenga en cuenta al buscar un chat."
}

internal data class ProfileManagementPresentationPolicy(
    val showProfileSection: Boolean,
    val showSearchSection: Boolean,
    val profileSectionCollapsible: Boolean,
    val searchSectionCollapsible: Boolean,
    val showAccountManagement: Boolean,
    val initialExpandedSection: ProfileSection?,
)

internal fun profileManagementPresentationPolicy(
    managementSurface: ProfileManagementSurface,
    profileStatus: ProfileStatus?,
): ProfileManagementPresentationPolicy = when (managementSurface) {
    ProfileManagementSurface.Setup -> ProfileManagementPresentationPolicy(
        showProfileSection = true,
        showSearchSection = true,
        profileSectionCollapsible = true,
        searchSectionCollapsible = true,
        showAccountManagement = true,
        initialExpandedSection = if (profileStatus == ProfileStatus.Draft) ProfileSection.Photos else null,
    )
    ProfileManagementSurface.Profile -> ProfileManagementPresentationPolicy(
        showProfileSection = true,
        showSearchSection = false,
        profileSectionCollapsible = false,
        searchSectionCollapsible = false,
        showAccountManagement = false,
        initialExpandedSection = ProfileSection.Profile,
    )
    ProfileManagementSurface.Search -> ProfileManagementPresentationPolicy(
        showProfileSection = false,
        showSearchSection = true,
        profileSectionCollapsible = false,
        searchSectionCollapsible = false,
        showAccountManagement = false,
        initialExpandedSection = ProfileSection.Filters,
    )
}

@Composable
private fun MissingProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Todavía no tenés perfil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Tu cuenta ya está autenticada y provisionada. El proximo pasó es crear el perfil para completar el onboarding.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    managementSurface: ProfileManagementSurface,
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
    onOpenProfileQuestions: () -> Unit,
) {
    val presentationPolicy = profileManagementPresentationPolicy(
        managementSurface = managementSurface,
        profileStatus = profile.status,
    )
    var expandedSection by rememberSaveable(profile.id, managementSurface) {
        mutableStateOf(presentationPolicy.initialExpandedSection)
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
        if (
            profileSaveSucceeded &&
            expandedSection == ProfileSection.Profile &&
            presentationPolicy.profileSectionCollapsible
        ) {
            expandedSection = null
        }
        if (profileSaveInFlight && !profileUpdateLoading && (profileUpdateMessage != null || profileUpdateError != null)) {
            profileSaveRequested = false
            profileSaveInFlight = false
        }
    }
    LaunchedEffect(matchFiltersLoading, matchFiltersMessage, matchFiltersError) {
        if (
            matchFiltersSaveSucceeded &&
            expandedSection == ProfileSection.Filters &&
            presentationPolicy.searchSectionCollapsible
        ) {
            expandedSection = null
        }
        if (matchFiltersSaveInFlight && !matchFiltersLoading && (matchFiltersMessage != null || matchFiltersError != null)) {
            matchFiltersSaveRequested = false
            matchFiltersSaveInFlight = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (presentationPolicy.showProfileSection) {
            ProfileDetailsCard(
                profile = profile,
                countries = countries,
                countriesLoading = countriesLoading,
                countriesError = countriesError,
                loading = profileUpdateLoading,
                busy = busy,
                error = profileUpdateError,
                message = profileUpdateMessage,
                expanded = !presentationPolicy.profileSectionCollapsible ||
                    (expandedSection == ProfileSection.Profile && !profileSaveSucceeded),
                collapsible = presentationPolicy.profileSectionCollapsible,
                onToggleExpanded = {
                    expandedSection = if (expandedSection == ProfileSection.Profile) null else ProfileSection.Profile
                },
                onUpdateProfile = {
                    profileSaveRequested = true
                    onUpdateProfile(it)
                },
                onLoadCountries = onLoadCountries,
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
            ProfileQuestionsEntryCard(
                busy = busy,
                onOpenProfileQuestions = onOpenProfileQuestions,
            )
        }
        if (presentationPolicy.showSearchSection) {
            MatchPreferencesCard(
                profile = profile,
                loading = matchFiltersLoading,
                busy = busy,
                error = matchFiltersError,
                message = matchFiltersMessage,
                expanded = !presentationPolicy.searchSectionCollapsible ||
                    (expandedSection == ProfileSection.Filters && !matchFiltersSaveSucceeded),
                collapsible = presentationPolicy.searchSectionCollapsible,
                onToggleExpanded = {
                    expandedSection = if (expandedSection == ProfileSection.Filters) null else ProfileSection.Filters
                },
                onUpdateMatchFilters = {
                    matchFiltersSaveRequested = true
                    onUpdateMatchFilters(it)
                },
            )
        }
    }
}

internal enum class ProfileSection {
    Profile,
    Filters,
    Photos,
}

@Composable
private fun ProfileQuestionsEntryCard(
    busy: Boolean,
    onOpenProfileQuestions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Preguntas del perfil",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Son opcionales y públicas. Podés responder varias y elegir hasta tres para mostrar en tu perfil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenProfileQuestions,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Responder o editar")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberExpandedSectionRequester(expanded: Boolean): BringIntoViewRequester {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return requester
}


@Composable
internal fun SectionHeader(
    title: String,
    expanded: Boolean,
    collapsible: Boolean = true,
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
        if (expanded && collapsible) {
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
internal fun ErrorFeedback(title: String, message: String) {
    FeedbackCard(title = title, message = message, tone = FeedbackTone.Error)
}

@Composable
internal fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}
