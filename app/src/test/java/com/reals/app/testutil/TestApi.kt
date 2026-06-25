package com.reals.app.testutil

import com.reals.app.core.network.ApiExecutor
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.AddPhotoRequestDto
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
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.NegotiationResponseDto
import com.reals.app.data.dto.PartnerPersonalMessageResponseDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.RegisterPushTokenRequestDto
import com.reals.app.data.dto.RegisterPushTokenResponseDto
import com.reals.app.data.dto.ReplacePhotoRequestDto
import com.reals.app.data.dto.ScheduleProposalResponseDto
import com.reals.app.data.dto.SendMessageRequestDto
import com.reals.app.data.dto.UpdateMatchFiltersRequestDto
import com.reals.app.data.dto.UpdateProfileRequestDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.data.dto.VisualDecisionRequestDto
import com.reals.app.data.dto.VisualProfileResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

val testJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun testApiExecutor(): ApiExecutor = ApiExecutor(testJson)

fun backendError(
    statusCode: Int,
    code: String,
    message: String = "backend error",
): Response<Unit> {
    val body = """{"code":"$code","error":"$code","message":"$message"}"""
        .toResponseBody("application/json".toMediaType())
    return Response.error(statusCode, body)
}

fun <T> backendErrorResponse(
    statusCode: Int,
    code: String,
    message: String = "backend error",
): Response<T> {
    val body = """{"code":"$code","error":"$code","message":"$message"}"""
        .toResponseBody("application/json".toMediaType())
    return Response.error(statusCode, body)
}

class FakeRealsApi : RealsApi {
    var calls: List<String> = emptyList()
        private set
    var lastAuthorization: String? = null
        private set
    var lastPathId: String? = null
        private set

    var enqueueBody: EnqueueMatchmakingRequestDto? = null
        private set
    var chatDecisionBody: ChatDecisionRequestDto? = null
        private set
    var chatMessageBody: SendMessageRequestDto? = null
        private set
    var exitBody: ChatExitRequestCreateRequestDto? = null
        private set
    var visualDecisionBody: VisualDecisionRequestDto? = null
        private set
    var personalMessageBody: com.reals.app.data.dto.PersonalMessageRequestDto? = null
        private set
    var proposalsBody: AddProposalRequestDto? = null
        private set
    var createProfileBody: CreateProfileRequestDto? = null
        private set
    var updateProfileBody: UpdateProfileRequestDto? = null
        private set
    var updateFiltersBody: UpdateMatchFiltersRequestDto? = null
        private set
    var addPhotoBody: AddPhotoRequestDto? = null
        private set
    var replacePhotoBody: ReplacePhotoRequestDto? = null
        private set
    var registerPushTokenBody: RegisterPushTokenRequestDto? = null
        private set

    var pingResponse: Response<PingResponseDto> = Response.success(PingResponseDto("ok"))
    var userResponse: Response<UserResponseDto> = Response.success(TestDtos.user())
    var deleteMeResponse: Response<Unit> = Response.success(Unit)
    var homeResponse: Response<HomeResponseDto> = Response.success(TestDtos.home())
    var registerPushTokenResponse: Response<RegisterPushTokenResponseDto> =
        Response.success(RegisterPushTokenResponseDto(registered = true))
    var profileResponse: Response<ProfileResponseDto> = Response.success(TestDtos.profile())
    var photosResponse: Response<List<PhotoResponseDto>> = Response.success(listOf(TestDtos.photo()))
    var photoResponse: Response<PhotoResponseDto> = Response.success(TestDtos.photo())
    var queueResponse: Response<QueueStatusResponseDto> = Response.success(TestDtos.queueStatus())
    var matchResponse: Response<MatchResponseDto> = Response.success(TestDtos.match())
    var chatResponse: Response<ChatResponseDto> = Response.success(TestDtos.chat())
    var chatResponseQueue: MutableList<Response<ChatResponseDto>> = mutableListOf()
    var visualProfileResponse: Response<VisualProfileResponseDto> = Response.success(TestDtos.visualProfile())
    var unitResponse: Response<Unit> = Response.success(Unit)
    var partnerMessageResponse: Response<PartnerPersonalMessageResponseDto> =
        Response.success(PartnerPersonalMessageResponseDto("hola"))
    var chatMessageResponse: Response<ChatMessageResponseDto> = Response.success(TestDtos.chatMessage())
    var chatMessagesResponse: Response<JsonElement> = Response.success(TestDtos.chatMessagesArrayPayload())
    var exitRequestResponse: Response<ChatExitRequestResponseDto> = Response.success(TestDtos.exitRequest())
    var exitRequestsResponse: Response<List<ChatExitRequestResponseDto>> = Response.success(listOf(TestDtos.exitRequest()))
    var exitOutcomeResponse: Response<ChatExitOutcomeResponseDto> = Response.success(TestDtos.exitOutcome())
    var connectionResponse: Response<ConnectionResponseDto> = Response.success(TestDtos.connection())
    var connectionDismissalResponse: Response<ConnectionDismissalResponseDto> =
        Response.success(ConnectionDismissalResponseDto(dismissed = true))
    var negotiationResponse: Response<NegotiationResponseDto> = Response.success(TestDtos.negotiation())
    var proposalsResponse: Response<List<ScheduleProposalResponseDto>> = Response.success(listOf(TestDtos.proposal()))

    override suspend fun ping(): Response<PingResponseDto> = record("ping", null) { pingResponse }

    override suspend fun provisionMe(authorization: String): Response<UserResponseDto> =
        record("provisionMe", authorization) { userResponse }

    override suspend fun getMe(authorization: String): Response<UserResponseDto> =
        record("getMe", authorization) { userResponse }

    override suspend fun getHome(authorization: String): Response<HomeResponseDto> =
        record("getHome", authorization) { homeResponse }

    override suspend fun registerPushToken(
        authorization: String,
        body: RegisterPushTokenRequestDto,
    ): Response<RegisterPushTokenResponseDto> =
        record("registerPushToken", authorization) {
            registerPushTokenBody = body
            registerPushTokenResponse
        }

    override suspend fun deleteMe(authorization: String): Response<Unit> =
        record("deleteMe", authorization) { deleteMeResponse }

    override suspend fun reactivateMe(authorization: String): Response<UserResponseDto> =
        record("reactivateMe", authorization) { userResponse }

    override suspend fun getMyProfile(authorization: String): Response<ProfileResponseDto> =
        record("getMyProfile", authorization) { profileResponse }

    override suspend fun createMyProfile(
        authorization: String,
        body: CreateProfileRequestDto,
    ): Response<ProfileResponseDto> =
        record("createMyProfile", authorization) {
            createProfileBody = body
            profileResponse
        }

    override suspend fun updateMyProfile(
        authorization: String,
        body: UpdateProfileRequestDto,
    ): Response<ProfileResponseDto> =
        record("updateMyProfile", authorization) {
            updateProfileBody = body
            profileResponse
        }

    override suspend fun updateMyMatchFilters(
        authorization: String,
        body: UpdateMatchFiltersRequestDto,
    ): Response<ProfileResponseDto> =
        record("updateMyMatchFilters", authorization) {
            updateFiltersBody = body
            profileResponse
        }

    override suspend fun getMyProfilePhotos(authorization: String): Response<List<PhotoResponseDto>> =
        record("getMyProfilePhotos", authorization) { photosResponse }

    override suspend fun addMyProfilePhoto(
        authorization: String,
        body: AddPhotoRequestDto,
    ): Response<PhotoResponseDto> =
        record("addMyProfilePhoto", authorization) {
            addPhotoBody = body
            photoResponse
        }

    override suspend fun addMyProfilePhotoFile(
        authorization: String,
        file: MultipartBody.Part,
        position: RequestBody,
    ): Response<PhotoResponseDto> =
        record("addMyProfilePhotoFile", authorization) { photoResponse }

    override suspend fun deleteMyProfilePhoto(
        authorization: String,
        photoId: String,
    ): Response<ProfileResponseDto> =
        record("deleteMyProfilePhoto", authorization, photoId) { profileResponse }

    override suspend fun replaceMyProfilePhoto(
        authorization: String,
        position: Int,
        body: ReplacePhotoRequestDto,
    ): Response<PhotoResponseDto> =
        record("replaceMyProfilePhoto", authorization, position.toString()) {
            replacePhotoBody = body
            photoResponse
        }

    override suspend fun replaceMyProfilePhotoFile(
        authorization: String,
        photoId: String,
        file: MultipartBody.Part,
    ): Response<PhotoResponseDto> =
        record("replaceMyProfilePhotoFile", authorization, photoId) { photoResponse }

    override suspend fun activateMyProfile(authorization: String): Response<ProfileResponseDto> =
        record("activateMyProfile", authorization) { profileResponse }

    override suspend fun enqueueMatchmaking(
        authorization: String,
        body: EnqueueMatchmakingRequestDto,
    ): Response<QueueStatusResponseDto> =
        record("enqueueMatchmaking", authorization) {
            enqueueBody = body
            queueResponse
        }

    override suspend fun leaveMatchmakingQueue(authorization: String): Response<QueueStatusResponseDto> =
        record("leaveMatchmakingQueue", authorization) { queueResponse }

    override suspend fun getMatchmakingQueueStatus(authorization: String): Response<QueueStatusResponseDto> =
        record("getMatchmakingQueueStatus", authorization) { queueResponse }

    override suspend fun getMatch(
        authorization: String,
        matchId: String,
    ): Response<MatchResponseDto> =
        record("getMatch", authorization, matchId) { matchResponse }

    override suspend fun getFirstChatForMatch(
        authorization: String,
        matchId: String,
    ): Response<ChatResponseDto> =
        record("getFirstChatForMatch", authorization, matchId) { nextChatResponse() }

    override suspend fun submitChatDecision(
        authorization: String,
        matchId: String,
        body: ChatDecisionRequestDto,
    ): Response<MatchResponseDto> =
        record("submitChatDecision", authorization, matchId) {
            chatDecisionBody = body
            matchResponse
        }

    override suspend fun getVisualProfile(
        authorization: String,
        matchId: String,
    ): Response<VisualProfileResponseDto> =
        record("getVisualProfile", authorization, matchId) { visualProfileResponse }

    override suspend fun submitVisualDecision(
        authorization: String,
        matchId: String,
        body: VisualDecisionRequestDto,
    ): Response<MatchResponseDto> =
        record("submitVisualDecision", authorization, matchId) {
            visualDecisionBody = body
            matchResponse
        }

    override suspend fun putMyPersonalMessage(
        authorization: String,
        matchId: String,
        body: com.reals.app.data.dto.PersonalMessageRequestDto,
    ): Response<Unit> =
        record("putMyPersonalMessage", authorization, matchId) {
            personalMessageBody = body
            unitResponse
        }

    override suspend fun getPartnerPersonalMessage(
        authorization: String,
        matchId: String,
    ): Response<PartnerPersonalMessageResponseDto> =
        record("getPartnerPersonalMessage", authorization, matchId) { partnerMessageResponse }

    override suspend fun getChat(
        authorization: String,
        chatId: String,
    ): Response<ChatResponseDto> =
        record("getChat", authorization, chatId) { nextChatResponse() }

    override suspend fun sendChatMessage(
        authorization: String,
        chatId: String,
        body: SendMessageRequestDto,
    ): Response<ChatMessageResponseDto> =
        record("sendChatMessage", authorization, chatId) {
            chatMessageBody = body
            chatMessageResponse
        }

    override suspend fun getChatMessages(
        authorization: String,
        chatId: String,
        afterMessageId: String?,
        afterMessageIdAlias: String?,
    ): Response<JsonElement> =
        record("getChatMessages", authorization, chatId) { chatMessagesResponse }

    override suspend fun requestChatExit(
        authorization: String,
        chatId: String,
        body: ChatExitRequestCreateRequestDto,
    ): Response<ChatExitRequestResponseDto> =
        record("requestChatExit", authorization, chatId) {
            exitBody = body
            exitRequestResponse
        }

    override suspend fun getChatExitRequests(
        authorization: String,
        chatId: String,
    ): Response<List<ChatExitRequestResponseDto>> =
        record("getChatExitRequests", authorization, chatId) { exitRequestsResponse }

    override suspend fun acceptChatExitRequest(
        authorization: String,
        chatId: String,
        exitRequestId: String,
    ): Response<ChatExitOutcomeResponseDto> =
        record("acceptChatExitRequest", authorization, "$chatId/$exitRequestId") { exitOutcomeResponse }

    override suspend fun rejectChatExitRequest(
        authorization: String,
        chatId: String,
        exitRequestId: String,
    ): Response<ChatExitOutcomeResponseDto> =
        record("rejectChatExitRequest", authorization, "$chatId/$exitRequestId") { exitOutcomeResponse }

    override suspend fun timeoutChatExitRequest(
        authorization: String,
        chatId: String,
        exitRequestId: String,
    ): Response<ChatExitOutcomeResponseDto> =
        record("timeoutChatExitRequest", authorization, "$chatId/$exitRequestId") { exitOutcomeResponse }

    override suspend fun cancelChat(
        authorization: String,
        chatId: String,
        body: ChatExitRequestCreateRequestDto,
    ): Response<ChatExitOutcomeResponseDto> =
        record("cancelChat", authorization, chatId) {
            exitBody = body
            exitOutcomeResponse
        }

    override suspend fun safetyCancelChat(
        authorization: String,
        chatId: String,
        body: ChatExitRequestCreateRequestDto,
    ): Response<ChatExitOutcomeResponseDto> =
        record("safetyCancelChat", authorization, chatId) {
            exitBody = body
            exitOutcomeResponse
        }

    override suspend fun getConnection(
        authorization: String,
        connectionId: String,
    ): Response<ConnectionResponseDto> =
        record("getConnection", authorization, connectionId) { connectionResponse }

    override suspend fun getSecondChatForConnection(
        authorization: String,
        connectionId: String,
    ): Response<ChatResponseDto> =
        record("getSecondChatForConnection", authorization, connectionId) { nextChatResponse() }

    override suspend fun dismissSecondChatForConnection(
        authorization: String,
        connectionId: String,
    ): Response<ConnectionDismissalResponseDto> =
        record("dismissSecondChatForConnection", authorization, connectionId) { connectionDismissalResponse }

    override suspend fun getConnectionNegotiation(
        authorization: String,
        connectionId: String,
    ): Response<NegotiationResponseDto> =
        record("getConnectionNegotiation", authorization, connectionId) { negotiationResponse }

    override suspend fun getConnectionProposals(
        authorization: String,
        connectionId: String,
    ): Response<List<ScheduleProposalResponseDto>> =
        record("getConnectionProposals", authorization, connectionId) { proposalsResponse }

    override suspend fun submitConnectionProposals(
        authorization: String,
        connectionId: String,
        body: AddProposalRequestDto,
    ): Response<List<ScheduleProposalResponseDto>> =
        record("submitConnectionProposals", authorization, connectionId) {
            proposalsBody = body
            proposalsResponse
        }

    override suspend fun acceptConnectionProposal(
        authorization: String,
        connectionId: String,
        proposalId: String,
    ): Response<NegotiationResponseDto> =
        record("acceptConnectionProposal", authorization, "$connectionId/$proposalId") { negotiationResponse }

    override suspend fun rejectConnectionNegotiationRound(
        authorization: String,
        connectionId: String,
    ): Response<NegotiationResponseDto> =
        record("rejectConnectionNegotiationRound", authorization, connectionId) { negotiationResponse }

    private fun <T> record(
        name: String,
        authorization: String?,
        pathId: String? = null,
        response: () -> Response<T>,
    ): Response<T> {
        calls = calls + name
        lastAuthorization = authorization
        lastPathId = pathId
        return response()
    }

    private fun nextChatResponse(): Response<ChatResponseDto> =
        if (chatResponseQueue.isNotEmpty()) chatResponseQueue.removeAt(0) else chatResponse
}
