package com.reals.app.data.api

import com.reals.app.data.dto.AddPhotoRequestDto
import com.reals.app.data.dto.ChatDecisionRequestDto
import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestCreateRequestDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.CreateProfileRequestDto
import com.reals.app.data.dto.EnqueueMatchmakingRequestDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.ReplacePhotoRequestDto
import com.reals.app.data.dto.SendMessageRequestDto
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

    @GET("api/me/home")
    suspend fun getHome(
        @Header("Authorization") authorization: String,
    ): Response<HomeResponseDto>

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

    @POST("api/matchmaking/queue")
    suspend fun enqueueMatchmaking(
        @Header("Authorization") authorization: String,
        @Body body: EnqueueMatchmakingRequestDto,
    ): Response<QueueStatusResponseDto>

    @DELETE("api/matchmaking/queue")
    suspend fun leaveMatchmakingQueue(
        @Header("Authorization") authorization: String,
    ): Response<QueueStatusResponseDto>

    @GET("api/matchmaking/queue")
    suspend fun getMatchmakingQueueStatus(
        @Header("Authorization") authorization: String,
    ): Response<QueueStatusResponseDto>

    @GET("api/matches/{matchId}")
    suspend fun getMatch(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
    ): Response<MatchResponseDto>

    @GET("api/matches/{matchId}/chat")
    suspend fun getFirstChatForMatch(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
    ): Response<ChatResponseDto>

    @POST("api/matches/{matchId}/chat-decision")
    suspend fun submitChatDecision(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
        @Body body: ChatDecisionRequestDto,
    ): Response<MatchResponseDto>

    @GET("api/chats/{chatId}")
    suspend fun getChat(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
    ): Response<ChatResponseDto>

    @POST("api/chats/{chatId}/messages")
    suspend fun sendChatMessage(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Body body: SendMessageRequestDto,
    ): Response<ChatMessageResponseDto>

    @GET("api/chats/{chatId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
    ): Response<List<ChatMessageResponseDto>>

    @POST("api/chats/{chatId}/exit-requests")
    suspend fun requestChatExit(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Body body: ChatExitRequestCreateRequestDto,
    ): Response<ChatExitRequestResponseDto>

    @GET("api/chats/{chatId}/exit-requests")
    suspend fun getChatExitRequests(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
    ): Response<List<ChatExitRequestResponseDto>>

    @POST("api/chats/{chatId}/exit-requests/{exitRequestId}/acceptance")
    suspend fun acceptChatExitRequest(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Path("exitRequestId") exitRequestId: String,
    ): Response<ChatExitOutcomeResponseDto>

    @POST("api/chats/{chatId}/exit-requests/{exitRequestId}/rejection")
    suspend fun rejectChatExitRequest(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Path("exitRequestId") exitRequestId: String,
    ): Response<ChatExitRequestResponseDto>

    @POST("api/chats/{chatId}/cancellations")
    suspend fun cancelChat(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Body body: ChatExitRequestCreateRequestDto,
    ): Response<ChatExitOutcomeResponseDto>

    @POST("api/chats/{chatId}/safety-cancellations")
    suspend fun safetyCancelChat(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Body body: ChatExitRequestCreateRequestDto,
    ): Response<ChatExitOutcomeResponseDto>
}
