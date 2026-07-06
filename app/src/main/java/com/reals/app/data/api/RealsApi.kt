package com.reals.app.data.api

import com.reals.app.data.dto.AddProposalRequestDto
import com.reals.app.data.dto.ChatDecisionRequestDto
import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestCreateRequestDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.ConnectionDismissalResponseDto
import com.reals.app.data.dto.ConnectionResponseDto
import com.reals.app.data.dto.CreateProfileRequestDto
import com.reals.app.data.dto.EnqueueMatchmakingRequestDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.HomePendingStateResponseDto
import com.reals.app.data.dto.HomeStatusResponseDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
import com.reals.app.data.dto.NegotiationResponseDto
import com.reals.app.data.dto.PartnerPersonalMessageResponseDto
import com.reals.app.data.dto.PersonalMessageRequestDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.RegisterPushTokenRequestDto
import com.reals.app.data.dto.RegisterPushTokenResponseDto
import com.reals.app.data.dto.ReorderProfilePhotosRequestDto
import com.reals.app.data.dto.ScheduleProposalResponseDto
import com.reals.app.data.dto.SendMessageRequestDto
import com.reals.app.data.dto.UpdateMatchFiltersRequestDto
import com.reals.app.data.dto.UpdateProfileRequestDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.data.dto.VisualDecisionRequestDto
import com.reals.app.data.dto.VisualProfileResponseDto
import kotlinx.serialization.json.JsonElement
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
import retrofit2.http.Query

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

    @GET("api/me/home/status")
    suspend fun getHomeStatus(
        @Header("Authorization") authorization: String,
    ): Response<HomeStatusResponseDto>

    @GET("api/me/home/pending")
    suspend fun getHomePending(
        @Header("Authorization") authorization: String,
    ): Response<HomePendingStateResponseDto>

    @PUT("api/me/push-tokens")
    suspend fun registerPushToken(
        @Header("Authorization") authorization: String,
        @Body body: RegisterPushTokenRequestDto,
    ): Response<RegisterPushTokenResponseDto>

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

    @PUT("api/me/profile/photos/reorder")
    suspend fun reorderMyProfilePhotos(
        @Header("Authorization") authorization: String,
        @Body body: ReorderProfilePhotosRequestDto,
    ): Response<List<PhotoResponseDto>>

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

    @GET("api/matches/{matchId}/visual-profile")
    suspend fun getVisualProfile(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
    ): Response<VisualProfileResponseDto>

    @POST("api/matches/{matchId}/visual-decision")
    suspend fun submitVisualDecision(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
        @Body body: VisualDecisionRequestDto,
    ): Response<MatchResponseDto>

    @PUT("api/matches/{matchId}/personal-messages/me")
    suspend fun putMyPersonalMessage(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
        @Body body: PersonalMessageRequestDto,
    ): Response<Unit>

    @GET("api/matches/{matchId}/personal-messages/partner")
    suspend fun getPartnerPersonalMessage(
        @Header("Authorization") authorization: String,
        @Path("matchId") matchId: String,
    ): Response<PartnerPersonalMessageResponseDto>

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
        @Query("after") afterMessageId: String? = null,
        @Query("afterMessageId") afterMessageIdAlias: String? = null,
    ): Response<JsonElement>

    @POST("api/chats/{chatId}/guidance/next-request")
    suspend fun requestNextFirstChatGuidanceQuestion(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
    ): Response<FirstChatGuidanceResponseDto>

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
    ): Response<ChatExitOutcomeResponseDto>

    @POST("api/chats/{chatId}/exit-requests/{exitRequestId}/timeout")
    suspend fun timeoutChatExitRequest(
        @Header("Authorization") authorization: String,
        @Path("chatId") chatId: String,
        @Path("exitRequestId") exitRequestId: String,
    ): Response<ChatExitOutcomeResponseDto>

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

    @GET("api/connections/{connectionId}")
    suspend fun getConnection(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
    ): Response<ConnectionResponseDto>

    @GET("api/connections/{connectionId}/chat")
    suspend fun getSecondChatForConnection(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
    ): Response<ChatResponseDto>

    @POST("api/connections/{connectionId}/second-chat-dismissal")
    suspend fun dismissSecondChatForConnection(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
    ): Response<ConnectionDismissalResponseDto>

    @GET("api/connections/{connectionId}/negotiation")
    suspend fun getConnectionNegotiation(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
    ): Response<NegotiationResponseDto>

    @GET("api/connections/{connectionId}/proposals")
    suspend fun getConnectionProposals(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
    ): Response<List<ScheduleProposalResponseDto>>

    @POST("api/connections/{connectionId}/proposals")
    suspend fun submitConnectionProposals(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
        @Body body: AddProposalRequestDto,
    ): Response<List<ScheduleProposalResponseDto>>

    @POST("api/connections/{connectionId}/proposals/{proposalId}/acceptance")
    suspend fun acceptConnectionProposal(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
        @Path("proposalId") proposalId: String,
    ): Response<NegotiationResponseDto>

    @POST("api/connections/{connectionId}/negotiation/rejections")
    suspend fun rejectConnectionNegotiationRound(
        @Header("Authorization") authorization: String,
        @Path("connectionId") connectionId: String,
    ): Response<NegotiationResponseDto>
}
