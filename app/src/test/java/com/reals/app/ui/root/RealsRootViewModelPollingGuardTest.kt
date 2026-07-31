package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.LegalRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.data.mapper.toDomain
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.di.LegalFeatureDependencies
import com.reals.app.di.ManualBlockFeatureDependencies
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.di.RealsRootDependencies
import com.reals.app.di.SchedulingFeatureDependencies
import com.reals.app.di.SecondChatFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.model.NegotiationStatus
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
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.DecideSecondChatCompletionRequestUseCase
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
import com.reals.app.domain.usecase.GetSecondChatStatusUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.JoinSecondChatUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RejectPartnerSchedulingProposalsUseCase
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.RequestNextFirstChatGuidanceQuestionUseCase
import com.reals.app.domain.usecase.RecordLegalDocumentActionUseCase
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
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelPollingGuardTest {
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
    fun `silent first chat refresh ignores overlapping call`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val firstCallStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetFirstChatForMatchResponse = {
                firstCallStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState())

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        firstCallStarted.await()

        viewModel.refreshFirstChat(silent = true)
        runCurrent()

        assertEquals(1, api.calls.count { it == "getFirstChatForMatch" })

        gate.complete(Unit)
        advanceUntilIdle()

        viewModel.refreshFirstChat(silent = true)
        runCurrent()

        assertEquals(2, api.calls.count { it == "getFirstChatForMatch" })
    }

    @Test
    fun `silent second chat refresh ignores overlapping call`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val firstCallStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetChatResponse = {
                firstCallStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState())

        viewModel.refreshSecondChat(silent = true)
        runCurrent()
        firstCallStarted.await()

        viewModel.refreshSecondChat(silent = true)
        runCurrent()

        assertEquals(1, api.calls.count { it == "getChat" })

        gate.complete(Unit)
        advanceUntilIdle()

        viewModel.refreshSecondChat(silent = true)
        runCurrent()

        assertEquals(2, api.calls.count { it == "getChat" })
    }

    @Test
    fun `silent first chat refresh started before draft deletion cannot restore deleted draft`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        val draft = audioDraft("first-chat-draft.m4a")
        viewModel.setState(firstChatState().copy(audioDraft = draft))

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.deleteFirstChatAudioDraft()
        runCurrent()
        assertNull((viewModel.uiState.value as RealsRootUiState.FirstChat).audioDraft)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull((viewModel.uiState.value as RealsRootUiState.FirstChat).audioDraft)
    }

    @Test
    fun `silent second chat refresh started before draft deletion cannot restore deleted draft`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetSecondChatStatusResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        val draft = audioDraft("second-chat-draft.m4a")
        viewModel.setState(secondChatState().copy(audioDraft = draft))

        viewModel.refreshSecondChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.deleteSecondChatAudioDraft()
        runCurrent()
        assertNull((viewModel.uiState.value as RealsRootUiState.SecondChat).audioDraft)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull((viewModel.uiState.value as RealsRootUiState.SecondChat).audioDraft)
    }

    @Test
    fun `silent first chat refresh started before draft replacement cannot restore old draft`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        val oldDraft = audioDraft("first-old.m4a")
        val newDraft = audioDraft("first-new.m4a")
        viewModel.setState(firstChatState().copy(audioDraft = oldDraft))

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.setFirstChatAudioDraft(newDraft)
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(newDraft, (viewModel.uiState.value as RealsRootUiState.FirstChat).audioDraft)
    }

    @Test
    fun `silent second chat refresh started before draft replacement cannot restore old draft`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetSecondChatStatusResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        val oldDraft = audioDraft("second-old.m4a")
        val newDraft = audioDraft("second-new.m4a")
        viewModel.setState(secondChatState().copy(audioDraft = oldDraft))

        viewModel.refreshSecondChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.setSecondChatAudioDraft(newDraft)
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(newDraft, (viewModel.uiState.value as RealsRootUiState.SecondChat).audioDraft)
    }

    @Test
    fun `silent first chat refresh cannot erase audio upload completion`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChatState())

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.setState(
            firstChatState().copy(audioUpload = ChatAudioUploadUiState(completedClientMessageId = "client-1"))
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "client-1",
            (viewModel.uiState.value as RealsRootUiState.FirstChat).audioUpload.completedClientMessageId,
        )
    }

    @Test
    fun `silent second chat refresh cannot erase audio upload completion`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetSecondChatStatusResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(secondChatState())

        viewModel.refreshSecondChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.setState(
            secondChatState().copy(audioUpload = ChatAudioUploadUiState(completedClientMessageId = "client-2"))
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "client-2",
            (viewModel.uiState.value as RealsRootUiState.SecondChat).audioUpload.completedClientMessageId,
        )
    }

    @Test
    fun `silent first chat refresh cannot erase audio upload failure`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        val failure = ApiError.Unexpected("upload failed")
        viewModel.setState(firstChatState())

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.setState(firstChatState().copy(audioUpload = ChatAudioUploadUiState(error = failure)))

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(failure, (viewModel.uiState.value as RealsRootUiState.FirstChat).audioUpload.error)
    }

    @Test
    fun `silent second chat refresh cannot erase audio upload failure`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetSecondChatStatusResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        val failure = ApiError.Unexpected("upload failed")
        viewModel.setState(secondChatState())

        viewModel.refreshSecondChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.setState(secondChatState().copy(audioUpload = ChatAudioUploadUiState(error = failure)))

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(failure, (viewModel.uiState.value as RealsRootUiState.SecondChat).audioUpload.error)
    }

    @Test
    fun `silent first chat refresh cannot restore cleared audio upload error`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetFirstChatForMatchResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            firstChatState().copy(audioUpload = ChatAudioUploadUiState(error = ApiError.Unexpected("old")))
        )

        viewModel.refreshFirstChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.clearFirstChatAudioUploadState()
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull((viewModel.uiState.value as RealsRootUiState.FirstChat).audioUpload.error)
    }

    @Test
    fun `silent second chat refresh cannot restore cleared audio upload error`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetSecondChatStatusResponse = {
                refreshStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            secondChatState().copy(audioUpload = ChatAudioUploadUiState(error = ApiError.Unexpected("old")))
        )

        viewModel.refreshSecondChat(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.clearSecondChatAudioUploadState()
        runCurrent()

        gate.complete(Unit)
        advanceUntilIdle()

        assertNull((viewModel.uiState.value as RealsRootUiState.SecondChat).audioUpload.error)
    }

    @Test
    fun `scheduling refresh ignores overlapping calls and allows later refresh`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val firstCallStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                firstCallStarted.complete(Unit)
                gate.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.refreshScheduling(silent = true)
        runCurrent()
        firstCallStarted.await()

        viewModel.refreshScheduling(silent = true)
        runCurrent()

        assertEquals(1, api.calls.count { it == "getConnectionNegotiation" })

        viewModel.refreshScheduling(silent = false)
        runCurrent()

        assertEquals(1, api.calls.count { it == "getConnectionNegotiation" })

        gate.complete(Unit)
        advanceUntilIdle()

        viewModel.refreshScheduling(silent = false)
        advanceUntilIdle()

        assertEquals(2, api.calls.count { it == "getConnectionNegotiation" })
    }

    @Test
    fun `submit scheduling proposals publishes pending state before request completes`() = runTest(dispatcher) {
        val submitStarted = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeSubmitConnectionProposalsResponse = {
                submitStarted.complete(Unit)
                releaseSubmit.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            schedulingState().copy(
                error = ApiError.Unexpected("old"),
                message = "mensaje anterior",
            )
        )

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:00:00Z"))
        runCurrent()
        submitStarted.await()

        val pending = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(true, pending.submitting)
        assertEquals("Enviando horarios...", pending.submittingLabel)
        assertNull(pending.error)
        assertNull(pending.message)

        releaseSubmit.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `silent scheduling refresh cannot overwrite failed proposal submission`() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        var negotiationCalls = 0
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                negotiationCalls += 1
                if (negotiationCalls == 1) {
                    refreshStarted.complete(Unit)
                    refreshGate.await()
                }
            }
            submitProposalsResponse = backendErrorResponse(
                statusCode = 400,
                code = "SCHEDULING_INVALID_PROPOSALS",
            )
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.refreshScheduling(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:00:00Z"))
        advanceUntilIdle()

        refreshGate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        val error = state.error as ApiError.Backend
        assertEquals(false, state.submitting)
        assertEquals(BackendErrorCode.SchedulingInvalidProposals, error.backendErrorCode)
        assertNull(state.message)
    }

    @Test
    fun `silent scheduling refresh is ignored while submission is pending`() = runTest(dispatcher) {
        val submitStarted = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeSubmitConnectionProposalsResponse = {
                submitStarted.complete(Unit)
                releaseSubmit.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:00:00Z"))
        runCurrent()
        submitStarted.await()

        viewModel.refreshScheduling(silent = true)
        runCurrent()

        assertEquals(0, api.calls.count { it == "getConnectionNegotiation" })

        releaseSubmit.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `duplicate scheduling submission is blocked while pending`() = runTest(dispatcher) {
        val submitStarted = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeSubmitConnectionProposalsResponse = {
                submitStarted.complete(Unit)
                releaseSubmit.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:00:00Z"))
        runCurrent()
        submitStarted.await()

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:30:00Z"))
        runCurrent()

        assertEquals(1, api.calls.count { it == "submitConnectionProposals" })

        releaseSubmit.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `silent scheduling refresh cannot overwrite accepted proposal`() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        var negotiationCalls = 0
        val api = FakeRealsApi().apply {
            negotiationResponse = Response.success(TestDtos.negotiation("CONFIRMED"))
            beforeGetConnectionNegotiationResponse = {
                negotiationCalls += 1
                if (negotiationCalls == 1) {
                    refreshStarted.complete(Unit)
                    refreshGate.await()
                }
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.refreshScheduling(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.acceptSchedulingProposal("proposal-1")
        advanceUntilIdle()

        refreshGate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(false, state.submitting)
        assertEquals("Horario confirmado.", state.message)
    }

    @Test
    fun `accept scheduling proposal publishes pending and blocks duplicate actions`() = runTest(dispatcher) {
        val acceptStarted = CompletableDeferred<Unit>()
        val releaseAccept = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeAcceptConnectionProposalResponse = {
                acceptStarted.complete(Unit)
                releaseAccept.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.acceptSchedulingProposal("proposal-1")
        runCurrent()
        acceptStarted.await()

        val pending = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(true, pending.submitting)
        assertEquals("Aceptando horario...", pending.submittingLabel)

        viewModel.acceptSchedulingProposal("proposal-2")
        runCurrent()
        assertEquals(1, api.calls.count { it == "acceptConnectionProposal" })

        releaseAccept.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `silent scheduling refresh cannot overwrite partner proposal rejection`() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        var negotiationCalls = 0
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                negotiationCalls += 1
                if (negotiationCalls == 1) {
                    refreshStarted.complete(Unit)
                    refreshGate.await()
                }
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.refreshScheduling(silent = true)
        runCurrent()
        refreshStarted.await()

        viewModel.rejectSchedulingPartnerProposals()
        advanceUntilIdle()

        refreshGate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(false, state.submitting)
        assertEquals("Rechazaste las opciones recibidas.", state.message)
    }

    @Test
    fun `reject partner proposals publishes pending and blocks duplicate actions`() = runTest(dispatcher) {
        val rejectStarted = CompletableDeferred<Unit>()
        val releaseReject = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeRejectConnectionPartnerProposalsResponse = {
                rejectStarted.complete(Unit)
                releaseReject.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.rejectSchedulingPartnerProposals()
        runCurrent()
        rejectStarted.await()

        val pending = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(true, pending.submitting)
        assertEquals("Rechazando opciones...", pending.submittingLabel)

        viewModel.rejectSchedulingPartnerProposals()
        runCurrent()
        assertEquals(1, api.calls.count { it == "rejectConnectionPartnerProposals" })

        releaseReject.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `rejection result cannot overwrite another scheduling connection`() = runTest(dispatcher) {
        val rejectStarted = CompletableDeferred<Unit>()
        val releaseReject = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeRejectConnectionPartnerProposalsResponse = {
                rejectStarted.complete(Unit)
                releaseReject.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState(connectionId = "connection-1"))

        viewModel.rejectSchedulingPartnerProposals()
        runCurrent()
        rejectStarted.await()

        val otherConnection = schedulingState(connectionId = "connection-2", matchId = "match-2")
        viewModel.setState(otherConnection)
        releaseReject.complete(Unit)
        advanceUntilIdle()

        assertEquals(otherConnection, viewModel.uiState.value)
    }

    @Test
    fun `scheduling submission result cannot overwrite another scheduling connection`() = runTest(dispatcher) {
        val submitStarted = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeSubmitConnectionProposalsResponse = {
                submitStarted.complete(Unit)
                releaseSubmit.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState(connectionId = "connection-1"))

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:00:00Z"))
        runCurrent()
        submitStarted.await()

        val otherConnection = schedulingState(connectionId = "connection-2", matchId = "match-2")
        viewModel.setState(otherConnection)
        releaseSubmit.complete(Unit)
        advanceUntilIdle()

        assertEquals(otherConnection, viewModel.uiState.value)
    }

    @Test
    fun `scheduling submission result cannot overwrite another screen`() = runTest(dispatcher) {
        val submitStarted = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeSubmitConnectionProposalsResponse = {
                submitStarted.complete(Unit)
                releaseSubmit.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(schedulingState())

        viewModel.submitSchedulingProposals(listOf("2026-06-18T21:00:00Z"))
        runCurrent()
        submitStarted.await()

        val ready = RealsRootUiState.Ready(TestDomain.session())
        viewModel.setState(ready)
        releaseSubmit.complete(Unit)
        advanceUntilIdle()

        assertEquals(ready, viewModel.uiState.value)
    }

    @Test
    fun `open scheduling publishes pending state before requests complete`() = runTest(dispatcher) {
        val negotiationStarted = CompletableDeferred<Unit>()
        val releaseNegotiation = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                negotiationStarted.complete(Unit)
                releaseNegotiation.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling(" connection-1 ", " match-1 ", "Alex")

        val pending = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(true, pending.loading)
        assertEquals("connection-1", pending.connectionId)
        assertEquals("match-1", pending.matchId)
        assertEquals("Alex", pending.partnerName)
        assertNull(pending.negotiation)
        assertEquals(emptyList<Any>(), pending.proposals)
        assertEquals(false, viewModel.uiState.value is RealsRootUiState.Ready)

        runCurrent()
        negotiationStarted.await()

        releaseNegotiation.complete(Unit)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(false, loaded.loading)
        assertEquals("connection-1", loaded.connectionId)
        assertEquals(NegotiationStatus.Pending, loaded.negotiation?.status)
        assertEquals(1, loaded.proposals.size)
        assertEquals(60L, loaded.availability?.conflictWindowMinutes)
    }

    @Test
    fun `duplicate open scheduling taps share the active open request`() = runTest(dispatcher) {
        val negotiationStarted = CompletableDeferred<Unit>()
        val releaseNegotiation = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                negotiationStarted.complete(Unit)
                releaseNegotiation.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        runCurrent()
        negotiationStarted.await()

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        runCurrent()

        assertEquals(1, api.calls.count { it == "getConnectionNegotiation" })

        releaseNegotiation.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, api.calls.count { it == "getConnectionProposals" })
    }

    @Test
    fun `closing scheduling during initial load prevents late result from reopening it`() = runTest(dispatcher) {
        val negotiationStarted = CompletableDeferred<Unit>()
        val releaseNegotiation = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                negotiationStarted.complete(Unit)
                releaseNegotiation.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        runCurrent()
        negotiationStarted.await()

        viewModel.closeScheduling()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value is RealsRootUiState.Scheduling)

        releaseNegotiation.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value is RealsRootUiState.Scheduling)
    }

    @Test
    fun `initial scheduling result cannot overwrite another scheduling connection`() = runTest(dispatcher) {
        val negotiationStarted = CompletableDeferred<Unit>()
        val releaseNegotiation = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetConnectionNegotiationResponse = {
                negotiationStarted.complete(Unit)
                releaseNegotiation.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        runCurrent()
        negotiationStarted.await()

        val otherConnection = schedulingState(connectionId = "connection-2", matchId = "match-2")
        viewModel.setState(otherConnection)

        releaseNegotiation.complete(Unit)
        advanceUntilIdle()

        assertEquals(otherConnection, viewModel.uiState.value)
    }

    @Test
    fun `initial scheduling negotiation failure remains on scheduling screen`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            negotiationResponse = backendErrorResponse(
                statusCode = 404,
                code = "SCHEDULING_NEGOTIATION_NOT_FOUND",
            )
        }
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(false, state.loading)
        assertNull(state.negotiation)
        assertEquals(ApiError.Backend::class, state.error!!::class)
        assertEquals(0, api.calls.count { it == "getConnectionProposals" })
    }

    @Test
    fun `initial scheduling proposal failure preserves loaded negotiation`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            proposalsResponse = backendErrorResponse(
                statusCode = 500,
                code = "SCHEDULING_PROPOSALS_UNAVAILABLE",
            )
        }
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(false, state.loading)
        assertEquals(NegotiationStatus.Pending, state.negotiation?.status)
        assertEquals(emptyList<Any>(), state.proposals)
        assertEquals(ApiError.Backend::class, state.error!!::class)
    }

    @Test
    fun `completed scheduling open job allows a later open`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(api)
        viewModel.setState(RealsRootUiState.Ready(TestDomain.session()))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        advanceUntilIdle()

        viewModel.openScheduling("connection-2", "match-2", "Blake")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals("connection-2", state.connectionId)
        assertEquals("match-2", state.matchId)
        assertEquals("Blake", state.partnerName)
        assertEquals(2, api.calls.count { it == "getConnectionNegotiation" })
    }

    private fun viewModel(api: FakeRealsApi): RealsRootViewModel =
        RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private fun firstChatState(): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = TestDomain.session(),
            matchId = "match-1",
            chatId = "chat-1",
            match = TestDtos.match("CHAT_ACTIVE").toDomain(),
            chat = TestDtos.chat(status = "ACTIVE").copy(id = "chat-1").toDomain(),
        )

    private fun secondChatState(): RealsRootUiState.SecondChat =
        RealsRootUiState.SecondChat(
            session = TestDomain.session(),
            connectionId = "connection-1",
            matchId = "match-1",
            partnerName = "Alex",
            chatId = "chat-1",
            chat = TestDtos.chat(status = "ACTIVE").copy(id = "chat-1", chatType = "SECOND_CHAT").toDomain(),
        )

    private fun audioDraft(fileName: String): ChatAudioDraftUiState =
        ChatAudioDraftUiState(
            filePath = "C:\\temp\\$fileName",
            clientMessageId = "$fileName-client-id",
            durationMillis = 2_000L,
            sizeBytes = 12_345L,
        )

    private fun schedulingState(
        connectionId: String = "connection-1",
        matchId: String = "match-1",
    ): RealsRootUiState.Scheduling =
        RealsRootUiState.Scheduling(
            session = TestDomain.session(),
            connectionId = connectionId,
            matchId = matchId,
            partnerName = "Alex",
            negotiation = TestDtos.negotiation("PENDING").toDomain(),
        )

}

internal fun rootViewModelTestDependencies(
    api: FakeRealsApi,
    firstChatDismissalStore: com.reals.app.data.preferences.InMemoryFirstChatUnansweredSuggestionDismissalStore =
        com.reals.app.data.preferences.InMemoryFirstChatUnansweredSuggestionDismissalStore(),
): RealsRootDependencies {
        val context = ContextWrapper(null)
        val tokenProvider = FakeAuthTokenProvider()
        val apiExecutor = testApiExecutor()
        val authRepository = FirebaseAuthRepository(context)
        val meRepository = MeRepository(api, tokenProvider, apiExecutor)
        val profileRepository = ProfileRepository(context, api, tokenProvider, apiExecutor)
        val matchmakingRepository = MatchmakingRepository(api, tokenProvider, apiExecutor)
        val matchRepository = MatchRepository(api, { 0L }, tokenProvider, apiExecutor)
        val chatRepository = ChatRepository(api, testJson, tokenProvider, apiExecutor)
        val schedulingRepository = SchedulingRepository(api, tokenProvider, apiExecutor)
        val legalRepository = LegalRepository(api, tokenProvider, apiExecutor)
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
                unansweredSuggestionDismissalStore = firstChatDismissalStore,
            ),
            secondChat = SecondChatFeatureDependencies(
                getStatus = GetSecondChatStatusUseCase(chatRepository),
                join = JoinSecondChatUseCase(chatRepository),
                createNoShowClaim = CreateSecondChatNoShowClaimUseCase(chatRepository),
                getChat = GetChatUseCase(chatRepository),
                getSecondChatForConnection = GetSecondChatForConnectionUseCase(chatRepository),
                getChatMessages = GetChatMessagesUseCase(chatRepository),
                sendChatMessage = SendChatMessageUseCase(chatRepository),
                sendChatAudioMessage = com.reals.app.domain.usecase.SendChatAudioMessageUseCase(chatRepository),
                safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
                createCompletionRequest = CreateSecondChatCompletionRequestUseCase(chatRepository),
                decideCompletionRequest = DecideSecondChatCompletionRequestUseCase(chatRepository),
                createInactivityClaim = CreateSecondChatInactivityClaimUseCase(chatRepository),
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
        )
}
