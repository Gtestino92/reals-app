package com.reals.app.ui.root

import android.net.Uri
import com.reals.app.core.network.ApiResult
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
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
 * @param provisionAndLoadProfile refreshes the backend session after photo mutations
 * @param scope coroutine scope (typically [androidx.lifecycle.viewModelScope])
 */
class ProfileOperationHandler(
    private val uiState: MutableStateFlow<RealsRootUiState>,
    private val dependencies: ProfileFeatureDependencies,
    private val getProfilePhotosUseCase: GetProfilePhotosUseCase,
    private val provisionAndLoadProfile: ProvisionAndLoadProfileUseCase,
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

    fun addMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        val current = requireReady() ?: return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
            uiState.value = pending
            when (val result = dependencies.addMockProfilePhoto(profile, position, isPersonPhoto, isFullBody)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
                    successMessage = "Foto agregada correctamente.",
                )

                is ApiResult.Failure -> {
                    uiState.value = pending.copy(
                        photos = pending.photos.copy(addingPhoto = false, photoActionError = result.error),
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
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
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

    fun replaceMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        val current = requireReady() ?: return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
            uiState.value = pending
            when (val result = dependencies.replaceMockProfilePhoto(profile, position, isPersonPhoto, isFullBody)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
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

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri) {
        val current = requireReady() ?: return
        scope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(photos = cleared.photos.copy(addingPhoto = true))
            uiState.value = pending
            when (val result = dependencies.replaceProfilePhotoFile(photoId, fileUri)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
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
                is ApiResult.Success -> {
                    val refreshedPhotos = getProfilePhotosUseCase()
                    val updatedPhotos = (refreshedPhotos as? ApiResult.Success)?.value
                        ?.sortedBy { it.position }
                        ?: pending.profilePhotos.filterNot { it.id == photoId }
                    uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        photos = pending.photos.copy(
                            profilePhotos = updatedPhotos,
                            profilePhotosError = null,
                            addingPhoto = false,
                            photoActionMessage = "Foto eliminada.",
                        ),
                    )
                }

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

    private suspend fun refreshAfterPhotoMutation(
        previous: RealsRootUiState.Ready,
        successMessage: String,
    ) {
        val refreshedPhotos = getProfilePhotosUseCase()
        val refreshedSession = provisionAndLoadProfile()

        if (refreshedPhotos is ApiResult.Success) {
            uiState.value = previous.copy(
                session = (refreshedSession as? ApiResult.Success)?.value ?: previous.session,
                photos = previous.photos.copy(
                    profilePhotos = refreshedPhotos.value.sortedBy { it.position },
                    profilePhotosError = null,
                    addingPhoto = false,
                    photoActionMessage = successMessage,
                    photoActionError = null,
                ),
            )
            return
        }

        uiState.value = previous.copy(
            session = (refreshedSession as? ApiResult.Success)?.value ?: previous.session,
            photos = previous.photos.copy(
                addingPhoto = false,
                photoActionError = (refreshedPhotos as ApiResult.Failure).error,
            ),
        )
    }
}
