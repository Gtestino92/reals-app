package com.reals.app.testutil

import com.reals.app.core.network.ApiExecutor
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.AddProposalRequestDto
import com.reals.app.data.dto.AffinityAnswersResponseDto
import com.reals.app.data.dto.AffinityQuestionCatalogResponseDto
import com.reals.app.data.dto.ChatDecisionRequestDto
import com.reals.app.data.dto.ChatExitOutcomeResponseDto
import com.reals.app.data.dto.ChatExitRequestCreateRequestDto
import com.reals.app.data.dto.ChatExitRequestResponseDto
import com.reals.app.data.dto.ChatMessageResponseDto
import com.reals.app.data.dto.ChatResponseDto
import com.reals.app.data.dto.ConnectionDismissalResponseDto
import com.reals.app.data.dto.ConnectionResponseDto
import com.reals.app.data.dto.CountryReferenceResponseDto
import com.reals.app.data.dto.CreateProfileRequestDto
import com.reals.app.data.dto.CurrentLegalDocumentsResponseDto
import com.reals.app.data.dto.EnqueueMatchmakingRequestDto
import com.reals.app.data.dto.FirstChatGuidanceResponseDto
import com.reals.app.data.dto.HomeResponseDto
import com.reals.app.data.dto.HomePendingStateResponseDto
import com.reals.app.data.dto.HomeStatusResponseDto
import com.reals.app.data.dto.LegalDocumentActionResponseDto
import com.reals.app.data.dto.LegalStatusResponseDto
import com.reals.app.data.dto.MatchResponseDto
import com.reals.app.data.dto.NegotiationResponseDto
import com.reals.app.data.dto.PartnerPersonalMessageResponseDto
import com.reals.app.data.dto.PhotoResponseDto
import com.reals.app.data.dto.PasswordResetRequestDto
import com.reals.app.data.dto.PingResponseDto
import com.reals.app.data.dto.PatchAffinityAnswersRequestDto
import com.reals.app.data.dto.ProfileQuestionAnswersResponseDto
import com.reals.app.data.dto.ProfileQuestionCatalogResponseDto
import com.reals.app.data.dto.ProfileResponseDto
import com.reals.app.data.dto.PutMessageReactionRequestDto
import com.reals.app.data.dto.QueueStatusResponseDto
import com.reals.app.data.dto.RegisterPushTokenRequestDto
import com.reals.app.data.dto.RegisterPushTokenResponseDto
import com.reals.app.data.dto.RecordLegalDocumentActionRequestDto
import com.reals.app.data.dto.ReplaceProfileQuestionSelectionsRequestDto
import com.reals.app.data.dto.ReorderProfilePhotosRequestDto
import com.reals.app.data.dto.RejectPartnerProposalsRequestDto
import com.reals.app.data.dto.ScheduleProposalResponseDto
import com.reals.app.data.dto.SchedulingAvailabilityResponseDto
import com.reals.app.data.dto.SecondChatAttendanceResponseDto
import com.reals.app.data.dto.SecondChatCompletionDecisionRequestDto
import com.reals.app.data.dto.SendMessageRequestDto
import com.reals.app.data.dto.UpdateMatchFiltersRequestDto
import com.reals.app.data.dto.UpdateProfileRequestDto
import com.reals.app.data.dto.UpsertProfileQuestionAnswerRequestDto
import com.reals.app.data.dto.UserResponseDto
import com.reals.app.data.dto.UserBlockResponseDto
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
    var chatAudioFilePart: MultipartBody.Part? = null
        private set
    var chatAudioClientMessageIdPart: RequestBody? = null
        private set
    var chatMessageReactionBody: PutMessageReactionRequestDto? = null
        private set
    var lastChatMessagesLimit: Int? = null
        private set
    var lastChatMessagesAfter: String? = null
        private set
    var lastChatMessagesAfterAlias: String? = null
        private set
    var exitBody: ChatExitRequestCreateRequestDto? = null
        private set
    var visualDecisionBody: VisualDecisionRequestDto? = null
        private set
    var personalMessageBody: com.reals.app.data.dto.PersonalMessageRequestDto? = null
        private set
    var proposalsBody: AddProposalRequestDto? = null
        private set
    var rejectPartnerProposalsBody: RejectPartnerProposalsRequestDto? = null
        private set
    var completionDecisionBody: SecondChatCompletionDecisionRequestDto? = null
        private set
    var createProfileBody: CreateProfileRequestDto? = null
        private set
    var updateProfileBody: UpdateProfileRequestDto? = null
        private set
    var updateFiltersBody: UpdateMatchFiltersRequestDto? = null
        private set
    var reorderPhotosBody: ReorderProfilePhotosRequestDto? = null
        private set
    var registerPushTokenBody: RegisterPushTokenRequestDto? = null
        private set
    var passwordResetBody: PasswordResetRequestDto? = null
        private set
    var legalActionBody: RecordLegalDocumentActionRequestDto? = null
        private set
    var patchAffinityAnswersBody: PatchAffinityAnswersRequestDto? = null
        private set
    var upsertProfileQuestionAnswerBody: UpsertProfileQuestionAnswerRequestDto? = null
        private set
    var replaceProfileQuestionSelectionsBody: ReplaceProfileQuestionSelectionsRequestDto? = null
        private set
    var blockMatchIds: List<String> = emptyList()
        private set

    var beforeGetHomeResponse: suspend () -> Unit = {}
    var beforeGetHomeStatusResponse: suspend () -> Unit = {}
    var beforeGetMatchResponse: suspend () -> Unit = {}
    var beforeGetVisualProfileResponse: suspend () -> Unit = {}
    var beforeGetFirstChatForMatchResponse: suspend () -> Unit = {}
    var beforeGetChatResponse: suspend () -> Unit = {}
    var beforeGetChatMessagesResponse: suspend () -> Unit = {}
    var beforeSendChatMessageResponse: suspend () -> Unit = {}
    var beforeSendChatAudioMessageResponse: suspend () -> Unit = {}
    var beforeGetChatExitRequestsResponse: suspend () -> Unit = {}
    var beforeGetSecondChatStatusResponse: suspend () -> Unit = {}
    var beforeGetConnectionNegotiationResponse: suspend () -> Unit = {}
    var beforeGetConnectionSchedulingAvailabilityResponse: suspend () -> Unit = {}
    var beforeSubmitConnectionProposalsResponse: suspend () -> Unit = {}
    var beforeAcceptConnectionProposalResponse: suspend () -> Unit = {}
    var beforeRejectConnectionPartnerProposalsResponse: suspend () -> Unit = {}
    var beforeGetPartnerPersonalMessageResponse: suspend () -> Unit = {}
    var beforeGetProfilePhotosResponse: suspend () -> Unit = {}
    var beforeReorderPhotosResponse: suspend () -> Unit = {}
    var beforeAddPhotoResponse: suspend () -> Unit = {}
    var beforeReplacePhotoResponse: suspend () -> Unit = {}
    var beforeGetAffinityQuestionCatalogResponse: suspend () -> Unit = {}
    var beforeGetMyAffinityAnswersResponse: suspend () -> Unit = {}
    var beforePatchMyAffinityAnswersResponse: suspend () -> Unit = {}
    var beforeDeleteMyAffinityAnswerResponse: suspend () -> Unit = {}
    var beforeGetProfileQuestionCatalogResponse: suspend () -> Unit = {}
    var beforeGetMyProfileQuestionAnswersResponse: suspend () -> Unit = {}
    var beforePasswordResetResponse: suspend () -> Unit = {}
    var beforeUpsertMyProfileQuestionAnswerResponse: suspend () -> Unit = {}
    var beforeDeleteMyProfileQuestionAnswerResponse: suspend () -> Unit = {}
    var beforeReplaceMyProfileQuestionSelectionsResponse: suspend () -> Unit = {}

    var pingResponse: Response<PingResponseDto> = Response.success(PingResponseDto("ok"))
    var userResponse: Response<UserResponseDto> = Response.success(TestDtos.user())
    var getMeResponse: Response<UserResponseDto>? = null
    var provisionMeResponse: Response<UserResponseDto>? = null
    var deleteMeResponse: Response<Unit> = Response.success(Unit)
    var passwordResetResponse: Response<Unit> = Response.success(Unit)
    var finalizeMyDeletionResponse: Response<UserResponseDto> = Response.success(TestDtos.user(status = "DELETED"))
    var homeResponse: Response<HomeResponseDto> = Response.success(TestDtos.homeWithoutPendingEngagements())
    var homeStatusResponse: Response<HomeStatusResponseDto> = Response.success(TestDtos.homeStatus())
    var homePendingResponse: Response<HomePendingStateResponseDto> = Response.success(TestDtos.homePending())
    var registerPushTokenResponse: Response<RegisterPushTokenResponseDto> =
        Response.success(RegisterPushTokenResponseDto(registered = true))
    var localFirebaseEmailVerificationResponse: Response<Unit> = Response.success(Unit)
    var currentLegalDocumentsResponse: Response<CurrentLegalDocumentsResponseDto> =
        Response.success(TestDtos.currentLegalDocuments())
    var legalStatusResponse: Response<LegalStatusResponseDto> = Response.success(TestDtos.legalStatus())
    var legalActionResponse: Response<LegalDocumentActionResponseDto> = Response.success(TestDtos.legalAction())
    var profileResponse: Response<ProfileResponseDto> = Response.success(TestDtos.profile())
    var countriesResponse: Response<List<CountryReferenceResponseDto>> =
        Response.success(listOf(TestDtos.country("AR", "Argentina"), TestDtos.country("BR", "Brasil")))
    var photosResponse: Response<List<PhotoResponseDto>> = Response.success(listOf(TestDtos.photo()))
    var reorderPhotosResponse: Response<List<PhotoResponseDto>> = Response.success(listOf(TestDtos.photo()))
    var photoResponse: Response<PhotoResponseDto> = Response.success(TestDtos.photo())
    var queueResponse: Response<QueueStatusResponseDto> = Response.success(TestDtos.queueStatus())
    var matchResponse: Response<MatchResponseDto> = Response.success(TestDtos.match())
    var userBlockResponse: Response<UserBlockResponseDto> = Response.success(TestDtos.userBlock())
    var chatResponse: Response<ChatResponseDto> = Response.success(TestDtos.chat())
    var chatResponseQueue: MutableList<Response<ChatResponseDto>> = mutableListOf()
    var secondChatStatusResponse: Response<SecondChatAttendanceResponseDto> =
        Response.success(TestDtos.secondChatStatus())
    var secondChatStatusResponseQueue: MutableList<Response<SecondChatAttendanceResponseDto>> = mutableListOf()
    var visualProfileResponse: Response<VisualProfileResponseDto> = Response.success(TestDtos.visualProfile())
    var unitResponse: Response<Unit> = Response.success(Unit)
    var partnerMessageResponse: Response<PartnerPersonalMessageResponseDto> =
        Response.success(PartnerPersonalMessageResponseDto("hola"))
    var chatMessageResponse: Response<ChatMessageResponseDto> = Response.success(TestDtos.chatMessage())
    var chatMessageReactionResponse: Response<ChatMessageResponseDto> = Response.success(
        TestDtos.chatMessage().copy(reactionType = "HEART")
    )
    var chatAudioMessageResponse: Response<ChatMessageResponseDto> =
        Response.success(TestDtos.audioChatMessage())
    var chatMessagesResponse: Response<JsonElement> = Response.success(TestDtos.chatMessagesArrayPayload())
    var firstChatGuidanceResponse: Response<FirstChatGuidanceResponseDto> =
        Response.success(TestDtos.firstChatGuidance())
    var exitRequestResponse: Response<ChatExitRequestResponseDto> = Response.success(TestDtos.exitRequest())
    var exitRequestsResponse: Response<List<ChatExitRequestResponseDto>> = Response.success(listOf(TestDtos.exitRequest()))
    var exitOutcomeResponse: Response<ChatExitOutcomeResponseDto> = Response.success(TestDtos.exitOutcome())
    var connectionResponse: Response<ConnectionResponseDto> = Response.success(TestDtos.connection())
    var connectionDismissalResponse: Response<ConnectionDismissalResponseDto> =
        Response.success(ConnectionDismissalResponseDto(dismissed = true))
    var negotiationResponse: Response<NegotiationResponseDto> = Response.success(TestDtos.negotiation())
    var proposalsResponse: Response<List<ScheduleProposalResponseDto>> = Response.success(listOf(TestDtos.proposal()))
    var schedulingAvailabilityResponse: Response<SchedulingAvailabilityResponseDto> =
        Response.success(TestDtos.schedulingAvailability())
    var submitProposalsResponse: Response<List<ScheduleProposalResponseDto>>? = null
    var acceptProposalResponse: Response<NegotiationResponseDto>? = null
    var rejectPartnerProposalsResponse: Response<NegotiationResponseDto>? = null
    var affinityQuestionCatalogResponse: Response<AffinityQuestionCatalogResponseDto> =
        Response.success(TestDtos.affinityQuestionCatalog())
    var affinityAnswersResponse: Response<AffinityAnswersResponseDto> =
        Response.success(TestDtos.affinityAnswers())
    var profileQuestionCatalogResponse: Response<ProfileQuestionCatalogResponseDto> =
        Response.success(TestDtos.profileQuestionCatalog())
    var profileQuestionAnswersResponse: Response<ProfileQuestionAnswersResponseDto> =
        Response.success(TestDtos.profileQuestionAnswers())

    override suspend fun ping(): Response<PingResponseDto> = record("ping", null) { pingResponse }

    override suspend fun provisionMe(authorization: String): Response<UserResponseDto> =
        record("provisionMe", authorization) { provisionMeResponse ?: userResponse }

    override suspend fun getMe(authorization: String): Response<UserResponseDto> =
        record("getMe", authorization) { getMeResponse ?: userResponse }

    override suspend fun requestPasswordReset(body: PasswordResetRequestDto): Response<Unit> =
        record("requestPasswordReset", null, beforeResponse = beforePasswordResetResponse) {
            passwordResetBody = body
            passwordResetResponse
        }

    override suspend fun getHome(authorization: String): Response<HomeResponseDto> =
        record("getHome", authorization, beforeResponse = beforeGetHomeResponse) { homeResponse }

    override suspend fun getHomeStatus(authorization: String): Response<HomeStatusResponseDto> =
        record("getHomeStatus", authorization, beforeResponse = beforeGetHomeStatusResponse) { homeStatusResponse }

    override suspend fun getHomePending(authorization: String): Response<HomePendingStateResponseDto> =
        record("getHomePending", authorization) { homePendingResponse }

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

    override suspend fun finalizeMyDeletion(authorization: String): Response<UserResponseDto> =
        record("finalizeMyDeletion", authorization) { finalizeMyDeletionResponse }

    override suspend fun markCurrentFirebaseEmailVerifiedForLocalDevelopment(
        authorization: String,
    ): Response<Unit> =
        record("markCurrentFirebaseEmailVerifiedForLocalDevelopment", authorization) {
            localFirebaseEmailVerificationResponse
        }

    override suspend fun getCurrentLegalDocuments(): Response<CurrentLegalDocumentsResponseDto> =
        record("getCurrentLegalDocuments", null) { currentLegalDocumentsResponse }

    override suspend fun getMyLegalStatus(authorization: String): Response<LegalStatusResponseDto> =
        record("getMyLegalStatus", authorization) { legalStatusResponse }

    override suspend fun recordMyLegalDocumentAction(
        authorization: String,
        body: RecordLegalDocumentActionRequestDto,
    ): Response<LegalDocumentActionResponseDto> =
        record("recordMyLegalDocumentAction", authorization) {
            legalActionBody = body
            legalActionResponse
        }

    override suspend fun getMyProfile(authorization: String): Response<ProfileResponseDto> =
        record("getMyProfile", authorization) { profileResponse }

    override suspend fun getCountries(authorization: String): Response<List<CountryReferenceResponseDto>> =
        record("getCountries", authorization) { countriesResponse }

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
        record("getMyProfilePhotos", authorization, beforeResponse = beforeGetProfilePhotosResponse) { photosResponse }

    override suspend fun reorderMyProfilePhotos(
        authorization: String,
        body: ReorderProfilePhotosRequestDto,
    ): Response<List<PhotoResponseDto>> =
        record("reorderMyProfilePhotos", authorization, beforeResponse = beforeReorderPhotosResponse) {
            reorderPhotosBody = body
            reorderPhotosResponse
        }

    override suspend fun addMyProfilePhotoFile(
        authorization: String,
        file: MultipartBody.Part,
        position: RequestBody,
    ): Response<PhotoResponseDto> =
        record("addMyProfilePhotoFile", authorization, beforeResponse = beforeAddPhotoResponse) { photoResponse }

    override suspend fun deleteMyProfilePhoto(
        authorization: String,
        photoId: String,
    ): Response<ProfileResponseDto> =
        record("deleteMyProfilePhoto", authorization, photoId) { profileResponse }

    override suspend fun replaceMyProfilePhotoFile(
        authorization: String,
        photoId: String,
        file: MultipartBody.Part,
    ): Response<PhotoResponseDto> =
        record("replaceMyProfilePhotoFile", authorization, photoId, beforeResponse = beforeReplacePhotoResponse) {
            photoResponse
        }

    override suspend fun activateMyProfile(authorization: String): Response<ProfileResponseDto> =
        record("activateMyProfile", authorization) { profileResponse }

    override suspend fun getAffinityQuestionCatalog(
        authorization: String,
    ): Response<AffinityQuestionCatalogResponseDto> =
        record(
            "getAffinityQuestionCatalog",
            authorization,
            beforeResponse = beforeGetAffinityQuestionCatalogResponse,
        ) {
            affinityQuestionCatalogResponse
        }

    override suspend fun getMyAffinityAnswers(
        authorization: String,
    ): Response<AffinityAnswersResponseDto> =
        record(
            "getMyAffinityAnswers",
            authorization,
            beforeResponse = beforeGetMyAffinityAnswersResponse,
        ) {
            affinityAnswersResponse
        }

    override suspend fun patchMyAffinityAnswers(
        authorization: String,
        body: PatchAffinityAnswersRequestDto,
    ): Response<AffinityAnswersResponseDto> =
        record(
            "patchMyAffinityAnswers",
            authorization,
            beforeResponse = beforePatchMyAffinityAnswersResponse,
        ) {
            patchAffinityAnswersBody = body
            affinityAnswersResponse
        }

    override suspend fun deleteMyAffinityAnswer(
        authorization: String,
        questionId: String,
    ): Response<AffinityAnswersResponseDto> =
        record(
            "deleteMyAffinityAnswer",
            authorization,
            questionId,
            beforeResponse = beforeDeleteMyAffinityAnswerResponse,
        ) {
            affinityAnswersResponse
        }

    override suspend fun getProfileQuestionCatalog(
        authorization: String,
    ): Response<ProfileQuestionCatalogResponseDto> =
        record(
            "getProfileQuestionCatalog",
            authorization,
            beforeResponse = beforeGetProfileQuestionCatalogResponse,
        ) {
            profileQuestionCatalogResponse
        }

    override suspend fun getMyProfileQuestionAnswers(
        authorization: String,
    ): Response<ProfileQuestionAnswersResponseDto> =
        record(
            "getMyProfileQuestionAnswers",
            authorization,
            beforeResponse = beforeGetMyProfileQuestionAnswersResponse,
        ) {
            profileQuestionAnswersResponse
        }

    override suspend fun upsertMyProfileQuestionAnswer(
        authorization: String,
        questionId: String,
        body: UpsertProfileQuestionAnswerRequestDto,
    ): Response<ProfileQuestionAnswersResponseDto> =
        record(
            "upsertMyProfileQuestionAnswer",
            authorization,
            questionId,
            beforeResponse = beforeUpsertMyProfileQuestionAnswerResponse,
        ) {
            upsertProfileQuestionAnswerBody = body
            profileQuestionAnswersResponse
        }

    override suspend fun deleteMyProfileQuestionAnswer(
        authorization: String,
        questionId: String,
    ): Response<ProfileQuestionAnswersResponseDto> =
        record(
            "deleteMyProfileQuestionAnswer",
            authorization,
            questionId,
            beforeResponse = beforeDeleteMyProfileQuestionAnswerResponse,
        ) {
            profileQuestionAnswersResponse
        }

    override suspend fun replaceMyProfileQuestionSelections(
        authorization: String,
        body: ReplaceProfileQuestionSelectionsRequestDto,
    ): Response<ProfileQuestionAnswersResponseDto> =
        record(
            "replaceMyProfileQuestionSelections",
            authorization,
            beforeResponse = beforeReplaceMyProfileQuestionSelectionsResponse,
        ) {
            replaceProfileQuestionSelectionsBody = body
            profileQuestionAnswersResponse
        }

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
        record("getMatch", authorization, matchId, beforeResponse = beforeGetMatchResponse) { matchResponse }

    override suspend fun blockMatchParticipant(
        authorization: String,
        matchId: String,
    ): Response<UserBlockResponseDto> =
        record("blockMatchParticipant", authorization, matchId) {
            blockMatchIds = blockMatchIds + matchId
            userBlockResponse
        }

    override suspend fun getFirstChatForMatch(
        authorization: String,
        matchId: String,
    ): Response<ChatResponseDto> =
        record(
            "getFirstChatForMatch",
            authorization,
            matchId,
            beforeResponse = beforeGetFirstChatForMatchResponse,
        ) { nextChatResponse() }

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
        record("getVisualProfile", authorization, matchId, beforeResponse = beforeGetVisualProfileResponse) {
            visualProfileResponse
        }

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
        record(
            "getPartnerPersonalMessage",
            authorization,
            matchId,
            beforeResponse = beforeGetPartnerPersonalMessageResponse,
        ) { partnerMessageResponse }

    override suspend fun getChat(
        authorization: String,
        chatId: String,
    ): Response<ChatResponseDto> =
        record("getChat", authorization, chatId, beforeResponse = beforeGetChatResponse) { nextChatResponse() }

    override suspend fun sendChatMessage(
        authorization: String,
        chatId: String,
        body: SendMessageRequestDto,
    ): Response<ChatMessageResponseDto> =
        record("sendChatMessage", authorization, chatId, beforeResponse = beforeSendChatMessageResponse) {
            chatMessageBody = body
            chatMessageResponse
        }

    override suspend fun sendChatAudioMessage(
        authorization: String,
        chatId: String,
        file: MultipartBody.Part,
        clientMessageId: RequestBody,
    ): Response<ChatMessageResponseDto> =
        record("sendChatAudioMessage", authorization, chatId, beforeResponse = beforeSendChatAudioMessageResponse) {
            chatAudioFilePart = file
            chatAudioClientMessageIdPart = clientMessageId
            chatAudioMessageResponse
        }

    override suspend fun putChatMessageReaction(
        authorization: String,
        chatId: String,
        messageId: String,
        body: PutMessageReactionRequestDto,
    ): Response<ChatMessageResponseDto> =
        record("putChatMessageReaction", authorization, "$chatId/$messageId", beforeResponse = {}) {
            chatMessageReactionBody = body
            chatMessageReactionResponse
        }

    override suspend fun getChatMessages(
        authorization: String,
        chatId: String,
        afterMessageId: String?,
        afterMessageIdAlias: String?,
        limit: Int?,
    ): Response<JsonElement> =
        record("getChatMessages", authorization, chatId, beforeResponse = beforeGetChatMessagesResponse) {
            lastChatMessagesLimit = limit
            lastChatMessagesAfter = afterMessageId
            lastChatMessagesAfterAlias = afterMessageIdAlias
            chatMessagesResponse
        }

    override suspend fun requestNextFirstChatGuidanceQuestion(
        authorization: String,
        chatId: String,
    ): Response<FirstChatGuidanceResponseDto> =
        record("requestNextFirstChatGuidanceQuestion", authorization, chatId) { firstChatGuidanceResponse }

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
        record("getChatExitRequests", authorization, chatId, beforeResponse = beforeGetChatExitRequestsResponse) {
            exitRequestsResponse
        }

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

    override suspend fun getSecondChatStatus(
        authorization: String,
        connectionId: String,
    ): Response<SecondChatAttendanceResponseDto> =
        record("getSecondChatStatus", authorization, connectionId, beforeResponse = beforeGetSecondChatStatusResponse) {
            nextSecondChatStatusResponse()
        }

    override suspend fun joinSecondChat(
        authorization: String,
        connectionId: String,
    ): Response<SecondChatAttendanceResponseDto> =
        record("joinSecondChat", authorization, connectionId) { nextSecondChatStatusResponse() }

    override suspend fun createSecondChatNoShowClaim(
        authorization: String,
        connectionId: String,
    ): Response<SecondChatAttendanceResponseDto> =
        record("createSecondChatNoShowClaim", authorization, connectionId) { nextSecondChatStatusResponse() }

    override suspend fun createSecondChatCompletionRequest(
        authorization: String,
        connectionId: String,
    ): Response<SecondChatAttendanceResponseDto> =
        record("createSecondChatCompletionRequest", authorization, connectionId) { nextSecondChatStatusResponse() }

    override suspend fun decideSecondChatCompletionRequest(
        authorization: String,
        connectionId: String,
        requestId: String,
        body: SecondChatCompletionDecisionRequestDto,
    ): Response<SecondChatAttendanceResponseDto> =
        record("decideSecondChatCompletionRequest", authorization, "$connectionId/$requestId") {
            completionDecisionBody = body
            nextSecondChatStatusResponse()
        }

    override suspend fun createSecondChatInactivityClaim(
        authorization: String,
        connectionId: String,
    ): Response<SecondChatAttendanceResponseDto> =
        record("createSecondChatInactivityClaim", authorization, connectionId) { nextSecondChatStatusResponse() }

    override suspend fun dismissSecondChatForConnection(
        authorization: String,
        connectionId: String,
    ): Response<ConnectionDismissalResponseDto> =
        record("dismissSecondChatForConnection", authorization, connectionId) { connectionDismissalResponse }

    override suspend fun getConnectionNegotiation(
        authorization: String,
        connectionId: String,
    ): Response<NegotiationResponseDto> =
        record(
            "getConnectionNegotiation",
            authorization,
            connectionId,
            beforeResponse = beforeGetConnectionNegotiationResponse,
        ) { negotiationResponse }

    override suspend fun getConnectionProposals(
        authorization: String,
        connectionId: String,
    ): Response<List<ScheduleProposalResponseDto>> =
        record("getConnectionProposals", authorization, connectionId) { proposalsResponse }

    override suspend fun getConnectionSchedulingAvailability(
        authorization: String,
        connectionId: String,
    ): Response<SchedulingAvailabilityResponseDto> =
        record(
            "getConnectionSchedulingAvailability",
            authorization,
            connectionId,
            beforeResponse = beforeGetConnectionSchedulingAvailabilityResponse,
        ) { schedulingAvailabilityResponse }

    override suspend fun submitConnectionProposals(
        authorization: String,
        connectionId: String,
        body: AddProposalRequestDto,
    ): Response<List<ScheduleProposalResponseDto>> =
        record("submitConnectionProposals", authorization, connectionId, beforeResponse = beforeSubmitConnectionProposalsResponse) {
            proposalsBody = body
            submitProposalsResponse ?: proposalsResponse
        }

    override suspend fun acceptConnectionProposal(
        authorization: String,
        connectionId: String,
        proposalId: String,
    ): Response<NegotiationResponseDto> =
        record(
            "acceptConnectionProposal",
            authorization,
            "$connectionId/$proposalId",
            beforeResponse = beforeAcceptConnectionProposalResponse,
        ) { acceptProposalResponse ?: negotiationResponse }

    override suspend fun rejectConnectionPartnerProposals(
        authorization: String,
        connectionId: String,
        body: RejectPartnerProposalsRequestDto,
    ): Response<NegotiationResponseDto> =
        record(
            "rejectConnectionPartnerProposals",
            authorization,
            connectionId,
            beforeResponse = beforeRejectConnectionPartnerProposalsResponse,
        ) {
            rejectPartnerProposalsBody = body
            rejectPartnerProposalsResponse ?: negotiationResponse
        }

    private suspend fun <T> record(
        name: String,
        authorization: String?,
        pathId: String? = null,
        beforeResponse: suspend () -> Unit = {},
        response: () -> Response<T>,
    ): Response<T> {
        calls = calls + name
        lastAuthorization = authorization
        lastPathId = pathId
        beforeResponse()
        return response()
    }

    private fun nextChatResponse(): Response<ChatResponseDto> =
        if (chatResponseQueue.isNotEmpty()) chatResponseQueue.removeAt(0) else chatResponse

    private fun nextSecondChatStatusResponse(): Response<SecondChatAttendanceResponseDto> =
        if (secondChatStatusResponseQueue.isNotEmpty()) {
            secondChatStatusResponseQueue.removeAt(0)
        } else {
            secondChatStatusResponse
        }
}
