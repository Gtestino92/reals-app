package com.reals.app.di

import android.content.Context
import com.reals.app.BuildConfig
import com.reals.app.core.firebase.FirebaseAuthTokenProvider
import com.reals.app.core.network.ApiExecutor
import com.reals.app.data.api.RealsApiClient
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.AcceptSchedulingProposalUseCase
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddMockProfilePhotoUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetChatUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.GetQueueStatusUseCase
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RejectSchedulingRoundUseCase
import com.reals.app.domain.usecase.ReplaceMockProfilePhotoUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val apiExecutor = ApiExecutor(json)
    private val tokenProvider = FirebaseAuthTokenProvider(appContext)
    private val api = RealsApiClient.create(BuildConfig.REALS_BASE_URL, json)

    val authRepository = FirebaseAuthRepository(appContext)
    private val meRepository = MeRepository(api, tokenProvider, apiExecutor)
    private val profileRepository = ProfileRepository(appContext, api, tokenProvider, apiExecutor)
    private val matchmakingRepository = MatchmakingRepository(api, tokenProvider, apiExecutor)
    private val matchRepository = MatchRepository(api, tokenProvider, apiExecutor)
    private val chatRepository = ChatRepository(api, json, tokenProvider, apiExecutor)
    private val schedulingRepository = SchedulingRepository(api, tokenProvider, apiExecutor)
    val provisionAndLoadProfileUseCase = ProvisionAndLoadProfileUseCase(
        meRepository = meRepository,
        profileRepository = profileRepository,
    )
    val createProfileUseCase = CreateProfileUseCase(profileRepository)
    val updateProfileUseCase = UpdateProfileUseCase(profileRepository)
    val updateMatchFiltersUseCase = UpdateMatchFiltersUseCase(profileRepository)
    val getMeUseCase = GetMeUseCase(meRepository)
    val getHomeUseCase = GetHomeUseCase(meRepository)
    val getProfilePhotosUseCase = GetProfilePhotosUseCase(profileRepository)
    val addMockProfilePhotoUseCase = AddMockProfilePhotoUseCase(profileRepository)
    val addProfilePhotoFileUseCase = AddProfilePhotoFileUseCase(profileRepository)
    val replaceMockProfilePhotoUseCase = ReplaceMockProfilePhotoUseCase(profileRepository)
    val replaceProfilePhotoFileUseCase = ReplaceProfilePhotoFileUseCase(profileRepository)
    val deleteProfilePhotoUseCase = DeleteProfilePhotoUseCase(profileRepository)
    val activateProfileUseCase = ActivateProfileUseCase(profileRepository)
    val reactivateAccountUseCase = ReactivateAccountUseCase(meRepository)
    val deleteAccountUseCase = DeleteAccountUseCase(meRepository, authRepository)
    val enqueueMatchmakingUseCase = EnqueueMatchmakingUseCase(matchmakingRepository)
    val getQueueStatusUseCase = GetQueueStatusUseCase(matchmakingRepository)
    val leaveQueueUseCase = LeaveQueueUseCase(matchmakingRepository)
    val getMatchUseCase = GetMatchUseCase(matchRepository)
    val getFirstChatForMatchUseCase = GetFirstChatForMatchUseCase(matchRepository)
    val submitChatDecisionUseCase = SubmitChatDecisionUseCase(matchRepository)
    val getVisualProfileUseCase = GetVisualProfileUseCase(matchRepository)
    val submitVisualDecisionUseCase = SubmitVisualDecisionUseCase(matchRepository)
    val putMyPersonalMessageUseCase = PutMyPersonalMessageUseCase(matchRepository)
    val getPartnerPersonalMessageUseCase = GetPartnerPersonalMessageUseCase(matchRepository)
    val getChatUseCase = GetChatUseCase(chatRepository)
    val getChatMessagesUseCase = GetChatMessagesUseCase(chatRepository)
    val sendChatMessageUseCase = SendChatMessageUseCase(chatRepository)
    val getChatExitRequestsUseCase = GetChatExitRequestsUseCase(chatRepository)
    val requestMutualChatExitUseCase = RequestMutualChatExitUseCase(chatRepository)
    val acceptChatExitRequestUseCase = AcceptChatExitRequestUseCase(chatRepository)
    val rejectChatExitRequestUseCase = RejectChatExitRequestUseCase(chatRepository)
    val cancelChatUseCase = CancelChatUseCase(chatRepository)
    val safetyCancelChatUseCase = SafetyCancelChatUseCase(chatRepository)
    val getSchedulingNegotiationUseCase = GetSchedulingNegotiationUseCase(schedulingRepository)
    val getSchedulingProposalsUseCase = GetSchedulingProposalsUseCase(schedulingRepository)
    val submitSchedulingProposalsUseCase = SubmitSchedulingProposalsUseCase(schedulingRepository)
    val acceptSchedulingProposalUseCase = AcceptSchedulingProposalUseCase(schedulingRepository)
    val rejectSchedulingRoundUseCase = RejectSchedulingRoundUseCase(schedulingRepository)

    val rootDependencies = RealsRootDependencies(
        session = SessionFeatureDependencies(
            authRepository = authRepository,
            provisionAndLoadProfile = provisionAndLoadProfileUseCase,
            getMe = getMeUseCase,
        ),
        account = AccountFeatureDependencies(
            reactivateAccount = reactivateAccountUseCase,
            deleteAccount = deleteAccountUseCase,
        ),
        profile = ProfileFeatureDependencies(
            createProfile = createProfileUseCase,
            updateProfile = updateProfileUseCase,
            updateMatchFilters = updateMatchFiltersUseCase,
            getProfilePhotos = getProfilePhotosUseCase,
            addMockProfilePhoto = addMockProfilePhotoUseCase,
            addProfilePhotoFile = addProfilePhotoFileUseCase,
            replaceMockProfilePhoto = replaceMockProfilePhotoUseCase,
            replaceProfilePhotoFile = replaceProfilePhotoFileUseCase,
            deleteProfilePhoto = deleteProfilePhotoUseCase,
            activateProfile = activateProfileUseCase,
        ),
        home = HomeFeatureDependencies(
            enqueueMatchmaking = enqueueMatchmakingUseCase,
            getHome = getHomeUseCase,
            leaveQueue = leaveQueueUseCase,
        ),
        firstChat = FirstChatFeatureDependencies(
            getMatch = getMatchUseCase,
            getFirstChatForMatch = getFirstChatForMatchUseCase,
            submitChatDecision = submitChatDecisionUseCase,
            getChatMessages = getChatMessagesUseCase,
            sendChatMessage = sendChatMessageUseCase,
            getChatExitRequests = getChatExitRequestsUseCase,
            requestMutualChatExit = requestMutualChatExitUseCase,
            acceptChatExitRequest = acceptChatExitRequestUseCase,
            rejectChatExitRequest = rejectChatExitRequestUseCase,
            cancelChat = cancelChatUseCase,
            safetyCancelChat = safetyCancelChatUseCase,
        ),
        visualApproval = VisualApprovalFeatureDependencies(
            getMatch = getMatchUseCase,
            getVisualProfile = getVisualProfileUseCase,
            submitVisualDecision = submitVisualDecisionUseCase,
            putMyPersonalMessage = putMyPersonalMessageUseCase,
            getPartnerPersonalMessage = getPartnerPersonalMessageUseCase,
        ),
        scheduling = SchedulingFeatureDependencies(
            getNegotiation = getSchedulingNegotiationUseCase,
            getProposals = getSchedulingProposalsUseCase,
            submitProposals = submitSchedulingProposalsUseCase,
            acceptProposal = acceptSchedulingProposalUseCase,
            rejectRound = rejectSchedulingRoundUseCase,
        ),
    )
}
