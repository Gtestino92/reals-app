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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT

interface RealsApi {
    @GET("api/ping")
    suspend fun ping(): Response<PingResponseDto>

    @POST("api/me/provision")
    suspend fun provisionMe(
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

    @DELETE("api/me/profile/photos/{position}")
    suspend fun deleteMyProfilePhoto(
        @Header("Authorization") authorization: String,
        @Path("position") position: Int,
    ): Response<ProfileResponseDto>

    @PUT("api/me/profile/photos/{position}")
    suspend fun replaceMyProfilePhoto(
        @Header("Authorization") authorization: String,
        @Path("position") position: Int,
        @Body body: ReplacePhotoRequestDto,
    ): Response<PhotoResponseDto>

    @POST("api/me/profile/activation")
    suspend fun activateMyProfile(
        @Header("Authorization") authorization: String,
    ): Response<ProfileResponseDto>
}
