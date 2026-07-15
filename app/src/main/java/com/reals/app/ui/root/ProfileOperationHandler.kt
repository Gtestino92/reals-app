package com.reals.app.ui.root

import android.net.Uri
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.ui.profile.movePhotoLocally
import com.reals.app.ui.profile.photosWithPendingOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Extracts profile and photo management operations from [RealsRootViewModel].
 *
 * Reads the current [RealsRootUiState.Ready] from the shared state flow, performs
 * the operation, and writes the updated state back. This keeps the profile logic
 * isolated in a single place.
 *
 * @param uiState the shared mutable state flow that [RealsRootViewModel] owns
 * @param dependencies profile feature use cases
 * @param getProfilePhotosUseCase retrieves profile photos
 * @param scope coroutine scope (typically [androidx.lifecycle.viewModelScope])
 */
class ProfileOperationHandler(
    private val uiState: MutableStateFlow<RealsRootUiState>,
    private val dependencies: ProfileFeatureDependencies,
    private val authRepository: FirebaseAuthRepository,
    private val getProfilePhotosUseCase: GetProfilePhotosUseCase,
    private val scope: CoroutineScope,
    private val onTerminalAuthFailure: () -> Unit,
) {
    /**
     * Called when [RealsRootUiState.Ready] is updated from elsewhere (e.g. after home load).
     * The handler already reads from the flow directly so this is optional – kept as a
     * hook for any future synchronisation needs.
     */
    fun onReadyStateUpdated() = Unit

    // ── Profile CRUD ──

    fun createProfile(input: CreateProfileInput) {
        val current = requireReady() ?: return
        scope.launch {
            val pending = current.copy(
                profileOp = current.profileOp.copy(
                    creatingProfile = true,
                    profileCreateError = null,
                ),
            )
            uiState.value = pending
            when (val result = dependencies.createProfile(input)) {
                is ApiResult.Success -> {
                    uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profileOp = pending.profileOp.copy(
                            creatingProfile = false,
                            profileCreateError = null,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        profileOp = pending.profileOp.copy(
                            creatingProfile = false,
                            profileCreateError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun updateProfile(input: UpdateProfileInput) {
        val current = requireReady() ?: return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(profileOp = cleared.profileOp.copy(updatingProfile = true))
            uiState.value = pending
            when (val result = dependencies.updateProfile(input)) {
                is ApiResult.Success -> {
                    uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profileOp = pending.profileOp.copy(
                            updatingProfile = false,
                            profileUpdateMessage = "Perfil actualizado.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        profileOp = pending.profileOp.copy(
                            updatingProfile = false,
                            profileUpdateError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun loadCountriesIfNeeded() {
        val current = requireReady() ?: return
        if (current.countriesLoaded || current.countriesLoading) return

        scope.launch {
            val latest = requireReady() ?: return@launch
            if (latest.countriesLoaded || latest.countriesLoading) return@launch

            val pending = latest.copy(
                profileOp = latest.profileOp.copy(
                    countriesLoading = true,
                    countriesError = null,
                ),
            )
            uiState.value = pending

            when (val result = dependencies.getCountries()) {
                is ApiResult.Success -> {
                    val displayed = requireReady() ?: return@launch
                    uiState.value = displayed.copy(
                        profileOp = displayed.profileOp.copy(
                            countriesLoading = false,
                            countries = result.value,
                            countriesError = null,
                            countriesLoaded = true,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    val displayed = requireReady() ?: return@launch
                    uiState.value = displayed.copy(
                        profileOp = displayed.profileOp.copy(
                            countriesLoading = false,
                            countriesError = result.error,
                            countriesLoaded = false,
                        ),
                    )
                }
            }
        }
    }

    fun updateMatchFilters(input: UpdateMatchFiltersInput) {
        val current = requireReady() ?: return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                profileOp = cleared.profileOp.copy(updatingMatchFilters = true),
            )
            uiState.value = pending
            when (val result = dependencies.updateMatchFilters(input)) {
                is ApiResult.Success -> {
                    uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profileOp = pending.profileOp.copy(
                            updatingMatchFilters = false,
                            matchFiltersMessage = "Filtros actualizados.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        profileOp = pending.profileOp.copy(
                            updatingMatchFilters = false,
                            matchFiltersError = result.error,
                        ),
                    )
                }
            }
        }
    }

    // ── Photos ──

    fun loadProfilePhotos() {
        val current = requireReady() ?: return
        if (current.session.profileSnapshot !is ProfileSnapshot.Found) return
        if (current.reorderingPhotos) return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(loadingPhotos = true))
            uiState.value = pending
            when (val result = getProfilePhotosUseCase()) {
                is ApiResult.Success -> {
                    val latest = requireReady() ?: return@launch
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(
                            loadingPhotos = false,
                            profilePhotos = result.value.sortedBy { it.position },
                            profilePhotosError = null,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    val latest = requireReady() ?: return@launch
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(
                            loadingPhotos = false,
                            profilePhotosError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun moveProfilePhoto(
        photoId: String,
        targetPosition: Int,
    ) {
        val current = requireReady() ?: return
        if (current.reorderingPhotos) return

        val previousPhotos = current.profilePhotos.sortedBy { it.position }
        val newPlacements = movePhotoLocally(
            photos = previousPhotos,
            pendingOrder = null,
            photoId = photoId,
            targetPosition = targetPosition,
        )
        val optimisticPhotos = photosWithPendingOrder(
            photos = previousPhotos,
            pendingOrder = newPlacements,
        ).sortedBy { it.position }
        val previousOrder = previousPhotos.map { it.id to it.position }
        val nextOrder = optimisticPhotos.map { it.id to it.position }
        if (previousOrder == nextOrder) return

        uiState.value = current.copy(
            photos = current.photos.copy(
                profilePhotos = optimisticPhotos,
                reorderingPhotos = true,
                photoReorderError = null,
                photoReorderMessage = null,
                photoActionError = null,
                photoActionMessage = null,
            ),
        )

        scope.launch {
            when (val result = dependencies.reorderProfilePhotos(newPlacements)) {
                is ApiResult.Success -> {
                    val latest = requireReady() ?: return@launch
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(
                            profilePhotos = result.value.sortedBy { it.position },
                            reorderingPhotos = false,
                            photoReorderError = null,
                            photoReorderMessage = null,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    val latest = requireReady() ?: return@launch
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(
                            profilePhotos = previousPhotos,
                            reorderingPhotos = false,
                            photoReorderError = result.error,
                            photoReorderMessage = null,
                        ),
                    )
                }
            }
        }
    }

    fun addProfilePhotoFile(position: Int, fileUri: Uri?) {
        val current = requireReady() ?: return
        if (current.reorderingPhotos) return
        if (fileUri == null) return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
            uiState.value = pending
            when (val result = dependencies.addProfilePhotoFile(fileUri, position)) {
                is ApiResult.Success -> completePhotoAdded(
                    previous = pending,
                    addedPhoto = result.value,
                    successMessage = "Foto subida correctamente.",
                )

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        photos = pending.photos.copy(addingPhoto = false, photoActionError = result.error),
                    )
                }
            }
        }
    }

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri?) {
        val current = requireReady() ?: return
        if (current.reorderingPhotos) return
        if (fileUri == null) return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
            uiState.value = pending
            when (val result = dependencies.replaceProfilePhotoFile(photoId, fileUri)) {
                is ApiResult.Success -> completePhotoReplaced(
                    previous = pending,
                    replacedPhoto = result.value,
                    successMessage = "Foto reemplazada correctamente.",
                )

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        photos = pending.photos.copy(addingPhoto = false, photoActionError = result.error),
                    )
                }
            }
        }
    }

    fun deleteProfilePhoto(photoId: String, position: Int) {
        val current = requireReady() ?: return
        if (current.reorderingPhotos) return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
            uiState.value = pending
            when (val result = dependencies.deleteProfilePhoto(photoId)) {
                is ApiResult.Success -> completePhotoDeleted(
                    previous = pending,
                    deletedPhotoId = photoId,
                    updatedProfile = result.value,
                    successMessage = "Foto eliminada.",
                )

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        photos = pending.photos.copy(addingPhoto = false, photoActionError = result.error),
                    )
                }
            }
        }
    }

    // ── Activation ──

    fun activateProfile() {
        val current = requireReady() ?: return
        if (current.reorderingPhotos) return

        if (current.emailVerificationRequired && !current.emailVerificationLocallyVerified) {
            return
        }

        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                profileOp = cleared.profileOp.copy(
                    activatingProfile = true,
                ),
            )
            uiState.value = pending

            val verificationResult =
                if (current.emailVerificationLocallyVerified) {
                    EmailVerificationCheckResult.Verified
                } else {
                    authRepository.reloadAndRefreshEmailVerification()
                }

            val verifiedPending = when (verificationResult) {
                EmailVerificationCheckResult.Verified -> {
                    pending.copy(
                        profileOp = pending.profileOp.copy(
                            emailVerificationRequired = false,
                            emailVerificationLocallyVerified = true,
                            emailVerificationMessage = null,
                            emailVerificationError = null,
                            profileActivationError = null,
                        ),
                    )
                }

                EmailVerificationCheckResult.NotVerified -> {
                    uiState.value = pending.copy(
                        profileOp = pending.profileOp.copy(
                            activatingProfile = false,
                            profileActivationError = null,
                            emailVerificationRequired = true,
                            emailVerificationLocallyVerified = false,
                            emailVerificationMessage = null,
                            emailVerificationError = "Todavía no vemos el email verificado. Abrí el link del correo y volvé a intentar.",
                            checkEmailVerificationAvailableAtMillis =
                                System.currentTimeMillis() + CHECK_EMAIL_VERIFICATION_COOLDOWN_MILLIS,
                        ),
                    )
                    return@launch
                }

                EmailVerificationCheckResult.NotSignedIn -> {
                    onTerminalAuthFailure()
                    return@launch
                }

                EmailVerificationCheckResult.Failure -> {
                    uiState.value = pending.copy(
                        profileOp = pending.profileOp.copy(
                            activatingProfile = false,
                            emailVerificationMessage = null,
                            emailVerificationError = "No pudimos comprobár la verificación. Intent nuevamente.",
                        ),
                    )
                    return@launch
                }
            }

            uiState.value = verifiedPending

            when (val result = dependencies.activateProfile()) {
                is ApiResult.Success -> {
                    val updatedSession = verifiedPending.session.copy(
                        profileSnapshot = ProfileSnapshot.Found(result.value.profile),
                    )

                    uiState.value = RealsRootUiState.ActivationComplete(
                        session = updatedSession,
                        result = result.value,
                    )
                }

                is ApiResult.Failure -> {
                    val emailNotVerified = result.error.isEmailNotVerified()

                    uiState.value = verifiedPending.copy(
                        profileOp = verifiedPending.profileOp.copy(
                            activatingProfile = false,
                            profileActivationError = result.error,
                            emailVerificationRequired = emailNotVerified,
                            emailVerificationLocallyVerified =
                                if (emailNotVerified) false else verifiedPending.emailVerificationLocallyVerified,
                            emailVerificationMessage = null,
                            emailVerificationError = null,
                        ),
                    )
                }
            }
        }
    }

    // ── Private helpers ──

    fun resendEmailVerification() {
        val current = requireReady() ?: return
        if (current.isEmailVerificationActionBusy()) return
        val now = System.currentTimeMillis()
        if (current.resendEmailVerificationAvailableAtMillis.isInFuture(now)) {
            uiState.value = current.copy(
                profileOp = current.profileOp.copy(
                    emailVerificationMessage = "Podés pedir otro correo en unos segundos.",
                    emailVerificationError = null,
                ),
            )
            return
        }

        scope.launch {
            val pending = current.copy(
                profileOp = current.profileOp.copy(
                    sendingEmailVerification = true,
                    emailVerificationMessage = null,
                    emailVerificationError = null,
                ),
            )
            uiState.value = pending
            val result = authRepository.sendEmailVerificationEmail()
            if (result == EmailVerificationSendResult.NotSignedIn) {
                onTerminalAuthFailure()
                return@launch
            }
            val feedback = when (result) {
                EmailVerificationSendResult.Sent -> EmailVerificationFeedback(
                    resendAvailableAtMillis = System.currentTimeMillis() + RESEND_EMAIL_VERIFICATION_COOLDOWN_MILLIS,
                    message = "Te enviamos un nuevo correo de verificación.",
                )

                EmailVerificationSendResult.AlreadyVerified -> EmailVerificationFeedback(
                    emailVerificationRequired = false,
                    emailVerificationLocallyVerified = true,
                    message = "Email verificado. Ya podés activar tu perfil.",
                )

                EmailVerificationSendResult.NotSignedIn -> error("Handled above")

                EmailVerificationSendResult.Failure -> EmailVerificationFeedback(
                    error = "No pudimos enviar el correo de verificación. Intent nuevamente.",
                )
            }
            uiState.value = pending.copy(
                profileOp = pending.profileOp.copy(
                    sendingEmailVerification = false,
                    emailVerificationMessage = feedback.message,
                    emailVerificationError = feedback.error,
                    emailVerificationRequired = feedback.emailVerificationRequired
                        ?: pending.emailVerificationRequired,
                    emailVerificationLocallyVerified = feedback.emailVerificationLocallyVerified
                        ?: pending.emailVerificationLocallyVerified,
                    resendEmailVerificationAvailableAtMillis = feedback.resendAvailableAtMillis
                        ?: pending.resendEmailVerificationAvailableAtMillis,
                ),
            )
        }
    }

    fun checkEmailVerification() {
        val current = requireReady() ?: return
        if (current.isEmailVerificationActionBusy()) return
        val now = System.currentTimeMillis()
        if (current.checkEmailVerificationAvailableAtMillis.isInFuture(now)) return

        scope.launch {
            val pending = current.copy(
                profileOp = current.profileOp.copy(
                    checkingEmailVerification = true,
                    emailVerificationMessage = null,
                    emailVerificationError = null,
                ),
            )
            uiState.value = pending
            val result = authRepository.reloadAndRefreshEmailVerification()
            if (result == EmailVerificationCheckResult.NotSignedIn) {
                onTerminalAuthFailure()
                return@launch
            }
            val feedback = when (result) {
                EmailVerificationCheckResult.Verified -> EmailVerificationFeedback(
                    emailVerificationRequired = false,
                    emailVerificationLocallyVerified = true,
                    checkAvailableAtMillis = 0L,
                    message = "Email verificado. Ya podés activar tu perfil.",
                )

                EmailVerificationCheckResult.NotVerified -> EmailVerificationFeedback(
                    emailVerificationRequired = true,
                    emailVerificationLocallyVerified = false,
                    checkAvailableAtMillis = System.currentTimeMillis() + CHECK_EMAIL_VERIFICATION_COOLDOWN_MILLIS,
                    error = "Todavía no vemos el email verificado. Abrí el link del correo y volvé a intentar.",
                )

                EmailVerificationCheckResult.NotSignedIn -> error("Handled above")

                EmailVerificationCheckResult.Failure -> EmailVerificationFeedback(
                    error = "No pudimos comprobár la verificación. Intent nuevamente.",
                )
            }
            uiState.value = pending.copy(
                profileOp = pending.profileOp.copy(
                    checkingEmailVerification = false,
                    emailVerificationMessage = feedback.message,
                    emailVerificationError = feedback.error,
                    profileActivationError = if (
                        feedback.emailVerificationLocallyVerified == true
                    ) null else pending.profileOp.profileActivationError,
                    emailVerificationRequired = feedback.emailVerificationRequired
                        ?: pending.emailVerificationRequired,
                    emailVerificationLocallyVerified = feedback.emailVerificationLocallyVerified
                        ?: pending.emailVerificationLocallyVerified,
                    checkEmailVerificationAvailableAtMillis = feedback.checkAvailableAtMillis
                        ?: pending.checkEmailVerificationAvailableAtMillis,
                ),
            )
        }
    }

    private fun requireReady(): RealsRootUiState.Ready? =
        uiState.value as? RealsRootUiState.Ready

    private fun completePhotoAdded(
        previous: RealsRootUiState.Ready,
        addedPhoto: ProfilePhoto,
        successMessage: String,
    ) {
        uiState.value = photoAddedState(previous, addedPhoto, successMessage)
    }

    private fun completePhotoReplaced(
        previous: RealsRootUiState.Ready,
        replacedPhoto: ProfilePhoto,
        successMessage: String,
    ) {
        uiState.value = photoReplacedState(previous, replacedPhoto, successMessage)
    }

    private fun completePhotoDeleted(
        previous: RealsRootUiState.Ready,
        deletedPhotoId: String,
        updatedProfile: Profile,
        successMessage: String,
    ) {
        uiState.value = photoDeletedState(previous, deletedPhotoId, updatedProfile, successMessage)
    }
}

private data class EmailVerificationFeedback(
    val message: String? = null,
    val error: String? = null,
    val emailVerificationRequired: Boolean? = null,
    val emailVerificationLocallyVerified: Boolean? = null,
    val resendAvailableAtMillis: Long? = null,
    val checkAvailableAtMillis: Long? = null,
)

private const val CHECK_EMAIL_VERIFICATION_COOLDOWN_MILLIS = 10_000L
private const val RESEND_EMAIL_VERIFICATION_COOLDOWN_MILLIS = 60_000L

private fun Long?.isInFuture(nowMillis: Long): Boolean = this != null && nowMillis < this

private fun ApiError.isEmailNotVerified(): Boolean =
    this is ApiError.Backend &&
        backendErrorCode == BackendErrorCode.EmailNotVerified

private fun RealsRootUiState.Ready.isEmailVerificationActionBusy(): Boolean =
    updatingProfile ||
        updatingMatchFilters ||
        loadingPhotos ||
        addingPhoto ||
        reorderingPhotos ||
        activatingProfile ||
        sendingEmailVerification ||
        checkingEmailVerification

internal fun photoAddedState(
    previous: RealsRootUiState.Ready,
    addedPhoto: ProfilePhoto,
    successMessage: String,
): RealsRootUiState.Ready {
    val isNewPhoto = previous.profilePhotos.none {
        it.id == addedPhoto.id || it.position == addedPhoto.position
    }
    return previous.copy(
        session = markProfileDraftAfterPhotoMutation(
            session = previous.session,
            photoCountDelta = if (isNewPhoto) 1 else 0,
        ),
        photos = previous.photos.copy(
            profilePhotos = upsertPhoto(previous.profilePhotos, addedPhoto),
            profilePhotosError = null,
            addingPhoto = false,
            reorderingPhotos = false,
            photoReorderError = null,
            photoReorderMessage = null,
            photoActionMessage = successMessage,
            photoActionError = null,
        ),
    )
}

internal fun photoReplacedState(
    previous: RealsRootUiState.Ready,
    replacedPhoto: ProfilePhoto,
    successMessage: String,
): RealsRootUiState.Ready = previous.copy(
    session = markProfileDraftAfterPhotoMutation(previous.session),
    photos = previous.photos.copy(
        profilePhotos = replacePhoto(previous.profilePhotos, replacedPhoto),
        profilePhotosError = null,
        addingPhoto = false,
        reorderingPhotos = false,
        photoReorderError = null,
        photoReorderMessage = null,
        photoActionMessage = successMessage,
        photoActionError = null,
    ),
)

internal fun photoDeletedState(
    previous: RealsRootUiState.Ready,
    deletedPhotoId: String,
    updatedProfile: Profile,
    successMessage: String,
): RealsRootUiState.Ready = previous.copy(
    session = previous.session.copy(
        profileSnapshot = ProfileSnapshot.Found(updatedProfile),
    ),
    photos = previous.photos.copy(
        profilePhotos = previous.profilePhotos
            .filterNot { it.id == deletedPhotoId }
            .sortedBy { it.position },
        profilePhotosError = null,
        addingPhoto = false,
        reorderingPhotos = false,
        photoReorderError = null,
        photoReorderMessage = null,
        photoActionMessage = successMessage,
        photoActionError = null,
    ),
)

internal fun upsertPhoto(photos: List<ProfilePhoto>, photo: ProfilePhoto): List<ProfilePhoto> {
    val replaced = photos.map {
        if (it.id == photo.id || it.position == photo.position) photo else it
    }
    val withPhoto = if (replaced.any { it.id == photo.id }) replaced else replaced + photo
    return withPhoto.sortedBy { it.position }
}

internal fun replacePhoto(photos: List<ProfilePhoto>, photo: ProfilePhoto): List<ProfilePhoto> {
    val replacedById = photos.map { if (it.id == photo.id) photo else it }
    if (replacedById.any { it.id == photo.id }) return replacedById.sortedBy { it.position }

    val replacedByPosition = photos.map { if (it.position == photo.position) photo else it }
    if (replacedByPosition.any { it.id == photo.id }) return replacedByPosition.sortedBy { it.position }

    return (photos + photo).sortedBy { it.position }
}

internal fun markProfileDraftAfterPhotoMutation(
    session: ProvisionedSession,
    photoCountDelta: Int = 0,
): ProvisionedSession {
    val snapshot = session.profileSnapshot as? ProfileSnapshot.Found ?: return session
    val profile = snapshot.profile
    val updatedProfile = profile.copy(
        status = if (profile.status == ProfileStatus.Active) ProfileStatus.Draft else profile.status,
        photoCount = (profile.photoCount + photoCountDelta).coerceAtLeast(0),
    )
    return session.copy(profileSnapshot = ProfileSnapshot.Found(updatedProfile))
}
