package com.reals.app.data.api

import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.UserResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

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
}
