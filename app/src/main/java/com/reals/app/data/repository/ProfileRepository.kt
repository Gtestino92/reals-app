package com.reals.app.data.repository

import android.content.Context
import android.net.Uri
import com.reals.app.core.media.DefaultRealsCropUploadInspector
import com.reals.app.core.media.PreparedProfilePhotoUpload
import com.reals.app.core.media.ProfilePhotoPipelineTiming
import com.reals.app.core.media.ProfilePhotoTimingFields
import com.reals.app.core.media.ProfilePhotoPreprocessingException
import com.reals.app.core.media.ProfilePhotoPreprocessingFailure
import com.reals.app.core.media.ProfilePhotoPreprocessor
import com.reals.app.core.media.ProfilePhotoUploadPipelinePreprocessor
import com.reals.app.core.media.ProfilePhotoUploadPreprocessor
import com.reals.app.core.media.profilePhotoCropCacheDirectory
import com.reals.app.core.media.profilePhotoPreparedUploadCacheDirectory
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.PhotoPreparationReason
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.PhotoPlacementRequestDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.ReorderProfilePhotosRequestDto
import com.reals.app.data.mapper.toDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.CountryReference
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.PhotoPlacementInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import retrofit2.Response

class ProfileRepository(
    context: Context?,
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
    private val photoPreprocessor: ProfilePhotoUploadPreprocessor? =
        context?.let {
            ProfilePhotoUploadPipelinePreprocessor(
                fallbackPreprocessor = ProfilePhotoPreprocessor(it),
                cropInspector = DefaultRealsCropUploadInspector(profilePhotoCropCacheDirectory(it)),
                preparedCacheDir = profilePhotoPreparedUploadCacheDirectory(it),
            )
        },
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getMyProfileSnapshot(): ApiResult<ProfileSnapshot> {
        return when (val result = authorizedCall { authorization -> api.getMyProfile(authorization) }) {
            is ApiResult.Success -> ApiResult.Success(ProfileSnapshot.Found(result.value.toDomain()))
            is ApiResult.Failure -> {
                val backend = result.error as? ApiError.Backend
                if (backend?.statusCode == 404) {
                    ApiResult.Success(ProfileSnapshot.Missing)
                } else {
                    result
                }
            }
        }
    }

    suspend fun createMyProfile(input: CreateProfileInput): ApiResult<Profile> =
        authorizedCall { authorization -> api.createMyProfile(authorization, input.toDto()) }
            .map { it.toDomain() }

    suspend fun updateMyProfile(input: UpdateProfileInput): ApiResult<Profile> =
        authorizedCall { authorization -> api.updateMyProfile(authorization, input.toDto()) }
            .map { it.toDomain() }

    suspend fun getCountries(): ApiResult<List<CountryReference>> =
        authorizedCall { authorization -> api.getCountries(authorization) }
            .map { countries -> countries.map { it.toDomain() } }

    suspend fun updateMyMatchFilters(input: UpdateMatchFiltersInput): ApiResult<Profile> =
        authorizedCall { authorization -> api.updateMyMatchFilters(authorization, input.toDto()) }
            .map { it.toDomain() }

    suspend fun getMyProfilePhotos(): ApiResult<List<ProfilePhoto>> =
        authorizedCall { authorization -> api.getMyProfilePhotos(authorization) }
            .map { photos -> photos.map { it.toDomain() } }

    suspend fun reorderMyProfilePhotos(
        placements: List<PhotoPlacementInput>,
    ): ApiResult<List<ProfilePhoto>> =
        authorizedCall { authorization ->
            api.reorderMyProfilePhotos(
                authorization = authorization,
                body = ReorderProfilePhotosRequestDto(
                    placements = placements.map {
                        PhotoPlacementRequestDto(
                            photoId = it.photoId,
                            position = it.position,
                        )
                    },
                ),
            )
        }.map { photos -> photos.map { it.toDomain() } }

    suspend fun addMyProfilePhotoFile(
        fileUri: Uri?,
        position: Int,
    ): ApiResult<ProfilePhoto> =
        uploadPreparedProfilePhoto(fileUri) { authorization, prepared ->
            api.addMyProfilePhotoFile(
                authorization = authorization,
                file = ProfilePhotoUploadMultipart.filePart(prepared),
                position = ProfilePhotoUploadMultipart.positionPart(position),
            )
        }.map { it.toDomain() }

    suspend fun deleteMyProfilePhoto(photoId: String): ApiResult<Profile> =
        authorizedCall { authorization -> api.deleteMyProfilePhoto(authorization, photoId) }
            .map { it.toDomain() }

    suspend fun replaceMyProfilePhotoFile(
        photoId: String,
        fileUri: Uri?,
    ): ApiResult<ProfilePhoto> =
        uploadPreparedProfilePhoto(fileUri) { authorization, prepared ->
            api.replaceMyProfilePhotoFile(
                authorization = authorization,
                photoId = photoId,
                file = ProfilePhotoUploadMultipart.filePart(prepared),
            )
        }.map { it.toDomain() }

    suspend fun activateMyProfile(): ApiResult<Profile> =
        authorizedCall { authorization -> api.activateMyProfile(authorization) }
            .map { it.toDomain() }

    private suspend fun uploadPreparedProfilePhoto(
        sourceUri: Uri?,
        call: suspend (authorization: String, prepared: PreparedProfilePhotoUpload) -> Response<PhotoResponseDto>,
    ): ApiResult<PhotoResponseDto> {
        val preprocessor = photoPreprocessor ?: return ApiResult.Failure(photoPreparationError())
        val preparationStartedAt = ProfilePhotoPipelineTiming.nowMillis()
        val preparation = preprocessor.prepare(sourceUri)
        if (preparation.isFailure) {
            ProfilePhotoPipelineTiming.log(
                ProfilePhotoTimingFields(
                    phase = "upload_prepare",
                    durationMs = ProfilePhotoPipelineTiming.nowMillis() - preparationStartedAt,
                    fastPath = false,
                ),
            )
            return ApiResult.Failure(photoPreparationError(preparation.exceptionOrNull()))
        }
        val upload = preparation.getOrThrow()
        ProfilePhotoPipelineTiming.log(
            ProfilePhotoTimingFields(
                phase = "upload_prepare",
                durationMs = ProfilePhotoPipelineTiming.nowMillis() - preparationStartedAt,
                bytes = upload.fileSizeBytes,
                fastPath = upload.usedTrustedCropFastPath,
            ),
        )
        return upload.useDeletingFile { prepared ->
            val requestStartedAt = ProfilePhotoPipelineTiming.nowMillis()
            try {
                authorizedCall { authorization -> call(authorization, prepared) }
            } finally {
                ProfilePhotoPipelineTiming.log(
                    ProfilePhotoTimingFields(
                        phase = "authenticated_request",
                        durationMs = ProfilePhotoPipelineTiming.nowMillis() - requestStartedAt,
                    ),
                )
            }
        }
    }

    private fun photoPreparationError(cause: Throwable? = null): ApiError.PhotoPreparation {
        val reason = (cause as? ProfilePhotoPreprocessingException)?.failure
            ?: ProfilePhotoPreprocessingFailure.UndecodableSource
        return ApiError.PhotoPreparation(
            reason = when (reason) {
                ProfilePhotoPreprocessingFailure.UndecodableSource -> PhotoPreparationReason.UndecodableSource
                ProfilePhotoPreprocessingFailure.SourceTooLarge -> PhotoPreparationReason.SourceTooLarge
                ProfilePhotoPreprocessingFailure.CacheWriteFailure -> PhotoPreparationReason.CacheWriteFailure
                ProfilePhotoPreprocessingFailure.EncodingFailure -> PhotoPreparationReason.EncodingFailure
            },
            message = "No se pudo preparar la foto.",
        )
    }
}
