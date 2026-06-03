package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.dto.AddPhotoRequestDto
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.mapper.toDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.UpdateProfileInput

class ProfileRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
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

    suspend fun getMyProfilePhotos(): ApiResult<List<ProfilePhoto>> =
        authorizedCall { authorization -> api.getMyProfilePhotos(authorization) }
            .map { photos -> photos.map { it.toDomain() } }

    suspend fun addMyProfilePhoto(
        url: String,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ): ApiResult<ProfilePhoto> =
        authorizedCall { authorization ->
            api.addMyProfilePhoto(
                authorization = authorization,
                body = AddPhotoRequestDto(
                    url = url,
                    position = position,
                    isPersonPhoto = isPersonPhoto,
                    isFullBody = isFullBody,
                ),
            )
        }.map { it.toDomain() }

    suspend fun activateMyProfile(): ApiResult<Profile> =
        authorizedCall { authorization -> api.activateMyProfile(authorization) }
            .map { it.toDomain() }
}
