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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun PhotosCard(
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
    val photoInteractionState = rememberProfilePhotoInteractionState(profile.id)
    val visiblePhotoAction = photoInteractionState.visibleAction(photoActionLoading)
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val cropTransaction = photoInteractionState.cropTransaction
    val cropRequest = remember(cropTransaction) {
        cropTransaction?.let { ProfilePhotoCropRequest(Uri.parse(it.sourceUriString), it.target) }
    }
    fun deleteLocalPreviewFile(uriString: String?) {
        uriString?.let {
            deleteOwnedProfilePhotoCropFile(Uri.parse(it), profilePhotoCropCacheDirectory(context))
        }
    }
    fun logPhotoInteractionTiming(timing: ProfilePhotoInteractionTiming) {
        ProfilePhotoPipelineTiming.log(
            ProfilePhotoTimingFields(
                phase = timing.phase,
                durationMs = timing.durationMs,
            ),
        )
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photoInteractionState.onPickerResult(uri?.toString())
    }
    LaunchedEffect(Unit) {
        deleteStaleProfilePhotoCropFiles(
            cacheDir = profilePhotoCropCacheDirectory(context),
            nowMillis = System.currentTimeMillis(),
            maxAgeMillis = 24.hours.inWholeMilliseconds,
        )
    }
    LaunchedEffect(photoActionLoading, photoActionMessage, photoActionError, photos) {
        if (photoActionLoading) return@LaunchedEffect
        when {
            photoActionError != null -> {
                val transition = photoInteractionState.onMatchingUploadFailed(
                    responseAtElapsedMillis = ProfilePhotoPipelineTiming.nowMillis(),
                ) ?: return@LaunchedEffect
                logPhotoInteractionTiming(transition.timing)
                deleteLocalPreviewFile(transition.cleanupUriString)
            }
            photoActionMessage != null -> {
                when (
                    val transition = photoInteractionState.prepareMatchingUploadSucceeded(
                        photos = photos,
                        uploadResponseAtElapsedMillis = ProfilePhotoPipelineTiming.nowMillis(),
                    ) ?: return@LaunchedEffect
                ) {
                    is ProfilePhotoUploadSuccessTransition.Applied -> {
                        logPhotoInteractionTiming(transition.timing)
                        deleteLocalPreviewFile(transition.cleanupUriString)
                    }
                    is ProfilePhotoUploadSuccessTransition.AwaitingRemote -> {
                        logPhotoInteractionTiming(transition.timing)
                        when (val decision = transition.prepared.cacheDecision) {
                            is ProfilePhotoCacheRefreshDecision.Evict -> {
                                imageLoader.memoryCache?.remove(MemoryCache.Key(decision.canonicalCacheKey))
                                withContext(Dispatchers.IO) {
                                    imageLoader.diskCache?.remove(decision.canonicalCacheKey)
                                }
                            }
                            ProfilePhotoCacheRefreshDecision.None -> Unit
                        }
                        deleteLocalPreviewFile(
                            photoInteractionState.commitPreparedUploadSuccess(transition.prepared),
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(photoInteractionState.previewState) {
        deleteLocalPreviewFile(photoInteractionState.clearOrphanedPreview())
    }
    LaunchedEffect(photoActionLoading, photoActionMessage, photoActionError) {
        photoInteractionState.clearCompletedActionIfTerminal(
            photoActionLoading = photoActionLoading,
            photoActionMessage = photoActionMessage,
            photoActionErrorPresent = photoActionError != null,
        )
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
            } else if (photoActionError != null || photoActionMessage != null) {
                ProfilePhotoActionFeedback(
                    photoActionLoading = false,
                    photoActionError = photoActionError,
                    photoActionMessage = photoActionMessage,
                )
            }
            PhotoGrid(
                photos = photos,
                busy = busy,
                pendingAction = visiblePhotoAction,
                previewState = photoInteractionState.previewState,
                imageLoader = imageLoader,
                onRemotePreviewDisplayed = { remotePhotoId, generation ->
                    val transition = photoInteractionState.onRemotePreviewDisplayed(
                        remotePhotoId = remotePhotoId,
                        generation = generation,
                        displayedAtElapsedMillis = ProfilePhotoPipelineTiming.nowMillis(),
                    ) ?: return@PhotoGrid
                    logPhotoInteractionTiming(transition.timing)
                    deleteLocalPreviewFile(transition.cleanupUriString)
                },
                onPickNewFile = { position ->
                    photoInteractionState.startAddSelection(position)
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onPickReplacementFile = { photoId, position ->
                    photoInteractionState.startReplacementSelection(photoId, position)
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onDeletePhoto = { photoId, position ->
                    deleteLocalPreviewFile(photoInteractionState.startDelete(photoId, position))
                    onDeletePhoto(photoId, position)
                },
                onMovePhoto = { photoId, targetPosition ->
                    deleteLocalPreviewFile(photoInteractionState.startMove())
                    onMovePhoto(photoId, targetPosition)
                },
            )
            cropRequest?.let { request ->
                ProfilePhotoCropDialog(
                    request = request,
                    onCancel = {
                        photoInteractionState.cancelCrop()
                    },
                    onCropped = { croppedUri ->
                        val confirmation = photoInteractionState.confirmCrop(
                            croppedUriString = croppedUri.toString(),
                            photos = photos,
                            cropConfirmedAtElapsedMillis = ProfilePhotoPipelineTiming.nowMillis(),
                        ) ?: return@ProfilePhotoCropDialog
                        deleteLocalPreviewFile(confirmation.cleanupUriString)
                        dispatchCroppedProfilePhoto(
                            target = confirmation.target,
                            croppedUri = croppedUri,
                            onAddPhotoFile = onAddPhotoFile,
                            onReplacePhotoFile = onReplacePhotoFile,
                        )
                    },
                )
            }
            photoInteractionState.localError?.let { ErrorFeedback("Revisá las fotos", it) }
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
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = action.progressMessage(),
                    modifier = Modifier.testTag(ProfilePhotoActionProgressMessageTag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    previewState: ProfilePhotoPreviewState = ProfilePhotoPreviewState.None,
    imageLoader: coil3.ImageLoader? = null,
    onRemotePreviewDisplayed: (photoId: String, generation: String) -> Unit = { _, _ -> },
    onPickNewFile: (position: Int) -> Unit,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onMovePhoto: (photoId: String, targetPosition: Int) -> Unit,
) {
    val resolvedImageLoader = imageLoader ?: SingletonImageLoader.get(LocalContext.current)
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
                            pendingPreview = previewState.previewForPosition(position),
                            awaitingRemote = photosByPosition[position]?.let { previewState.awaitingRemoteForPhoto(it.id) },
                            imageLoader = resolvedImageLoader,
                            isDragTarget = currentDragState?.targetPosition == position,
                            isDraggingSource = currentDragState?.sourcePosition == position,
                            modifier = Modifier.weight(1f),
                            onSlotBoundsChanged = { slotPosition, bounds ->
                                slotBoundsByPosition[slotPosition] = bounds
                            },
                        onPickNewFile = onPickNewFile,
                        onPickReplacementFile = onPickReplacementFile,
                        onDeletePhoto = onDeletePhoto,
                        onRemotePreviewDisplayed = onRemotePreviewDisplayed,
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
    pendingPreview: PendingProfilePhotoPreview? = null,
    awaitingRemote: ProfilePhotoPreviewState.AwaitingRemote? = null,
    imageLoader: coil3.ImageLoader? = null,
    isDragTarget: Boolean,
    isDraggingSource: Boolean,
    modifier: Modifier = Modifier,
    onSlotBoundsChanged: (position: Int, bounds: Rect) -> Unit,
    onPickNewFile: (position: Int) -> Unit,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onRemotePreviewDisplayed: (photoId: String, generation: String) -> Unit,
    onDragStart: (photoId: String, sourcePosition: Int, pointerPosition: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val resolvedImageLoader = imageLoader ?: SingletonImageLoader.get(LocalContext.current)
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
            pendingPreview = pendingPreview,
            imageLoader = resolvedImageLoader,
            isDragTarget = isDragTarget,
            modifier = positionedModifier,
            onPickNewFile = onPickNewFile,
        )
    } else {
        FilledPhotoSlot(
            photo = photo,
            busy = busy,
            pendingAction = pendingAction,
            pendingPreview = pendingPreview,
            awaitingRemote = awaitingRemote,
            imageLoader = resolvedImageLoader,
            isDragTarget = isDragTarget,
            isDraggingSource = isDraggingSource,
            slotBounds = slotBounds,
            modifier = positionedModifier,
            onPickReplacementFile = onPickReplacementFile,
            onDeletePhoto = onDeletePhoto,
            onRemotePreviewDisplayed = onRemotePreviewDisplayed,
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
    pendingPreview: PendingProfilePhotoPreview? = null,
    awaitingRemote: ProfilePhotoPreviewState.AwaitingRemote? = null,
    imageLoader: coil3.ImageLoader,
    isDragTarget: Boolean,
    isDraggingSource: Boolean,
    slotBounds: Rect?,
    modifier: Modifier = Modifier,
    onPickReplacementFile: (photoId: String, position: Int) -> Unit,
    onDeletePhoto: (photoId: String, position: Int) -> Unit,
    onRemotePreviewDisplayed: (photoId: String, generation: String) -> Unit,
    onDragStart: (photoId: String, sourcePosition: Int, pointerPosition: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val imageShape = RoundedCornerShape(topStart = RealsRadii.Row, topEnd = RealsRadii.Row)
    val actionShape = RoundedCornerShape(bottomStart = RealsRadii.Row, bottomEnd = RealsRadii.Row)
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
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (isDragTarget) 2.dp else 1.dp,
                    color = if (isDragTarget) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = imageShape,
                ),
        ) {
            ProfilePhotoImage(
                photo = photo,
                contentDescription = "Foto de perfil ${photo.position}",
                imageLoader = imageLoader,
                remoteHandoffGeneration = awaitingRemote?.preview?.generation,
                onRemoteHandoffSuccess = onRemotePreviewDisplayed,
                modifier = Modifier
                    .fillMaxSize()
                    .then(dragModifier),
            )
            pendingPreview?.let {
                LocalProfilePhotoPreviewImage(
                    preview = it,
                    contentDescription = "Foto de perfil ${photo.position}",
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(profilePhotoLocalPreviewTag(photo.position)),
                )
            }
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
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
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
    pendingPreview: PendingProfilePhotoPreview? = null,
    imageLoader: coil3.ImageLoader,
    isDragTarget: Boolean,
    modifier: Modifier = Modifier,
    onPickNewFile: (position: Int) -> Unit,
) {
    val shape = RoundedCornerShape(RealsRadii.Row)
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
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isDragTarget) 2.dp else 1.dp,
                color = if (isDragTarget) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
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
        if (pendingPreview != null) {
            LocalProfilePhotoPreviewImage(
                preview = pendingPreview,
                contentDescription = "Foto de perfil $position",
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(profilePhotoLocalPreviewTag(position)),
            )
        } else {
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
    val shape = RoundedCornerShape(RealsRadii.Row)
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
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape),
    ) {
        ProfilePhotoImage(
            photo = photo,
            contentDescription = null,
            imageLoader = SingletonImageLoader.get(LocalContext.current),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun LocalProfilePhotoPreviewImage(
    preview: PendingProfilePhotoPreview,
    contentDescription: String?,
    imageLoader: coil3.ImageLoader,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewUri = remember(preview.uriString) { Uri.parse(preview.uriString) }
    val imageRequest = remember(context, preview.uriString, preview.generation) {
        ImageRequest.Builder(context)
            .data(previewUri)
            .memoryCacheKey("profile-photo-preview-${preview.generation}")
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
internal fun ProfilePhotoImage(
    photo: ProfilePhoto,
    contentDescription: String?,
    imageLoader: coil3.ImageLoader,
    remoteHandoffGeneration: String? = null,
    onRemoteHandoffSuccess: (photoId: String, generation: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val displayUrl = photo.url.toEmulatorReachableUrl()
    val context = LocalContext.current
    val imageRequest = remember(context, displayUrl, remoteHandoffGeneration) {
        ImageRequest.Builder(context)
            .data(displayUrl)
            .memoryCacheKey(displayUrl.stableProfilePhotoCacheKey())
            .diskCacheKey(displayUrl.stableProfilePhotoCacheKey())
            .listener(
                onSuccess = { _, _ ->
                    remoteHandoffGeneration?.let { onRemoteHandoffSuccess(photo.id, it) }
                },
            )
            .build()
    }
    when {
        displayUrl.isRenderableImageUrl() -> {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                imageLoader = imageLoader,
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
internal fun profilePhotoLocalPreviewTag(position: Int): String = "profile_photo_local_preview_$position"
internal fun profilePhotoDeleteTag(position: Int): String = "profile_photo_delete_$position"
internal fun profilePhotoDeleteVisualTag(position: Int): String = "profile_photo_delete_visual_$position"
internal fun profilePhotoReplaceTag(position: Int): String = "profile_photo_replace_$position"
internal fun profilePhotoAddTag(position: Int): String = "profile_photo_add_$position"
internal fun profilePhotoAddPlusTag(position: Int): String = "profile_photo_add_plus_$position"
internal fun profilePhotoAddLabelTag(position: Int): String = "profile_photo_add_label_$position"

internal fun List<ProfilePhoto>.profilePhotosByGridPosition(): Map<Int, ProfilePhoto> =
    filter { it.position in ProfilePhotoGridPositions }
        .associateBy { it.position }

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

internal fun String.isRenderableImageUrl(): Boolean {
    return startsWith("http://") || startsWith("https://")
}

private fun String.isPresignedUrl(): Boolean {
    return contains("X-Amz-Signature=")
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
