package com.reals.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.userDescription
import com.reals.app.ui.common.userLabel
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import kotlinx.coroutines.delay

@Composable
fun ProfileStatusScreen(
    session: ProvisionedSession,
    profileUpdateLoading: Boolean,
    profileUpdateError: ApiError?,
    profileUpdateMessage: String?,
    matchFiltersLoading: Boolean,
    matchFiltersError: ApiError?,
    matchFiltersMessage: String?,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
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
    showDraftAfterEditNotice: Boolean = false,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
    onLoadPhotos: () -> Unit,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBackHome: (() -> Unit)? = null,
) {
    val busy = profileUpdateLoading ||
        matchFiltersLoading ||
        photoActionLoading ||
        activationLoading ||
        emailVerificationSending ||
        emailVerificationChecking ||
        accountDeleteLoading
    val scrollState = rememberScrollState()
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
            text = if (onBackHome == null) "Estado de Reals" else "Perfil y filtros",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Gestiona tu perfil, tus fotos y tus preferencias.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (showDraftAfterEditNotice) {
            FeedbackCard(
                title = "Tu perfil volvió a borrador",
                message = "Como modificaste tus fotos, necesitamos que vuelvas a activar el perfil antes de volver al Home.",
                tone = FeedbackTone.Warning,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        when (val snapshot = session.profileSnapshot) {
            ProfileSnapshot.Missing -> MissingProfileCard()
            is ProfileSnapshot.Found -> ProfileCard(
                profile = snapshot.profile,
                profileUpdateLoading = profileUpdateLoading,
                profileUpdateError = profileUpdateError,
                profileUpdateMessage = profileUpdateMessage,
                matchFiltersLoading = matchFiltersLoading,
                matchFiltersError = matchFiltersError,
                matchFiltersMessage = matchFiltersMessage,
                photosLoading = photosLoading,
                photos = photos,
                photosError = photosError,
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
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
                onUpdateMatchFilters = onUpdateMatchFilters,
                onLoadPhotos = onLoadPhotos,
                onAddPhotoFile = onAddPhotoFile,
                onReplacePhotoFile = onReplacePhotoFile,
                onDeletePhoto = onDeletePhoto,
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
                text = "Todavia no tenes perfil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Tu cuenta ya esta autenticada y provisionada. El proximo paso es crear el perfil para completar el onboarding.",
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
    matchFiltersLoading: Boolean,
    matchFiltersError: ApiError?,
    matchFiltersMessage: String?,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
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
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
    onLoadPhotos: () -> Unit,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
) {
    var expandedSection by rememberSaveable(profile.id) {
        mutableStateOf(if (profile.status == ProfileStatus.Draft) ProfileSection.Photos else null)
    }
    val busy = profileUpdateLoading ||
            matchFiltersLoading ||
            photosLoading ||
            photoActionLoading ||
            activationLoading ||
            emailVerificationSending ||
            emailVerificationChecking

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = TextSafety.safeDisplay(profile.displayName, maxLength = 100), style = MaterialTheme.typography.titleLarge)
            Text("Estado: ${profile.status.userLabel()}")
            Text(
                "Edad: ${profile.age}. Ubicacion: ${
                    TextSafety.safeDisplay(profile.city, maxLength = 100)
                }, ${TextSafety.safeDisplay(profile.country, maxLength = 100)}"
            )
            Text("Fotos: ${profile.photoCount}. Identidad verificada: ${yesNo(profile.identityVerified)}")
            Text("Filtros: ${profile.preferredMinAge}-${profile.preferredMaxAge} anos, ${profile.maxDistanceKm} km")
            profile.bio?.takeIf { it.isNotBlank() }?.let {
                Text(TextSafety.safeDisplay(it, maxLength = 1_000))
            }
            Text(
                text = profileNextStep(profile.status),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            ProfileEditActions(
                profile = profile,
                loading = profileUpdateLoading,
                busy = busy,
                error = profileUpdateError,
                message = profileUpdateMessage,
                expanded = expandedSection == ProfileSection.Profile,
                onToggleExpanded = {
                    expandedSection = if (expandedSection == ProfileSection.Profile) null else ProfileSection.Profile
                },
                onUpdateProfile = onUpdateProfile,
            )
            MatchFiltersActions(
                profile = profile,
                loading = matchFiltersLoading,
                busy = busy,
                error = matchFiltersError,
                message = matchFiltersMessage,
                expanded = expandedSection == ProfileSection.Filters,
                onToggleExpanded = {
                    expandedSection = if (expandedSection == ProfileSection.Filters) null else ProfileSection.Filters
                },
                onUpdateMatchFilters = onUpdateMatchFilters,
            )
            PhotoManagerActions(
                profile = profile,
                photosLoading = photosLoading,
                photos = photos,
                photosError = photosError,
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
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
                onActivateProfile = onActivateProfile,
                onResendEmailVerification = onResendEmailVerification,
                onCheckEmailVerification = onCheckEmailVerification,
            )
        }
    }
}

private enum class ProfileSection {
    Profile,
    Filters,
    Photos,
}

@Composable
private fun ProfileEditActions(
    profile: Profile,
    loading: Boolean,
    busy: Boolean,
    error: ApiError?,
    message: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
) {
    var displayName by rememberSaveable(profile.id) { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable(profile.id) { mutableStateOf(profile.bio.orEmpty()) }
    var city by rememberSaveable(profile.id) { mutableStateOf(profile.city) }
    var country by rememberSaveable(profile.id) { mutableStateOf(profile.country) }
    var intention by rememberSaveable(profile.id) { mutableStateOf(profile.intention) }
    var lookingForGender by rememberSaveable(profile.id) { mutableStateOf(profile.lookingForGender) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onToggleExpanded, enabled = !loading && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar edicion de perfil" else "Editar perfil")
        }
        if (expanded) {
            Text(
                text = "Campos editables: nombre, bio, ciudad, pais, intencion y busqueda.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(displayName, { displayName = it.take(100) }, label = { Text("Nombre visible") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(1_000) }, label = { Text("Bio") }, enabled = !loading, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it.take(100) }, label = { Text("Ciudad") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(country, { country = it.take(100) }, label = { Text("Pais") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            EnumDropdown("Intencion", intention, listOf("DATE", "FRIENDSHIP", "CASUAL"), !loading) { intention = it }
            EnumDropdown("Busco", lookingForGender, listOf("MEN", "WOMEN", "EVERYONE", "OTHER"), !loading) { lookingForGender = it }
            localError?.let { ErrorFeedback("Revisa los datos", it) }
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileUpdate) }
            message?.let { SuccessFeedback(it) }
            Button(
                onClick = {
                    val input = validateUpdateProfileInput(displayName, bio, city, country, intention, lookingForGender)
                    if (input == null) {
                        localError = "Revisa nombre, ciudad, pais y bio. No uses etiquetas o formato HTML."
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
}

@Composable
private fun MatchFiltersActions(
    profile: Profile,
    loading: Boolean,
    busy: Boolean,
    error: ApiError?,
    message: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
) {
    var minAge by rememberSaveable(profile.id) { mutableStateOf(profile.preferredMinAge.toString()) }
    var maxAge by rememberSaveable(profile.id) { mutableStateOf(profile.preferredMaxAge.toString()) }
    var distance by rememberSaveable(profile.id) { mutableStateOf(profile.maxDistanceKm.toString()) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onToggleExpanded, enabled = !loading && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar filtros" else "Editar filtros de match")
        }
        if (expanded) {
            Text(
                text = "Estas preferencias nos ayudan a encontrar personas compatibles para vos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(minAge, { minAge = it }, "Edad min", !loading, Modifier.weight(1f))
                NumberField(maxAge, { maxAge = it }, "Edad max", !loading, Modifier.weight(1f))
            }
            NumberField(distance, { distance = it }, "Distancia maxima km", !loading, Modifier.fillMaxWidth())
            localError?.let { ErrorFeedback("Revisa los filtros", it) }
            error?.let { ApiErrorFeedbackCard(it, ErrorContext.MatchFilters) }
            message?.let { SuccessFeedback(it) }
            Button(
                onClick = {
                    val input = validateMatchFiltersInput(minAge, maxAge, distance)
                    if (input == null) {
                        localError = "Edades deben estar entre 18 y 99, min <= max, distancia entre 1 y 1000."
                    } else {
                        localError = null
                        onUpdateMatchFilters(input)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Guardando filtros..." else "Guardar filtros")
            }
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
    onActivateProfile: (Profile) -> Unit,
    onResendEmailVerification: () -> Unit,
    onCheckEmailVerification: () -> Unit,
) {
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var replacePhotoId by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var replacePosition by rememberSaveable(profile.id) { mutableStateOf<Int?>(null) }
    var pendingAddedPosition by rememberSaveable(profile.id) { mutableStateOf<Int?>(null) }
    val addFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val position = pendingAddedPosition
        if (uri != null && position != null) {
            localError = null
            onAddPhotoFile(position, uri)
        } else {
            pendingAddedPosition = null
        }
    }
    val replaceFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val photoId = replacePhotoId
        val position = replacePosition
        replacePhotoId = null
        replacePosition = null
        if (uri != null && photoId != null && position != null) {
            localError = null
            onReplacePhotoFile(photoId, position, uri)
        }
    }

    LaunchedEffect(photoActionLoading, photoActionMessage) {
        if (!photoActionLoading && photoActionMessage != null && pendingAddedPosition != null) {
            pendingAddedPosition = null
        }
        if (!photoActionLoading && photoActionError != null) {
            pendingAddedPosition = null
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onToggleExpanded, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar fotos" else "Administrar fotos")
        }
        if (expanded) {
            Text(
                text = "Subi, reemplaza o borra fotos. Si cambias fotos importantes, puede que tengamos que revisar tu perfil otra vez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            photosError?.let {
                ApiErrorFeedbackCard(it, ErrorContext.PhotoUpload)
                OutlinedButton(onClick = onLoadPhotos, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photosLoading) "Cargando fotos..." else "Reintentar carga de fotos")
                }
            }
            PhotoGrid(
                photos = photos,
                busy = busy,
                onPickNewFile = { position ->
                    localError = null
                    pendingAddedPosition = position
                    addFileLauncher.launch("image/*")
                },
                onPickReplacementFile = { photoId, position ->
                    localError = null
                    replacePhotoId = photoId
                    replacePosition = position
                    replaceFileLauncher.launch("image/*")
                },
                onDeletePhoto = onDeletePhoto,
            )
            localError?.let { ErrorFeedback("Revisa las fotos", it) }
            photoActionMessage?.let { SuccessFeedback(it) }
            photoActionError?.let { ApiErrorFeedbackCard(it, ErrorContext.PhotoUpload) }
            activationError?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileActivation) }
            val showEmailVerificationActions = activationError.isEmailNotVerified() || emailVerificationRequired
            if (showEmailVerificationActions) {
                EmailVerificationActions(
                    sending = emailVerificationSending,
                    checking = emailVerificationChecking,
                    message = emailVerificationMessage,
                    error = emailVerificationError,
                    busy = busy,
                    resendAvailableAtMillis = resendEmailVerificationAvailableAtMillis,
                    checkAvailableAtMillis = checkEmailVerificationAvailableAtMillis,
                    onResendEmailVerification = onResendEmailVerification,
                    onCheckEmailVerification = onCheckEmailVerification,
                )
            }
            if (profile.status == ProfileStatus.Draft) {
                if (!showEmailVerificationActions) {
                    FeedbackCard(
                        title = "Verificación de email",
                        message = "Antes de activar tu perfil, vas a necesitar verificar tu email. " +
                            "Revisá tu bandeja de entrada o spam.",
                        tone = FeedbackTone.Warning,
                    )
                }
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
}

@Composable
private fun EmailVerificationActions(
    sending: Boolean,
    checking: Boolean,
    message: String?,
    error: String?,
    busy: Boolean,
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
            delay((nextAvailableAt - System.currentTimeMillis()).coerceAtLeast(0L))
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
                enabled = !busy && !resendCoolingDown,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sending) "Enviando..." else "Reenviar email")
            }
            Button(
                onClick = onCheckEmailVerification,
                enabled = !busy && !checkCoolingDown,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checking) "Comprobando..." else "Ya verifiqué")
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<ProfilePhoto>,
    busy: Boolean,
    onPickNewFile: (position: Int) -> Unit,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
) {
    val photosByPosition = photos.profilePhotosByGridPosition()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfilePhotoGridPositions.chunked(3).forEach { rowPositions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPositions.forEach { position ->
                    PhotoSlot(
                        position = position,
                        photo = photosByPosition[position],
                        busy = busy,
                        modifier = Modifier.weight(1f),
                        onPickNewFile = onPickNewFile,
                        onPickReplacementFile = onPickReplacementFile,
                        onDeletePhoto = onDeletePhoto,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSlot(
    position: Int,
    photo: ProfilePhoto?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onPickNewFile: (position: Int) -> Unit,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
) {
    if (photo == null) {
        EmptyPhotoSlot(
            position = position,
            busy = busy,
            modifier = modifier,
            onPickNewFile = onPickNewFile,
        )
    } else {
        FilledPhotoSlot(
            photo = photo,
            busy = busy,
            modifier = modifier,
            onPickReplacementFile = onPickReplacementFile,
            onDeletePhoto = onDeletePhoto,
        )
    }
}

@Composable
private fun FilledPhotoSlot(
    photo: ProfilePhoto,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
) {
    val imageShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    val actionShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    val displayUrl = photo.url.toEmulatorReachableUrl()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(imageShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, imageShape),
        ) {
            when {
                photo.url.isLocalhostPresignedUrl() -> {
                    Text(
                        text = "URL local firmada no renderizable en emulador.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                displayUrl.isRenderableImageUrl() -> {
                    AsyncImage(
                        model = displayUrl,
                        contentDescription = "Foto de perfil ${photo.position}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    Text(
                        text = "Sin URL publica.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                text = "Foto ${photo.position}",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable(
                        enabled = !busy,
                        onClickLabel = "Borrar foto ${photo.position}",
                    ) { onDeletePhoto(photo.id, photo.position) }
                    .semantics { contentDescription = "Borrar foto ${photo.position}" },
            ) {
                Text(
                    text = "x",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
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
                .semantics { contentDescription = "Reemplazar foto ${photo.position}" },
        ) {
            Text(
                text = "Cambiar",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun EmptyPhotoSlot(
    position: Int,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onPickNewFile: (position: Int) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(
                enabled = !busy,
                onClickLabel = "Agregar foto $position",
            ) { onPickNewFile(position) }
            .semantics { contentDescription = "Agregar foto $position" },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Agregar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Foto $position",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)),
            )
        }
    }
}

internal val ProfilePhotoGridPositions: IntRange = 1..9

internal fun List<ProfilePhoto>.profilePhotosByGridPosition(): Map<Int, ProfilePhoto> =
    filter { it.position in ProfilePhotoGridPositions }
        .associateBy { it.position }

private fun String.toEmulatorReachableUrl(): String {
    if (isPresignedUrl()) return this
    return replace("http://localhost:", "http://10.0.2.2:")
        .replace("http://127.0.0.1:", "http://10.0.2.2:")
}

private fun String.isRenderableImageUrl(): Boolean {
    return startsWith("http://") || startsWith("https://")
}

private fun String.isPresignedUrl(): Boolean {
    return contains("X-Amz-Signature=")
}

private fun String.isLocalhostPresignedUrl(): Boolean {
    return isPresignedUrl() &&
        (startsWith("http://localhost:") || startsWith("http://127.0.0.1:"))
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
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

@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable(label, value) { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

private fun validateUpdateProfileInput(
    displayName: String,
    bio: String,
    city: String,
    country: String,
    intention: String,
    lookingForGender: String,
): UpdateProfileInput? {
    val cleanDisplayName = TextSafety.normalizeSingleLine(displayName, maxLength = 100)
    val cleanBio = TextSafety.normalizeMultiline(bio, maxLength = 1_000)
    val cleanCity = TextSafety.normalizeSingleLine(city, maxLength = 100)
    val cleanCountry = TextSafety.normalizeSingleLine(country, maxLength = 100)

    if (cleanDisplayName.length !in 2..100) return null
    if (cleanBio.length > 1000) return null
    if (cleanCity.isBlank() || cleanCity.length > 100) return null
    if (cleanCountry.isBlank() || cleanCountry.length > 100) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanDisplayName)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanBio)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanCity)) return null
    if (TextSafety.containsHtmlLikeMarkup(cleanCountry)) return null
    if (intention !in listOf("DATE", "FRIENDSHIP", "CASUAL")) return null
    if (lookingForGender !in listOf("MEN", "WOMEN", "EVERYONE", "OTHER")) return null

    return UpdateProfileInput(
        displayName = cleanDisplayName,
        bio = cleanBio.ifBlank { null },
        city = cleanCity,
        country = cleanCountry,
        intention = intention,
        lookingForGender = lookingForGender,
    )
}

private fun validateMatchFiltersInput(
    minAge: String,
    maxAge: String,
    distance: String,
): UpdateMatchFiltersInput? {
    val parsedMin = minAge.toIntOrNull()
    val parsedMax = maxAge.toIntOrNull()
    val parsedDistance = distance.toIntOrNull()
    if (parsedMin == null || parsedMax == null || parsedDistance == null) return null
    if (parsedMin !in 18..99 || parsedMax !in 18..99 || parsedMin > parsedMax) return null
    if (parsedDistance !in 1..1000) return null
    return UpdateMatchFiltersInput(
        preferredMinAge = parsedMin,
        preferredMaxAge = parsedMax,
        maxDistanceKm = parsedDistance,
    )
}

private fun yesNo(value: Boolean): String = if (value) "si" else "no"

private fun ApiError?.isEmailNotVerified(): Boolean =
    this is ApiError.Backend &&
        backendErrorCode == BackendErrorCode.EmailNotVerified
