package com.reals.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.mapper.toDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileRepository(
    private val context: Context?,
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

    suspend fun updateMyMatchFilters(input: UpdateMatchFiltersInput): ApiResult<Profile> =
        authorizedCall { authorization -> api.updateMyMatchFilters(authorization, input.toDto()) }
            .map { it.toDomain() }

    suspend fun getMyProfilePhotos(): ApiResult<List<ProfilePhoto>> =
        authorizedCall { authorization -> api.getMyProfilePhotos(authorization) }
            .map { photos -> photos.map { it.toDomain() } }

    suspend fun addMyProfilePhotoFile(
        fileUri: Uri,
        position: Int,
    ): ApiResult<ProfilePhoto> =
        authorizedCall { authorization ->
            api.addMyProfilePhotoFile(
                authorization = authorization,
                file = filePart(fileUri),
                position = positionPart(position),
            )
        }.map { it.toDomain() }

    suspend fun deleteMyProfilePhoto(photoId: String): ApiResult<Profile> =
        authorizedCall { authorization -> api.deleteMyProfilePhoto(authorization, photoId) }
            .map { it.toDomain() }

    suspend fun replaceMyProfilePhotoFile(
        photoId: String,
        fileUri: Uri,
    ): ApiResult<ProfilePhoto> =
        authorizedCall { authorization ->
            api.replaceMyProfilePhotoFile(
                authorization = authorization,
                photoId = photoId,
                file = filePart(fileUri),
            )
        }.map { it.toDomain() }

    suspend fun activateMyProfile(): ApiResult<Profile> =
        authorizedCall { authorization -> api.activateMyProfile(authorization) }
            .map { it.toDomain() }

    private fun filePart(uri: Uri): MultipartBody.Part {
        val resolver = requireNotNull(context) { "Context is required for file uploads." }.contentResolver
        val contentType = resolver.getType(uri)
            ?.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaType()
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer el archivo seleccionado.")
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = displayName(uri),
            body = bytes.toRequestBody(contentType),
        )
    }

    private fun displayName(uri: Uri): String {
        val resolver = requireNotNull(context) { "Context is required for file uploads." }.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                val value = cursor.getString(nameIndex)
                if (!value.isNullOrBlank()) return value
            }
        }
        return "profile-photo"
    }

    private fun positionPart(position: Int): RequestBody =
        position.toString().toRequestBody("text/plain".toMediaType())
}
