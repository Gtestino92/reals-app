package com.reals.app.di

import android.content.Context
import com.reals.app.BuildConfig
import com.reals.app.core.appcheck.FirebaseAppCheckTokenProvider
import com.reals.app.core.firebase.FirebaseAuthTokenProvider
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.time.AndroidElapsedRealtimeClock
import com.reals.app.data.preferences.SharedPreferencesFirstChatUnansweredSuggestionDismissalStore
import com.reals.app.data.api.RealsApiClient
import com.reals.app.data.repository.AffinityQuestionRepository
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.LegalRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.data.repository.ProfileQuestionRepository
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.AcceptSchedulingProposalUseCase
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.BlockMatchParticipantUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.CreateSecondChatCompletionRequestUseCase
import com.reals.app.domain.usecase.CreateSecondChatInactivityClaimUseCase
import com.reals.app.domain.usecase.CreateSecondChatNoShowClaimUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteMyAffinityAnswerUseCase
import com.reals.app.domain.usecase.DeleteMyProfileQuestionAnswerUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.DecideSecondChatCompletionRequestUseCase
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetAffinityQuestionCatalogUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetChatUseCase
import com.reals.app.domain.usecase.GetCurrentLegalDocumentsUseCase
import com.reals.app.domain.usecase.GetCountriesUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetHomePendingUseCase
import com.reals.app.domain.usecase.GetHomeStatusUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.GetLegalStatusUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.GetMyAffinityAnswersUseCase
import com.reals.app.domain.usecase.GetMyProfileQuestionAnswersUseCase
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.GetProfileQuestionCatalogUseCase
import com.reals.app.domain.usecase.GetQueueStatusUseCase
import com.reals.app.domain.usecase.GetSchedulingAvailabilityUseCase
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.GetSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.GetSecondChatStatusUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.JoinSecondChatUseCase
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerifiedUseCase
import com.reals.app.domain.usecase.PatchMyAffinityAnswerUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RejectPartnerSchedulingProposalsUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.ReplaceMyProfileQuestionSelectionsUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.RequestNextFirstChatGuidanceQuestionUseCase
import com.reals.app.domain.usecase.RecordLegalDocumentActionUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatAudioMessageUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.domain.usecase.TimeoutChatExitRequestUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import com.reals.app.domain.usecase.UpsertMyProfileQuestionAnswerUseCase
import com.reals.app.foreground.AtomicForegroundDestinationTracker
import com.reals.app.notifications.NotificationPresentationPolicy
import com.reals.app.notifications.registration.PushTokenRegistrationService
import com.reals.app.ui.root.LocalFirebaseEmailVerificationCoordinator
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val apiExecutor = ApiExecutor(json)
    private val tokenProvider = FirebaseAuthTokenProvider(appContext)
    private val appCheckTokenProvider = if (BuildConfig.ENABLE_FIREBASE_APP_CHECK) {
        FirebaseAppCheckTokenProvider(appContext)
    } else {
        null
    }
    private val api = RealsApiClient.create(BuildConfig.REALS_BASE_URL, json, appCheckTokenProvider)

    val authRepository = FirebaseAuthRepository(appContext)
    val foregroundDestinationTracker = AtomicForegroundDestinationTracker()
    val notificationPresentationPolicy = NotificationPresentationPolicy()
    private val meRepository = MeRepository(api, tokenProvider, apiExecutor)
    private val profileRepository = ProfileRepository(appContext, api, tokenProvider, apiExecutor)
    private val matchmakingRepository = MatchmakingRepository(api, tokenProvider, apiExecutor)
    private val elapsedRealtimeClock = AndroidElapsedRealtimeClock
    private val firstChatUnansweredSuggestionDismissalStore =
        SharedPreferencesFirstChatUnansweredSuggestionDismissalStore(appContext)
    private val matchRepository = MatchRepository(api, elapsedRealtimeClock, tokenProvider, apiExecutor)
    private val chatRepository = ChatRepository(api, json, tokenProvider, apiExecutor)
    private val schedulingRepository = SchedulingRepository(api, tokenProvider, apiExecutor)
    private val legalRepository = LegalRepository(api, tokenProvider, apiExecutor)
    private val affinityQuestionRepository = AffinityQuestionRepository(api, tokenProvider, apiExecutor)
    private val profileQuestionRepository = ProfileQuestionRepository(api, tokenProvider, apiExecutor)
    val provisionAndLoadProfileUseCase = ProvisionAndLoadProfileUseCase(
        meRepository = meRepository,
        profileRepository = profileRepository,
    )
    val createProfileUseCase = CreateProfileUseCase(profileRepository)
    val updateProfileUseCase = UpdateProfileUseCase(profileRepository)
    val getCountriesUseCase = GetCountriesUseCase(profileRepository)
    val updateMatchFiltersUseCase = UpdateMatchFiltersUseCase(profileRepository)
    val getMeUseCase = GetMeUseCase(meRepository)
    val getHomeUseCase = GetHomeUseCase(meRepository)
    val getHomeStatusUseCase = GetHomeStatusUseCase(meRepository)
    val getHomePendingUseCase = GetHomePendingUseCase(meRepository)
    val registerPushTokenUseCase = RegisterPushTokenUseCase(meRepository)
    val pushTokenRegistrationService = PushTokenRegistrationService(appContext, registerPushTokenUseCase)
    val markLocalFirebaseEmailVerifiedUseCase = MarkLocalFirebaseEmailVerifiedUseCase(meRepository)
    val localFirebaseEmailVerificationCoordinator = LocalFirebaseEmailVerificationCoordinator(
        localFirebaseEmailAutoVerificationEnabled = BuildConfig.ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION,
        authRepository = authRepository,
        markLocalFirebaseEmailVerified = markLocalFirebaseEmailVerifiedUseCase,
    )
    val getProfilePhotosUseCase = GetProfilePhotosUseCase(profileRepository)
    val addProfilePhotoFileUseCase = AddProfilePhotoFileUseCase(profileRepository)
    val replaceProfilePhotoFileUseCase = ReplaceProfilePhotoFileUseCase(profileRepository)
    val deleteProfilePhotoUseCase = DeleteProfilePhotoUseCase(profileRepository)
    val reorderProfilePhotosUseCase = ReorderProfilePhotosUseCase(profileRepository)
    val activateProfileUseCase = ActivateProfileUseCase(profileRepository)
    val reactivateAccountUseCase = ReactivateAccountUseCase(meRepository)
    val deleteAccountUseCase = DeleteAccountUseCase(meRepository, authRepository)
    val getCurrentLegalDocumentsUseCase = GetCurrentLegalDocumentsUseCase(legalRepository)
    val getLegalStatusUseCase = GetLegalStatusUseCase(legalRepository)
    val recordLegalDocumentActionUseCase = RecordLegalDocumentActionUseCase(legalRepository)
    val enqueueMatchmakingUseCase = EnqueueMatchmakingUseCase(matchmakingRepository)
    val getQueueStatusUseCase = GetQueueStatusUseCase(matchmakingRepository)
    val leaveQueueUseCase = LeaveQueueUseCase(matchmakingRepository)
    val getMatchUseCase = GetMatchUseCase(matchRepository)
    val blockMatchParticipantUseCase = BlockMatchParticipantUseCase(matchRepository)
    val getFirstChatForMatchUseCase = GetFirstChatForMatchUseCase(matchRepository)
    val submitChatDecisionUseCase = SubmitChatDecisionUseCase(matchRepository)
    val getVisualProfileUseCase = GetVisualProfileUseCase(matchRepository)
    val submitVisualDecisionUseCase = SubmitVisualDecisionUseCase(matchRepository)
    val putMyPersonalMessageUseCase = PutMyPersonalMessageUseCase(matchRepository)
    val getPartnerPersonalMessageUseCase = GetPartnerPersonalMessageUseCase(matchRepository)
    val getChatUseCase = GetChatUseCase(chatRepository)
    val getSecondChatForConnectionUseCase = GetSecondChatForConnectionUseCase(chatRepository)
    val getSecondChatStatusUseCase = GetSecondChatStatusUseCase(chatRepository)
    val joinSecondChatUseCase = JoinSecondChatUseCase(chatRepository)
    val createSecondChatNoShowClaimUseCase = CreateSecondChatNoShowClaimUseCase(chatRepository)
    val createSecondChatCompletionRequestUseCase = CreateSecondChatCompletionRequestUseCase(chatRepository)
    val decideSecondChatCompletionRequestUseCase = DecideSecondChatCompletionRequestUseCase(chatRepository)
    val createSecondChatInactivityClaimUseCase = CreateSecondChatInactivityClaimUseCase(chatRepository)
    val dismissSecondChatForConnectionUseCase = DismissSecondChatForConnectionUseCase(chatRepository)
    val getChatMessagesUseCase = GetChatMessagesUseCase(chatRepository)
    val sendChatMessageUseCase = SendChatMessageUseCase(chatRepository)
    val sendChatAudioMessageUseCase = SendChatAudioMessageUseCase(chatRepository)
    val requestNextFirstChatGuidanceQuestionUseCase = RequestNextFirstChatGuidanceQuestionUseCase(chatRepository)
    val getChatExitRequestsUseCase = GetChatExitRequestsUseCase(chatRepository)
    val requestMutualChatExitUseCase = RequestMutualChatExitUseCase(chatRepository)
    val acceptChatExitRequestUseCase = AcceptChatExitRequestUseCase(chatRepository)
    val rejectChatExitRequestUseCase = RejectChatExitRequestUseCase(chatRepository)
    val timeoutChatExitRequestUseCase = TimeoutChatExitRequestUseCase(chatRepository)
    val cancelChatUseCase = CancelChatUseCase(chatRepository)
    val safetyCancelChatUseCase = SafetyCancelChatUseCase(chatRepository)
    val getSchedulingNegotiationUseCase = GetSchedulingNegotiationUseCase(schedulingRepository)
    val getSchedulingProposalsUseCase = GetSchedulingProposalsUseCase(schedulingRepository)
    val getSchedulingAvailabilityUseCase = GetSchedulingAvailabilityUseCase(schedulingRepository)
    val submitSchedulingProposalsUseCase = SubmitSchedulingProposalsUseCase(schedulingRepository)
    val acceptSchedulingProposalUseCase = AcceptSchedulingProposalUseCase(schedulingRepository)
    val rejectPartnerSchedulingProposalsUseCase = RejectPartnerSchedulingProposalsUseCase(schedulingRepository)
    val getAffinityQuestionCatalogUseCase = GetAffinityQuestionCatalogUseCase(affinityQuestionRepository)
    val getMyAffinityAnswersUseCase = GetMyAffinityAnswersUseCase(affinityQuestionRepository)
    val patchMyAffinityAnswerUseCase = PatchMyAffinityAnswerUseCase(affinityQuestionRepository)
    val deleteMyAffinityAnswerUseCase = DeleteMyAffinityAnswerUseCase(affinityQuestionRepository)
    val getProfileQuestionCatalogUseCase = GetProfileQuestionCatalogUseCase(profileQuestionRepository)
    val getMyProfileQuestionAnswersUseCase = GetMyProfileQuestionAnswersUseCase(profileQuestionRepository)
    val upsertMyProfileQuestionAnswerUseCase = UpsertMyProfileQuestionAnswerUseCase(profileQuestionRepository)
    val deleteMyProfileQuestionAnswerUseCase = DeleteMyProfileQuestionAnswerUseCase(profileQuestionRepository)
    val replaceMyProfileQuestionSelectionsUseCase =
        ReplaceMyProfileQuestionSelectionsUseCase(profileQuestionRepository)

    val rootDependencies = RealsRootDependencies(
        session = SessionFeatureDependencies(
            authRepository = authRepository,
            provisionAndLoadProfile = provisionAndLoadProfileUseCase,
            getMe = getMeUseCase,
            pushTokenRegistrationService = pushTokenRegistrationService,
            markLocalFirebaseEmailVerified = markLocalFirebaseEmailVerifiedUseCase,
            localFirebaseEmailAutoVerificationEnabled =
                BuildConfig.ENABLE_LOCAL_FIREBASE_EMAIL_AUTO_VERIFICATION,
            localFirebaseEmailVerificationCoordinator = localFirebaseEmailVerificationCoordinator,
        ),
        account = AccountFeatureDependencies(
            reactivateAccount = reactivateAccountUseCase,
            deleteAccount = deleteAccountUseCase,
        ),
        legal = LegalFeatureDependencies(
            getCurrentDocuments = getCurrentLegalDocumentsUseCase,
            getStatus = getLegalStatusUseCase,
            recordAction = recordLegalDocumentActionUseCase,
        ),
        profile = ProfileFeatureDependencies(
            createProfile = createProfileUseCase,
            updateProfile = updateProfileUseCase,
            getCountries = getCountriesUseCase,
            updateMatchFilters = updateMatchFiltersUseCase,
            getProfilePhotos = getProfilePhotosUseCase,
            addProfilePhotoFile = addProfilePhotoFileUseCase,
            replaceProfilePhotoFile = replaceProfilePhotoFileUseCase,
            deleteProfilePhoto = deleteProfilePhotoUseCase,
            reorderProfilePhotos = reorderProfilePhotosUseCase,
            activateProfile = activateProfileUseCase,
        ),
        home = HomeFeatureDependencies(
            enqueueMatchmaking = enqueueMatchmakingUseCase,
            getHome = getHomeUseCase,
            getHomeStatus = getHomeStatusUseCase,
            getHomePending = getHomePendingUseCase,
            leaveQueue = leaveQueueUseCase,
            dismissSecondChat = dismissSecondChatForConnectionUseCase,
        ),
        manualBlock = ManualBlockFeatureDependencies(
            blockMatchParticipant = blockMatchParticipantUseCase,
        ),
        firstChat = FirstChatFeatureDependencies(
            getMatch = getMatchUseCase,
            getFirstChatForMatch = getFirstChatForMatchUseCase,
            submitChatDecision = submitChatDecisionUseCase,
            getChatMessages = getChatMessagesUseCase,
            sendChatMessage = sendChatMessageUseCase,
            sendChatAudioMessage = sendChatAudioMessageUseCase,
            requestNextFirstChatGuidanceQuestion = requestNextFirstChatGuidanceQuestionUseCase,
            getChatExitRequests = getChatExitRequestsUseCase,
            requestMutualChatExit = requestMutualChatExitUseCase,
            acceptChatExitRequest = acceptChatExitRequestUseCase,
            rejectChatExitRequest = rejectChatExitRequestUseCase,
            timeoutChatExitRequest = timeoutChatExitRequestUseCase,
            cancelChat = cancelChatUseCase,
            safetyCancelChat = safetyCancelChatUseCase,
            unansweredSuggestionDismissalStore = firstChatUnansweredSuggestionDismissalStore,
        ),
        secondChat = SecondChatFeatureDependencies(
            getStatus = getSecondChatStatusUseCase,
            join = joinSecondChatUseCase,
            createNoShowClaim = createSecondChatNoShowClaimUseCase,
            getChat = getChatUseCase,
            getSecondChatForConnection = getSecondChatForConnectionUseCase,
            getChatMessages = getChatMessagesUseCase,
            sendChatMessage = sendChatMessageUseCase,
            sendChatAudioMessage = sendChatAudioMessageUseCase,
            safetyCancelChat = safetyCancelChatUseCase,
            createCompletionRequest = createSecondChatCompletionRequestUseCase,
            decideCompletionRequest = decideSecondChatCompletionRequestUseCase,
            createInactivityClaim = createSecondChatInactivityClaimUseCase,
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
            getAvailability = getSchedulingAvailabilityUseCase,
            submitProposals = submitSchedulingProposalsUseCase,
            acceptProposal = acceptSchedulingProposalUseCase,
            rejectPartnerProposals = rejectPartnerSchedulingProposalsUseCase,
        ),
        affinity = AffinityFeatureDependencies(
            getCatalog = getAffinityQuestionCatalogUseCase,
            getMyAnswers = getMyAffinityAnswersUseCase,
            patchAnswer = patchMyAffinityAnswerUseCase,
            deleteAnswer = deleteMyAffinityAnswerUseCase,
        ),
        profileQuestions = ProfileQuestionFeatureDependencies(
            getCatalog = getProfileQuestionCatalogUseCase,
            getMyAnswers = getMyProfileQuestionAnswersUseCase,
            upsertAnswer = upsertMyProfileQuestionAnswerUseCase,
            deleteAnswer = deleteMyProfileQuestionAnswerUseCase,
            replaceSelections = replaceMyProfileQuestionSelectionsUseCase,
        ),
    )
}
