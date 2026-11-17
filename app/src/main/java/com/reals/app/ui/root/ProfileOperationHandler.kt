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
import com.reals.app.domain.model.isPendingModerationReview
import com.reals.app.domain.model.isRejectedByModeration
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.ui.profile.NoOpProfilePhotoPrefetcher
import com.reals.app.ui.profile.ProfilePhotoPrefetcher
import com.reals.app.ui.profile.movePhotoLocally
import com.reals.app.ui.profile.photosWithPendingOrder
import com.reals.app.domain.model.sameProfileOwner
import kotlinx.coroutines.Job
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
    private val localFirebaseEmailVerificationCoordinator: LocalFirebaseEmailVerificationCoordinator =
        LocalFirebaseEmailVerificationCoordinator.disabled(authRepository),
    private val getProfilePhotosUseCase: GetProfilePhotosUseCase,
    private val profilePhotoPrefetcher: ProfilePhotoPrefetcher = NoOpProfilePhotoPrefetcher,
    private val scope: CoroutineScope,
    private val onTerminalAuthFailure: () -> Unit,
) {
    /**
     * Called when [RealsRootUiState.Ready] is updated from elsewhere (e.g. after home load).
     * The handler already reads from the flow directly so this is optional – kept as a
     * hook for any future synchronisation needs.
     */
    private var ownerSession: ProvisionedSession? = null
    private var profileWriteJob: Job? = null
    private var profileReadJob: Job? = null

    fun cancelForSessionReplacement(session: ProvisionedSession?) {
        if (ownerSession != null && !ownerSession!!.sameProfileOwner(session)) {
            profileWriteJob?.cancel()
            profileReadJob?.cancel()
            ownerSession = null
            val ready = uiState.value as? RealsRootUiState.Ready ?: return
            uiState.value = ready.copy(profileOp = ProfileManagementState(), photos = PhotoManagementUiState())
        }
    }

    fun onReadyStateUpdated() = Unit

    // ── Profile CRUD ──

    fun createProfile(input: CreateProfileInput) {
        val current = requireReady() ?: return
        if (profileWriteJob?.isActive == true || current.creatingProfile) return
        profileWriteJob = scope.launch {
            val starting = requireReady() ?: return@launch
            if (!starting.session.sameProfileOwner(current.session)) return@launch
            val pending = starting.copy(
                profileOp = starting.profileOp.copy(
                    creatingProfile = true,
                    profileCreateError = null,
                ),
            )
            uiState.value = pending
            val result = dependencies.createProfile(input)
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            if (result is ApiResult.Success && result.value.userId != current.session.user.id) {
                uiState.value = latest.copy(profileOp = latest.profileOp.copy(creatingProfile = false,
                    profileCreateError = ApiError.Unexpected("Invalid profile owner")))
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    uiState.value = latest.copy(
                        session = latest.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profileOp = latest.profileOp.copy(
                            creatingProfile = false,
                            profileCreateError = null,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = latest.copy(
                        profileOp = latest.profileOp.copy(
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
        if (profileWriteJob?.isActive == true || current.updatingProfile || current.updatingMatchFilters || current.addingPhoto || current.reorderingPhotos || current.activatingProfile) return
        val owner = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        val cleared = current.copy(profileOp = current.profileOp.copy(profileUpdateError = null, profileUpdateMessage = null))
        val pending = cleared.copy(profileOp = cleared.profileOp.copy(updatingProfile = true))
        uiState.value = pending
        profileWriteJob = scope.launch {
            val result = dependencies.updateProfile(input)
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            if (result is ApiResult.Success && (result.value.id != owner.id || result.value.userId != owner.userId)) {
                uiState.value = latest.copy(profileOp = latest.profileOp.copy(updatingProfile = false,
                    profileUpdateError = ApiError.Unexpected("Invalid profile response")))
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    uiState.value = latest.copy(
                        session = latest.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profileOp = latest.profileOp.copy(
                            updatingProfile = false,
                            profileUpdateMessage = "Perfil actualizado.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = latest.copy(
                        profileOp = latest.profileOp.copy(
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
            if (!latest.session.sameProfileOwner(current.session)) return@launch
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
                    if (!displayed.session.sameProfileOwner(current.session)) return@launch
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
                    if (!displayed.session.sameProfileOwner(current.session)) return@launch
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
        if (profileWriteJob?.isActive == true || current.updatingProfile || current.updatingMatchFilters || current.addingPhoto || current.reorderingPhotos || current.activatingProfile) return
        val owner = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        val cleared = current.copy(profileOp = current.profileOp.copy(matchFiltersError = null, matchFiltersMessage = null))
        val pending = cleared.copy(
                profileOp = cleared.profileOp.copy(updatingMatchFilters = true),
            )
        uiState.value = pending
        profileWriteJob = scope.launch {
            val result = dependencies.updateMatchFilters(input)
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            if (result is ApiResult.Success && (result.value.id != owner.id || result.value.userId != owner.userId)) {
                uiState.value = latest.copy(profileOp = latest.profileOp.copy(updatingMatchFilters = false,
                    matchFiltersError = ApiError.Unexpected("Invalid profile response")))
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    uiState.value = latest.copy(
                        session = latest.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profileOp = latest.profileOp.copy(
                            updatingMatchFilters = false,
                            matchFiltersMessage = "Filtros actualizados.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = latest.copy(
                        profileOp = latest.profileOp.copy(
                            updatingMatchFilters = false,
                            matchFiltersError = result.error,
                        ),
                    )
                }
            }
        }
    }

    // ── Photos ──

    fun loadProfilePhotos(prefetchAfterSuccess: Boolean = false) {
        val current = requireReady() ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return
        val requestedProfileId = profile.id
        val cleared = current.copy(photos = current.photos.copy(
            profilePhotosError = null, photoActionError = null, photoActionMessage = null,
            photoReorderError = null, photoReorderMessage = null,
        ))
        val pending = cleared.copy(photos = cleared.photos.copy(loadingPhotos = true))
        uiState.value = pending
        profileReadJob = scope.launch {
            when (val result = getProfilePhotosUseCase()) {
                is ApiResult.Success -> {
                    val latest = readyForResponse(current, true) ?: return@launch
                    val latestProfile = (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile
                        ?: return@launch
                    if (latestProfile.id != requestedProfileId) return@launch
                    val orderedPhotos = result.value.sortedBy { it.position }
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(
                            loadingPhotos = false,
                            profilePhotos = orderedPhotos,
                            profilePhotosError = null,
                        ),
                    )
                    if (prefetchAfterSuccess && orderedPhotos.isNotEmpty()) {
                        profilePhotoPrefetcher.prefetchOwnProfilePhotos(
                            scope = scope,
                            profileId = latestProfile.id,
                            photos = orderedPhotos,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    val latest = readyForResponse(current, false) ?: return@launch
                    val latestProfile = (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile
                        ?: return@launch
                    if (latestProfile.id != requestedProfileId) return@launch
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

    fun cancelProfilePhotoPrefetch() {
        profilePhotoPrefetcher.cancel()
    }

    fun moveProfilePhoto(
        photoId: String,
        targetPosition: Int,
    ) {
        val current = requireReady() ?: return
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return

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

        profileWriteJob = scope.launch {
            when (val result = dependencies.reorderProfilePhotos(newPlacements)) {
                is ApiResult.Success -> {
                    val latest = readyForResponse(current, true) ?: return@launch
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
                    val latest = readyForResponse(current, !result.error.disallowsProfileSnapshot()) ?: return@launch
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(
                            profilePhotos = if (latest.profilePhotos == optimisticPhotos) previousPhotos else latest.profilePhotos,
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
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return
        if (fileUri == null) return
        val cleared = current.copy(photos = current.photos.copy(
            profilePhotosError = null, photoActionError = null, photoActionMessage = null,
            photoReorderError = null, photoReorderMessage = null,
        ))
        val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
        uiState.value = pending
        profileWriteJob = scope.launch {
            val result = dependencies.addProfilePhotoFile(fileUri, position)
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            when (result) {
                is ApiResult.Success -> completePhotoAdded(
                    previous = latest,
                    addedPhoto = result.value,
                    successMessage = "Foto subida correctamente.",
                )

                is ApiResult.Failure -> {
                    uiState.value = photoActionFailureState(latest, result.error)
                }
            }
        }
    }

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri?) {
        val current = requireReady() ?: return
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return
        if (fileUri == null) return
        val cleared = current.copy(photos = current.photos.copy(
            profilePhotosError = null, photoActionError = null, photoActionMessage = null,
            photoReorderError = null, photoReorderMessage = null,
        ))
        val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
        uiState.value = pending
        profileWriteJob = scope.launch {
            val result = dependencies.replaceProfilePhotoFile(photoId, fileUri)
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            when (result) {
                is ApiResult.Success -> completePhotoReplaced(
                    previous = latest,
                    replacedPhoto = result.value,
                    successMessage = "Foto reemplazada correctamente.",
                )

                is ApiResult.Failure -> {
                    uiState.value = photoActionFailureState(latest, result.error)
                }
            }
        }
    }

    fun deleteProfilePhoto(photoId: String, position: Int) {
        val current = requireReady() ?: return
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return
        val cleared = current.copy(photos = current.photos.copy(
            profilePhotosError = null, photoActionError = null, photoActionMessage = null,
            photoReorderError = null, photoReorderMessage = null,
        ))
        val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
        uiState.value = pending
        profileWriteJob = scope.launch {
            val result = dependencies.deleteProfilePhoto(photoId)
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            if (result is ApiResult.Success && (result.value.userId != current.session.user.id ||
                    result.value.id != (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id)) {
                uiState.value = latest.copy(photos = latest.photos.copy(addingPhoto = false,
                    photoActionError = ApiError.Unexpected("Invalid profile owner")))
                return@launch
            }
            when (result) {
                is ApiResult.Success -> completePhotoDeleted(
                    previous = latest,
                    deletedPhotoId = photoId,
                    updatedProfile = result.value,
                    successMessage = "Foto eliminada.",
                )

                is ApiResult.Failure -> {
                    uiState.value = latest.copy(
                        photos = latest.photos.copy(addingPhoto = false, photoActionError = result.error),
                    )
                }
            }
        }
    }

    // ── Activation ──

    fun activateProfile() {
        val current = requireReady() ?: return
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return

        if (current.emailVerificationRequired && !current.emailVerificationLocallyVerified) {
            return
        }

        profileWriteJob = scope.launch {
            val starting = requireReady() ?: return@launch
            if (!starting.session.sameProfileOwner(current.session)) return@launch
            val cleared = starting.copy(profileOp = starting.profileOp.copy(
                profileActivationError = null, emailVerificationMessage = null, emailVerificationError = null,
            ))
            val pending = cleared.copy(
                profileOp = cleared.profileOp.copy(
                    activatingProfile = true,
                ),
            )
            uiState.value = pending

            val verificationResult = authRepository.reloadAndRefreshEmailVerification()

            val verificationReady = readyForResponse(current, false) ?: return@launch
            val verifiedPending = when (verificationResult) {
                EmailVerificationCheckResult.Verified -> {
                    verificationReady.copy(
                        profileOp = verificationReady.profileOp.copy(
                            emailVerificationRequired = false,
                            emailVerificationLocallyVerified = true,
                            emailVerificationMessage = null,
                            emailVerificationError = null,
                            profileActivationError = null,
                        ),
                    )
                }

                EmailVerificationCheckResult.NotVerified -> {
                    uiState.value = verificationReady.copy(
                        profileOp = verificationReady.profileOp.copy(
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
                    uiState.value = verificationReady.copy(
                        profileOp = verificationReady.profileOp.copy(
                            activatingProfile = false,
                            emailVerificationMessage = null,
                            emailVerificationError = "No pudimos comprobar la verificación. Intentá nuevamente.",
                        ),
                    )
                    return@launch
                }
            }

            uiState.value = verifiedPending

            val result = dependencies.activateProfile()
            val latest = readyForResponse(current, result is ApiResult.Success) ?: return@launch
            if (result is ApiResult.Success && (result.value.profile.userId != current.session.user.id ||
                    result.value.profile.id != (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id)) {
                uiState.value = latest.copy(profileOp = latest.profileOp.copy(activatingProfile = false,
                    profileActivationError = ApiError.Unexpected("Invalid profile owner")))
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    val updatedSession = latest.session.copy(
                        profileSnapshot = ProfileSnapshot.Found(result.value.profile),
                    )

                    uiState.value = RealsRootUiState.ActivationComplete(
                        session = updatedSession,
                        result = result.value,
                    )
                }

                is ApiResult.Failure -> {
                    val emailNotVerified = result.error.isEmailNotVerified()

                    uiState.value = latest.copy(
                        profileOp = latest.profileOp.copy(
                            activatingProfile = false,
                            profileActivationError = result.error,
                            emailVerificationRequired = emailNotVerified,
                            emailVerificationLocallyVerified =
                                if (emailNotVerified) false else latest.emailVerificationLocallyVerified,
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
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return
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

        profileWriteJob = scope.launch {
            val starting = requireReady() ?: return@launch
            if (!starting.session.sameProfileOwner(current.session)) return@launch
            val pending = starting.copy(
                profileOp = starting.profileOp.copy(
                    sendingEmailVerification = true,
                    emailVerificationMessage = null,
                    emailVerificationError = null,
                ),
            )
            uiState.value = pending
            val result = authRepository.sendEmailVerificationEmail()
            val latest = readyForResponse(current, false) ?: return@launch
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
                    error = "No pudimos enviar el correo de verificación. Intentá nuevamente.",
                )
            }
            uiState.value = latest.copy(
                profileOp = latest.profileOp.copy(
                    sendingEmailVerification = false,
                    emailVerificationMessage = feedback.message,
                    emailVerificationError = feedback.error,
                    emailVerificationRequired = feedback.emailVerificationRequired
                        ?: latest.emailVerificationRequired,
                    emailVerificationLocallyVerified = feedback.emailVerificationLocallyVerified
                        ?: latest.emailVerificationLocallyVerified,
                    resendEmailVerificationAvailableAtMillis = feedback.resendAvailableAtMillis
                        ?: latest.resendEmailVerificationAvailableAtMillis,
                ),
            )
        }
    }

    fun checkEmailVerification() {
        val current = requireReady() ?: return
        if (profileWriteJob?.isActive == true || current.isEmailVerificationActionBusy()) return
        val now = System.currentTimeMillis()
        if (current.checkEmailVerificationAvailableAtMillis.isInFuture(now)) return

        profileWriteJob = scope.launch {
            val starting = requireReady() ?: return@launch
            if (!starting.session.sameProfileOwner(current.session)) return@launch
            val pending = starting.copy(
                profileOp = starting.profileOp.copy(
                    checkingEmailVerification = true,
                    emailVerificationMessage = null,
                    emailVerificationError = null,
                ),
            )
            uiState.value = pending
            val result = localFirebaseEmailVerificationCoordinator.checkOrVerifyForUserAction()
            val latest = readyForResponse(current, false) ?: return@launch
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
                    error = "No pudimos comprobar la verificación. Intentá nuevamente.",
                )
            }
            uiState.value = latest.copy(
                profileOp = latest.profileOp.copy(
                    checkingEmailVerification = false,
                    emailVerificationMessage = feedback.message,
                    emailVerificationError = feedback.error,
                    profileActivationError = if (
                        feedback.emailVerificationLocallyVerified == true
                    ) null else latest.profileOp.profileActivationError,
                    emailVerificationRequired = feedback.emailVerificationRequired
                        ?: latest.emailVerificationRequired,
                    emailVerificationLocallyVerified = feedback.emailVerificationLocallyVerified
                        ?: latest.emailVerificationLocallyVerified,
                    checkEmailVerificationAvailableAtMillis = feedback.checkAvailableAtMillis
                        ?: latest.checkEmailVerificationAvailableAtMillis,
                ),
            )
        }
    }

    private fun requireReady(): RealsRootUiState.Ready? =
        (uiState.value as? RealsRootUiState.Ready)?.also { if (ownerSession == null) ownerSession = it.session }

    private suspend fun readyForResponse(original: RealsRootUiState.Ready, success: Boolean): RealsRootUiState.Ready? {
        val latest = requireReady() ?: return null
        if (!latest.session.sameProfileOwner(original.session)) return null
        if (!success || latest.session.profileSnapshot == original.session.profileSnapshot) return latest
        // The write may have completed, but its response predates a newer confirmed snapshot.
        // Keep the pending guard until this authorized reconciliation completes.
        val captured = latest.session
        val profile = dependencies.readProfile()
        val photos: ApiResult<List<ProfilePhoto>> =
            if (profile is ApiResult.Failure && profile.error.disallowsProfileSnapshot()) profile else getProfilePhotosUseCase()
        val current = requireReady() ?: return null
        if (!current.session.sameProfileOwner(captured)) return null
        val confirmed = (profile as? ApiResult.Success)?.value as? ProfileSnapshot.Found
        val valid = confirmed?.profile?.let { it.id == (captured.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id && it.userId == captured.user.id } == true
        val error = (profile as? ApiResult.Failure)?.error ?: (photos as? ApiResult.Failure)?.error
            ?: if (!valid) ApiError.Unexpected("Invalid reconciliation response") else null
        val existing = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile
        val notOlder = confirmed?.profile?.let { returned -> existing == returned || runCatching {
            java.time.Instant.parse(returned.updatedAt).isAfter(java.time.Instant.parse(existing?.updatedAt))
        }.getOrDefault(false) } == true
        val install = valid && notOlder && error == null && current.session.profileSnapshot == captured.profileSnapshot
        uiState.value = current.copy(
            session = if (install) current.session.copy(profileSnapshot = confirmed!!) else current.session,
            profileOp = current.profileOp.copy(
                updatingProfile = false, updatingMatchFilters = false, activatingProfile = false, creatingProfile = false,
                profileCreateError = if (current.creatingProfile) error else current.profileCreateError,
                profileActivationError = if (current.activatingProfile) error else current.profileActivationError,
                profileUpdateError = if (current.updatingProfile) error else current.profileUpdateError,
                matchFiltersError = if (current.updatingMatchFilters) error else current.matchFiltersError,
            ),
            photos = current.photos.copy(
                addingPhoto = false, reorderingPhotos = false, loadingPhotos = false,
                profilePhotos = if (install) (photos as ApiResult.Success).value.sortedBy { it.position } else current.profilePhotos,
                profilePhotosError = if (current.loadingPhotos) error else current.profilePhotosError,
                photoActionError = if (current.addingPhoto) error else current.photoActionError,
                photoReorderError = if (current.reorderingPhotos) error else current.photoReorderError,
            ),
        )
        return null
    }

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
            photoActionMessage = profilePhotoUploadSuccessMessage(addedPhoto, successMessage),
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
        photoActionMessage = profilePhotoUploadSuccessMessage(replacedPhoto, successMessage),
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

internal fun photoActionFailureState(
    previous: RealsRootUiState.Ready,
    error: ApiError,
): RealsRootUiState.Ready {
    val emailNotVerified = error.isEmailNotVerified()
    return previous.copy(
        profileOp = if (emailNotVerified) {
            previous.profileOp.copy(
                emailVerificationRequired = true,
                emailVerificationLocallyVerified = false,
                emailVerificationMessage = null,
                emailVerificationError = null,
            )
        } else {
            previous.profileOp
        },
        photos = previous.photos.copy(
            addingPhoto = false,
            photoActionError = error,
        ),
    )
}

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

internal fun profilePhotoUploadSuccessMessage(
    photo: ProfilePhoto,
    defaultMessage: String,
): String =
    when {
        photo.isRejectedByModeration() ->
            "Foto guardada, pero fue rechazada. Reemplazala para que pueda mostrarse a otras personas."
        photo.isPendingModerationReview() ->
            "Foto guardada. Está pendiente de revisión y no será visible para otras personas hasta que la aprobemos."
        else -> defaultMessage
    }
