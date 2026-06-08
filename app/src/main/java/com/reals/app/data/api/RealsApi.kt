package com.reals.app.data.api

import com.reals.app.data.dto.AddPhotoRequestDto
import com.reals.app.data.dto.CreateProfileRequestDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.ReplacePhotoRequestDto
import com.reals.app.data.dto.UpdateMatchFiltersRequestDto
import com.reals.app.data.dto.UpdateProfileRequestDto
import com.reals.app.data.dto.UserResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT

interface RealsApi {
    @GET("api/ping")
    suspend fun ping(): Response<PingResponseDto>

    @POST("api/me/provision")
    suspend fun provisionMe(
        @Header("Authorization") authorization: String,
    ): Response<UserResponseDto>

    @GET("api/me")
    suspend fun getMe(
        @Header("Authorization") authorization: String,
    ): Response<UserResponseDto>

    @DELETE("api/me")
    suspend fun deleteMe(
        @Header("Authorization") authorization: String,
    ): Response<Unit>

    @POST("api/me/reactivation")
    suspend fun reactivateMe(
        @Header("Authorization") authorization: String,
    ): Response<UserResponseDto>

    @GET("api/me/profile")
    suspend fun getMyProfile(
        @Header("Authorization") authorization: String,
    ): Response<ProfileResponseDto>

    @POST("api/me/profile")
    suspend fun createMyProfile(
        @Header("Authorization") authorization: String,
        @Body body: CreateProfileRequestDto,
    ): Response<ProfileResponseDto>

    @PATCH("api/me/profile")
    suspend fun updateMyProfile(
        @Header("Authorization") authorization: String,
        @Body body: UpdateProfileRequestDto,
    ): Response<ProfileResponseDto>

    @PUT("api/me/profile/match-filters")
    suspend fun updateMyMatchFilters(
        @Header("Authorization") authorization: String,
        @Body body: UpdateMatchFiltersRequestDto,
    ): Response<ProfileResponseDto>

    @GET("api/me/profile/photos")
    suspend fun getMyProfilePhotos(
        @Header("Authorization") authorization: String,
    ): Response<List<PhotoResponseDto>>

    @POST("api/me/profile/photos")
    suspend fun addMyProfilePhoto(
        @Header("Authorization") authorization: String,
        @Body body: AddPhotoRequestDto,
    ): Response<PhotoResponseDto>

    @Multipart
    @POST("api/me/profile/photos")
    suspend fun addMyProfilePhotoFile(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("position") position: RequestBody,
    ): Response<PhotoResponseDto>

    @DELETE("api/me/profile/photos/{photoId}")
    suspend fun deleteMyProfilePhoto(
        @Header("Authorization") authorization: String,
        @Path("photoId") photoId: String,
    ): Response<ProfileResponseDto>

    @PUT("api/me/profile/photos/position/{position}")
    suspend fun replaceMyProfilePhoto(
        @Header("Authorization") authorization: String,
        @Path("position") position: Int,
        @Body body: ReplacePhotoRequestDto,
    ): Response<PhotoResponseDto>

    @Multipart
    @PUT("api/me/profile/photos/{photoId}/file")
    suspend fun replaceMyProfilePhotoFile(
        @Header("Authorization") authorization: String,
        @Path("photoId") photoId: String,
        @Part file: MultipartBody.Part,
    ): Response<PhotoResponseDto>

    @POST("api/me/profile/activation")
    suspend fun activateMyProfile(
        @Header("Authorization") authorization: String,
    ): Response<ProfileResponseDto>
}
