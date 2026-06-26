package com.reals.app.ui.root

import android.net.Uri
import com.reals.app.core.network.ApiResult
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
    private val getProfilePhotosUseCase: GetProfilePhotosUseCase,
    private val scope: CoroutineScope,
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
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(loadingPhotos = true))
            uiState.value = pending
            when (val result = getProfilePhotosUseCase()) {
                is ApiResult.Success -> {
                    uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            loadingPhotos = false,
                            profilePhotos = result.value.sortedBy { it.position },
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            loadingPhotos = false,
                            profilePhotosError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun addProfilePhotoFile(position: Int, fileUri: Uri) {
        val current = requireReady() ?: return
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

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri) {
        val current = requireReady() ?: return
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
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(profileOp = cleared.profileOp.copy(activatingProfile = true))
            uiState.value = pending
            when (val result = dependencies.activateProfile()) {
                is ApiResult.Success -> {
                    val updatedSession = pending.session.copy(
                        profileSnapshot = ProfileSnapshot.Found(result.value.profile),
                    )
                    uiState.value = RealsRootUiState.ActivationComplete(
                        session = updatedSession,
                        result = result.value,
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        profileOp = pending.profileOp.copy(
                            activatingProfile = false,
                            profileActivationError = result.error,
                        ),
                    )
                }
            }
        }
    }

    // ── Private helpers ──

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
