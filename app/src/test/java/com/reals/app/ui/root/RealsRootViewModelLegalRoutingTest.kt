package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.data.mapper.toDomain
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.LegalRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.di.LegalFeatureDependencies
import com.reals.app.di.ManualBlockFeatureDependencies
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.di.RealsRootDependencies
import com.reals.app.di.SchedulingFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.AcceptSchedulingProposalUseCase
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.BlockMatchParticipantUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
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
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.GetSchedulingAvailabilityUseCase
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.GetSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RejectPartnerSchedulingProposalsUseCase
import com.reals.app.domain.usecase.RecordLegalDocumentActionUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.RequestNextFirstChatGuidanceQuestionUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.domain.usecase.TimeoutChatExitRequestUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import com.reals.app.notifications.registration.PushTokenRegistrationService
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelLegalRoutingTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unsatisfied bootstrap can be deferred without recording legal actions`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            configureUnsatisfiedLegal()
            homeResponse = Response.success(emptyHome())
        }
        val viewModel = viewModel(api)
        runCurrent()

        viewModel.signIn("user@example.com", "password")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.LegalRequirements)

        viewModel.deferLegalRequirements()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(0, api.calls.count { it == "recordMyLegalDocumentAction" })
    }

    @Test
    fun `existing first chat is restored after reactive legal rejection and defer`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply { configureUnsatisfiedLegal() }
        val viewModel = viewModel(api)
        runCurrent()
        val message = TestDtos.chatMessage("message-42").toDomain()
        val optimistic = newOptimisticOutgoingMessage(
            chatId = "chat-42",
            senderId = "user-1",
            content = "mensaje pendiente",
            localId = "local-42",
        ).copy(deliveryState = OutgoingMessageDeliveryState.Failed)
        val firstChat = RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-42",
            chatId = "chat-42",
            messages = listOf(message),
            optimisticMessages = listOf(optimistic),
            error = legalActionRequiredError(),
        )

        viewModel.setState(firstChat)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.LegalRequirements)

        viewModel.deferLegalRequirements()
        advanceUntilIdle()

        val restored = viewModel.uiState.value as RealsRootUiState.FirstChat
        assertEquals("match-42", restored.matchId)
        assertEquals("chat-42", restored.chatId)
        assertEquals(listOf(message), restored.messages)
        assertEquals(listOf(optimistic), restored.optimisticMessages)
        assertNull(restored.error)
        assertEquals(0, api.calls.count { it == "sendChatMessage" })
        assertEquals(0, api.calls.count { it == "submitChatDecision" })
    }

    @Test
    fun `post reactivation defer uses normal session entry without enqueueing matchmaking`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            configureUnsatisfiedLegal()
            homeResponse = Response.success(emptyHome())
        }
        val viewModel = viewModel(api)
        runCurrent()
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))
        viewModel.enqueueMatchmaking(SearchLocationInput(-34.6, -58.4, 20))
        advanceUntilIdle()
        val enqueueCallsBeforeReactivation = api.calls.count { it == "enqueueMatchmaking" }

        viewModel.setState(
            RealsRootUiState.AccountDeletionPending(
                user = TestDtos.user(status = "DELETED").toDomain(),
            )
        )
        viewModel.reactivateAccount()
        advanceUntilIdle()

        val legal = viewModel.uiState.value as RealsRootUiState.LegalRequirements
        assertTrue(legal.resumeContext is LegalResumeContext.PostReactivation)

        viewModel.deferLegalRequirements()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.Ready)
        assertEquals(enqueueCallsBeforeReactivation, api.calls.count { it == "enqueueMatchmaking" })
    }

    @Test
    fun `unrelated backend error does not route to legal requirements`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRealsApi())
        runCurrent()

        viewModel.setState(
            RealsRootUiState.FirstChat(
                session = TestDomain.session(),
                matchId = "match-1",
                chatId = "chat-1",
                error = ApiError.Backend(
                    statusCode = 409,
                    code = "CHAT_DECISION_NOT_AVAILABLE",
                    error = "Conflict",
                    message = "decisión unavailable",
                ),
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RealsRootUiState.FirstChat)
    }

    @Test
    fun `affinity legal error enters existing legal flow and restores questionnaire without legal error`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply { configureUnsatisfiedLegal() }
            val viewModel = viewModel(api)
            runCurrent()

            viewModel.setState(
                RealsRootUiState.Ready(
                    session = TestDomain.session(),
                    editingActiveProfile = true,
                    affinityQuestionnaire = AffinityQuestionnaireUiState(
                        open = true,
                        profileId = "profile-1",
                        catalog = TestDtos.affinityQuestionCatalog().toDomain(),
                        answers = listOf(TestDtos.affinityAnswer().toDomain()),
                        mutationError = legalActionRequiredError(),
                    ),
                )
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is RealsRootUiState.LegalRequirements)

            viewModel.deferLegalRequirements()
            advanceUntilIdle()

            val restored = viewModel.uiState.value as RealsRootUiState.Ready
            assertTrue(restored.affinityQuestionnaire.open)
            assertNull(restored.affinityQuestionnaire.mutationError)
            assertTrue(restored.editingActiveProfile)
        }

    @Test
    fun `terminal authentication traversal includes affinity errors`() {
        val terminalError = ApiError.Auth(
            reason = com.reals.app.core.network.AuthFailureReason.NOT_SIGNED_IN,
            message = "signed out",
        )

        assertTrue(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(error = terminalError),
            ).hasTerminalAuthFailure()
        )
        assertTrue(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(mutationError = terminalError),
            ).hasTerminalAuthFailure()
        )
    }

    @Test
    fun `defer is ignored while legal requirements are busy`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRealsApi())
        runCurrent()

        val loadingState = legalRequirements(loading = true)
        viewModel.setState(loadingState)
        viewModel.deferLegalRequirements()
        advanceUntilIdle()

        assertEquals(loadingState, viewModel.uiState.value)

        val submittingState = legalRequirements(submittingDocumentType = LegalDocumentType.TermsOfUse)
        viewModel.setState(submittingState)
        viewModel.deferLegalRequirements()
        advanceUntilIdle()

        assertEquals(submittingState, viewModel.uiState.value)
    }

    private fun FakeRealsApi.configureUnsatisfiedLegal() {
        legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(TestDtos.legalDocumentStatus()),
            )
        )
        currentLegalDocumentsResponse = Response.success(
            TestDtos.currentLegalDocuments(listOf(TestDtos.currentLegalDocument()))
        )
    }

    private fun emptyHome() = TestDtos.home().copy(
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )

    private fun legalActionRequiredError(): ApiError.Backend = ApiError.Backend(
        statusCode = 409,
        code = "LEGAL_ACTION_REQUIRED",
        error = "Conflict",
        message = "legal action required",
    )

    private fun legalRequirements(
        loading: Boolean = false,
        submittingDocumentType: LegalDocumentType? = null,
    ): RealsRootUiState.LegalRequirements = RealsRootUiState.LegalRequirements(
        session = TestDomain.session(),
        resumeContext = LegalResumeContext.PostSession,
        loading = loading,
        submittingDocumentType = submittingDocumentType,
        documents = listOf(
            LegalRequirementUiItem(
                type = LegalDocumentType.TermsOfUse,
                version = "2026-07-01",
                url = "https://example.test/terms",
                requiredAction = LegalDocumentAction.Accepted,
                recordedAction = null,
                actedAt = null,
                satisfied = false,
            )
        ),
    )

    private fun viewModel(api: FakeRealsApi): RealsRootViewModel =
        RealsRootViewModel(rootDependencies(FakeFirebaseAuthRepository(), api), autoRefreshSession = false)

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private fun rootDependencies(
        authRepository: FirebaseAuthRepository,
        api: FakeRealsApi,
    ): RealsRootDependencies {
        val context = ContextWrapper(null)
        val tokenProvider = FakeAuthTokenProvider()
        val apiExecutor = testApiExecutor()
        val meRepository = MeRepository(api, tokenProvider, apiExecutor)
        val profileRepository = ProfileRepository(context, api, tokenProvider, apiExecutor)
        val matchmakingRepository = MatchmakingRepository(api, tokenProvider, apiExecutor)
        val matchRepository = MatchRepository(api, { 0L }, tokenProvider, apiExecutor)
        val chatRepository = ChatRepository(api, testJson, tokenProvider, apiExecutor)
        val schedulingRepository = SchedulingRepository(api, tokenProvider, apiExecutor)
        val legalRepository = LegalRepository(api, tokenProvider, apiExecutor)
        val affinityQuestionRepository =
            com.reals.app.data.repository.AffinityQuestionRepository(api, tokenProvider, apiExecutor)
        val registerPushTokenUseCase = RegisterPushTokenUseCase(meRepository)

        return RealsRootDependencies(
            session = SessionFeatureDependencies(
                authRepository = authRepository,
                provisionAndLoadProfile = ProvisionAndLoadProfileUseCase(meRepository, profileRepository),
                getMe = GetMeUseCase(meRepository),
                pushTokenRegistrationService = PushTokenRegistrationService(context, registerPushTokenUseCase),
            ),
            account = AccountFeatureDependencies(
                reactivateAccount = ReactivateAccountUseCase(meRepository),
                deleteAccount = DeleteAccountUseCase(meRepository, authRepository),
            ),
            legal = LegalFeatureDependencies(
                getCurrentDocuments = GetCurrentLegalDocumentsUseCase(legalRepository),
                getStatus = GetLegalStatusUseCase(legalRepository),
                recordAction = RecordLegalDocumentActionUseCase(legalRepository),
            ),
            profile = ProfileFeatureDependencies(
                createProfile = CreateProfileUseCase(profileRepository),
                updateProfile = UpdateProfileUseCase(profileRepository),
                getCountries = GetCountriesUseCase(profileRepository),
                updateMatchFilters = UpdateMatchFiltersUseCase(profileRepository),
                getProfilePhotos = GetProfilePhotosUseCase(profileRepository),
                addProfilePhotoFile = AddProfilePhotoFileUseCase(profileRepository),
                replaceProfilePhotoFile = ReplaceProfilePhotoFileUseCase(profileRepository),
                deleteProfilePhoto = DeleteProfilePhotoUseCase(profileRepository),
                reorderProfilePhotos = ReorderProfilePhotosUseCase(profileRepository),
                activateProfile = ActivateProfileUseCase(profileRepository),
            ),
            home = HomeFeatureDependencies(
                enqueueMatchmaking = EnqueueMatchmakingUseCase(matchmakingRepository),
                getHome = GetHomeUseCase(meRepository),
                getHomeStatus = GetHomeStatusUseCase(meRepository),
                getHomePending = GetHomePendingUseCase(meRepository),
                leaveQueue = LeaveQueueUseCase(matchmakingRepository),
                dismissSecondChat = DismissSecondChatForConnectionUseCase(chatRepository),
            ),
            manualBlock = ManualBlockFeatureDependencies(
                blockMatchParticipant = BlockMatchParticipantUseCase(matchRepository),
            ),
            firstChat = FirstChatFeatureDependencies(
                getMatch = GetMatchUseCase(matchRepository),
                getFirstChatForMatch = GetFirstChatForMatchUseCase(matchRepository),
                submitChatDecision = SubmitChatDecisionUseCase(matchRepository),
                getChatMessages = GetChatMessagesUseCase(chatRepository),
                sendChatMessage = SendChatMessageUseCase(chatRepository),
                sendChatAudioMessage = com.reals.app.domain.usecase.SendChatAudioMessageUseCase(chatRepository),
                requestNextFirstChatGuidanceQuestion = RequestNextFirstChatGuidanceQuestionUseCase(chatRepository),
                getChatExitRequests = GetChatExitRequestsUseCase(chatRepository),
                requestMutualChatExit = RequestMutualChatExitUseCase(chatRepository),
                acceptChatExitRequest = AcceptChatExitRequestUseCase(chatRepository),
                rejectChatExitRequest = RejectChatExitRequestUseCase(chatRepository),
                timeoutChatExitRequest = TimeoutChatExitRequestUseCase(chatRepository),
                cancelChat = CancelChatUseCase(chatRepository),
                safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
                unansweredSuggestionDismissalStore =
                    com.reals.app.data.preferences.InMemoryFirstChatUnansweredSuggestionDismissalStore(),
            ),
            secondChat = com.reals.app.di.SecondChatFeatureDependencies(
                getStatus = com.reals.app.domain.usecase.GetSecondChatStatusUseCase(chatRepository),
                join = com.reals.app.domain.usecase.JoinSecondChatUseCase(chatRepository),
                createNoShowClaim = com.reals.app.domain.usecase.CreateSecondChatNoShowClaimUseCase(chatRepository),
                getChat = GetChatUseCase(chatRepository),
                getSecondChatForConnection = GetSecondChatForConnectionUseCase(chatRepository),
                getChatMessages = GetChatMessagesUseCase(chatRepository),
                sendChatMessage = SendChatMessageUseCase(chatRepository),
                sendChatAudioMessage = com.reals.app.domain.usecase.SendChatAudioMessageUseCase(chatRepository),
                safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
                createCompletionRequest =
                    com.reals.app.domain.usecase.CreateSecondChatCompletionRequestUseCase(chatRepository),
                decideCompletionRequest =
                    com.reals.app.domain.usecase.DecideSecondChatCompletionRequestUseCase(chatRepository),
                createInactivityClaim =
                    com.reals.app.domain.usecase.CreateSecondChatInactivityClaimUseCase(chatRepository),
            ),
            visualApproval = VisualApprovalFeatureDependencies(
                getMatch = GetMatchUseCase(matchRepository),
                getVisualProfile = GetVisualProfileUseCase(matchRepository),
                submitVisualDecision = SubmitVisualDecisionUseCase(matchRepository),
                putMyPersonalMessage = PutMyPersonalMessageUseCase(matchRepository),
                getPartnerPersonalMessage = GetPartnerPersonalMessageUseCase(matchRepository),
            ),
            scheduling = SchedulingFeatureDependencies(
                getNegotiation = GetSchedulingNegotiationUseCase(schedulingRepository),
                getProposals = GetSchedulingProposalsUseCase(schedulingRepository),
                getAvailability = GetSchedulingAvailabilityUseCase(schedulingRepository),
                submitProposals = SubmitSchedulingProposalsUseCase(schedulingRepository),
                acceptProposal = AcceptSchedulingProposalUseCase(schedulingRepository),
                rejectPartnerProposals = RejectPartnerSchedulingProposalsUseCase(schedulingRepository),
            ),
            affinity = com.reals.app.di.AffinityFeatureDependencies(
                getCatalog = com.reals.app.domain.usecase.GetAffinityQuestionCatalogUseCase(
                    affinityQuestionRepository,
                ),
                getMyAnswers = com.reals.app.domain.usecase.GetMyAffinityAnswersUseCase(
                    affinityQuestionRepository,
                ),
                patchAnswer = com.reals.app.domain.usecase.PatchMyAffinityAnswerUseCase(
                    affinityQuestionRepository,
                ),
                deleteAnswer = com.reals.app.domain.usecase.DeleteMyAffinityAnswerUseCase(
                    affinityQuestionRepository,
                ),
            ),
        )
    }

    private class FakeFirebaseAuthRepository : FirebaseAuthRepository(ContextWrapper(null)) {
        override suspend fun signIn(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success
    }
}
