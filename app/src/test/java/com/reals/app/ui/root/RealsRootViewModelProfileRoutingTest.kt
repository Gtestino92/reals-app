package com.reals.app.ui.root

import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.VisualDecision
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelProfileRoutingTest {
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
    fun `after photo add marks draft closing profile management can return to Home`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(draftHomeWithFirstChat())
        }
        val viewModel = viewModel(api)
        val edited = photoAddedState(
            previous = editingReady(activeProfile(photoCount = 1), listOf(testPhoto("photo-1", 1))),
            addedPhoto = testPhoto("photo-2", 2),
            successMessage = "Foto subida correctamente.",
        )
        viewModel.setState(edited)

        viewModel.closeProfileManagement()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(ready.editingActiveProfile)
        assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
        assertEquals(ProfileStatus.Draft, ready.home.homeState?.profileStatus)
        assertTrue(ready.home.screenModel?.pendingActions?.isNotEmpty() == true)
        assertTrue(ready.home.screenModel?.draftProfileWarning?.title?.contains("borrador") == true)
        assertFalse(ready.home.screenModel?.matchmaking?.canSearch == true)
    }

    @Test
    fun `after photo add marks draft failed close keeps profile management and clears stale active Home`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
            }
            val viewModel = viewModel(api)
            val edited = photoAddedState(
                previous = editingReady(
                    profile = activeProfile(photoCount = 1),
                    photos = listOf(testPhoto("photo-1", 1)),
                    home = cachedActiveHome(),
                ),
                addedPhoto = testPhoto("photo-2", 2),
                successMessage = "Foto subida correctamente.",
            )
            viewModel.setState(edited)

            viewModel.closeProfileManagement()
            advanceUntilIdle()

            val ready = viewModel.uiState.value as RealsRootUiState.Ready
            assertTrue(ready.editingActiveProfile)
            assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
            assertEquals(listOf("photo-1", "photo-2"), ready.profilePhotos.map { it.id })
            assertTrue(ready.home.homeError != null)
            assertEquals(null, ready.home.homeState)
            assertEquals(null, ready.home.screenModel)
            assertFalse(ready.home.screenModel?.matchmaking?.canSearch == true)
        }

    @Test
    fun `after photo replacement marks draft closing profile management can return to Home`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(draftHomeWithFirstChat())
        }
        val viewModel = viewModel(api)
        val edited = photoReplacedState(
            previous = editingReady(activeProfile(photoCount = 1), listOf(testPhoto("photo-1", 1))),
            replacedPhoto = testPhoto("photo-1", 1, url = "https://example.com/new.jpg"),
            successMessage = "Foto reemplazada correctamente.",
        )
        viewModel.setState(edited)

        viewModel.closeProfileManagement()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(ready.editingActiveProfile)
        assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
        assertTrue(ready.home.screenModel?.pendingActions?.isNotEmpty() == true)
    }

    @Test
    fun `after photo deletion returns draft closing profile management can return to Home`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(draftHomeWithFirstChat())
        }
        val viewModel = viewModel(api)
        val edited = photoDeletedState(
            previous = editingReady(
                profile = activeProfile(photoCount = 2),
                photos = listOf(testPhoto("photo-1", 1), testPhoto("photo-2", 2)),
            ),
            deletedPhotoId = "photo-1",
            updatedProfile = activeProfile(photoCount = 1).copy(status = ProfileStatus.Draft),
            successMessage = "Foto eliminada.",
        )
        viewModel.setState(edited)

        viewModel.closeProfileManagement()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(ready.editingActiveProfile)
        assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
        assertTrue(ready.home.screenModel?.pendingActions?.isNotEmpty() == true)
    }

    @Test
    fun `post session re-entry for draft profile with visual review reaches Home`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            homeResponse = Response.success(draftHomeWithVisualReview())
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            RealsRootUiState.LegalRequirements(
                session = draftSession(),
                resumeContext = LegalResumeContext.PostSession,
                documents = listOf(legalRequirement()),
            )
        )

        viewModel.deferLegalRequirements()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
        assertTrue(ready.home.screenModel?.pendingActions?.isNotEmpty() == true)
        assertTrue(ready.home.screenModel?.draftProfileWarning?.title?.contains("borrador") == true)
        assertEquals(1, api.calls.count { it == "getHome" })
        assertEquals(0, api.calls.count { it == "getMyProfilePhotos" })
    }

    @Test
    fun `post session re-entry for draft profile with first chat reuses preloaded Home for auto route`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(draftHomeWithFirstChat())
            }
            val viewModel = viewModel(api)
            viewModel.setState(
                RealsRootUiState.LegalRequirements(
                    session = draftSession(),
                    resumeContext = LegalResumeContext.PostSession,
                    documents = listOf(legalRequirement()),
                )
            )

            viewModel.deferLegalRequirements()
            advanceUntilIdle()

            val firstChat = viewModel.uiState.value as RealsRootUiState.FirstChat
            assertEquals("match-1", firstChat.matchId)
            assertEquals("chat-1", firstChat.chatId)
            assertEquals(ProfileStatus.Draft, (firstChat.session.profileSnapshot as ProfileSnapshot.Found).profile.status)
            assertEquals(1, api.calls.count { it == "getHome" })
        }

    @Test
    fun `post session re-entry for draft profile Home failure remains recoverable without photos`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = backendErrorResponse(500, "SERVER_ERROR", "failed")
            }
            val viewModel = viewModel(api)
            viewModel.setState(
                RealsRootUiState.LegalRequirements(
                    session = draftSession(),
                    resumeContext = LegalResumeContext.PostSession,
                    documents = listOf(legalRequirement()),
                )
            )

            viewModel.deferLegalRequirements()
            advanceUntilIdle()

            val ready = viewModel.uiState.value as RealsRootUiState.Ready
            assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
            assertTrue(ready.home.homeError != null)
            assertEquals(null, ready.home.homeState)
            assertEquals(null, ready.home.screenModel)
            assertTrue(ready.profilePhotos.isEmpty())
            assertEquals(1, api.calls.count { it == "getHome" })
            assertEquals(0, api.calls.count { it == "getMyProfilePhotos" })
        }

    @Test
    fun `visual review rejection returns draft profile to Home even when no interactions remain`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                matchResponse = Response.success(TestDtos.match(state = "VISUAL_REJECTED"))
                homeResponse = Response.success(emptyDraftHome())
            }
            val viewModel = viewModel(api)
            viewModel.setState(
                RealsRootUiState.VisualApproval(
                    session = draftSession(),
                    matchId = "match-visual",
                )
            )

            viewModel.submitVisualDecision(VisualDecision.Rejected)
            advanceUntilIdle()

            val ready = viewModel.uiState.value as RealsRootUiState.Ready
            assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
            assertEquals(ProfileStatus.Draft, ready.home.homeState?.profileStatus)
            assertTrue(ready.home.allowDraftHomeWithoutInteractions)
            assertTrue(ready.home.screenModel?.pendingActions?.isEmpty() == true)
            assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
            assertTrue(ready.home.screenModel?.draftProfileWarning?.title?.contains("borrador") == true)
            assertEquals(1, api.calls.count { it == "submitVisualDecision" })
            assertEquals(1, api.calls.count { it == "getHome" })
        }

    @Test
    fun `first chat decision reload returns draft profile to Home even when no interactions remain`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                matchResponse = Response.success(TestDtos.match(state = "CHAT_ACTIVE"))
                homeResponse = Response.success(emptyDraftHome())
            }
            val viewModel = viewModel(api)
            viewModel.setState(firstChatState())

            viewModel.submitFirstChatDecision(ChatContinueDecision.Approved)
            advanceUntilIdle()

            val ready = viewModel.uiState.value as RealsRootUiState.Ready
            assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
            assertEquals(ProfileStatus.Draft, ready.home.homeState?.profileStatus)
            assertTrue(ready.home.allowDraftHomeWithoutInteractions)
            assertTrue(ready.home.screenModel?.pendingActions?.isEmpty() == true)
            assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
            assertTrue(ready.home.screenModel?.draftProfileWarning?.title?.contains("borrador") == true)
            assertEquals(1, api.calls.count { it == "submitChatDecision" })
            assertEquals(1, api.calls.count { it == "getHome" })
        }

    @Test
    fun `closing first chat returns draft profile to Home even when no interactions remain`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(emptyDraftHome())
            }
            val viewModel = viewModel(api)
            viewModel.setState(firstChatState())

            viewModel.closeFirstChat()
            advanceUntilIdle()

            val ready = viewModel.uiState.value as RealsRootUiState.Ready
            assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
            assertEquals(ProfileStatus.Draft, ready.home.homeState?.profileStatus)
            assertTrue(ready.home.allowDraftHomeWithoutInteractions)
            assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
            assertEquals(1, api.calls.count { it == "getHome" })
        }

    @Test
    fun `pending engagement return keeps draft profile in Home even when no interactions remain`() =
        runTest(dispatcher) {
            val api = FakeRealsApi().apply {
                homeResponse = Response.success(emptyDraftHome())
            }
            val viewModel = viewModel(api)
            viewModel.setState(
                RealsRootUiState.PendingEngagement(
                    session = draftSession(),
                    title = "Pendiente",
                    body = "Tenés una acción pendiente.",
                )
            )

            viewModel.returnToHomeFromPendingEngagement()
            advanceUntilIdle()

            val ready = viewModel.uiState.value as RealsRootUiState.Ready
            assertEquals(ProfileStatus.Draft, ready.currentProfile().status)
            assertEquals(ProfileStatus.Draft, ready.home.homeState?.profileStatus)
            assertTrue(ready.home.allowDraftHomeWithoutInteractions)
            assertEquals(false, ready.home.screenModel?.matchmaking?.canSearch)
            assertEquals(1, api.calls.count { it == "getHome" })
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

    private fun editingReady(
        profile: Profile,
        photos: List<ProfilePhoto>,
        home: HomeUiState = HomeUiState(),
    ): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Found(profile)),
            photos = PhotoManagementUiState(
                profilePhotos = photos,
                addingPhoto = true,
            ),
            home = home,
            editingActiveProfile = true,
        )

    private fun activeProfile(photoCount: Int): Profile =
        TestDtos.profile(status = "ACTIVE").toDomain().copy(photoCount = photoCount)

    private fun draftSession() = TestDomain.session().copy(
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = "DRAFT").toDomain()),
    )

    private fun firstChatState(): RealsRootUiState.FirstChat =
        RealsRootUiState.FirstChat(
            session = draftSession(),
            matchId = "match-1",
            chatId = "chat-1",
            chat = TestDtos.chat(status = "ACTIVE").toDomain(),
        )

    private fun testPhoto(
        id: String,
        position: Int,
        url: String = "https://example.com/$id.jpg",
    ): ProfilePhoto =
        TestDtos.photo(id = id, position = position).toDomain().copy(url = url)

    private fun draftHomeWithFirstChat() = emptyDraftHome().copy(
        pendingActions = listOf(
            HomePendingActionResponseDto(
                type = "FIRST_CHAT",
                matchId = "match-1",
                chatId = "chat-1",
                partner = TestDtos.partner("First"),
            ),
        ),
    )

    private fun draftHomeWithVisualReview() = emptyDraftHome().copy(
        pendingActions = listOf(
            HomePendingActionResponseDto(
                type = "VISUAL_REVIEW",
                matchId = "match-visual",
                partner = TestDtos.partner("Visual"),
            ),
        ),
    )

    private fun emptyDraftHome() = TestDtos.home().copy(
        profileStatus = "DRAFT",
        matchmaking = HomeMatchmakingResponseDto(
            inQueue = false,
            canSearch = false,
            blockedReason = null,
        ),
        activeInteractionsSummary = HomeActiveInteractionsSummaryResponseDto(
            activeInitialCount = 0,
            activeConnectionCount = 0,
            hasPendingSchedulingConnection = false,
            actionableConnectionCount = 0,
        ),
        pendingActions = emptyList(),
        nextSteps = emptyList(),
        passiveNotices = emptyList(),
    )

    private fun cachedActiveHome(): HomeUiState {
        val home = TestDtos.home().copy(
            profileStatus = "ACTIVE",
            matchmaking = HomeMatchmakingResponseDto(
                inQueue = false,
                canSearch = true,
                blockedReason = null,
            ),
            pendingActions = emptyList(),
            nextSteps = emptyList(),
            passiveNotices = emptyList(),
        ).toDomain()
        return HomeUiState(
            homeState = home,
            screenModel = HomeUiMapper().toScreenModel(
                home = home,
                localHidden = LocalHiddenInteractions(
                    hiddenFirstChatMatchIds = emptySet(),
                    hiddenVisualMatchIds = emptySet(),
                ),
                localMatchmakingBlockedReason = null,
            ),
        )
    }

    private fun legalRequirement(): LegalRequirementUiItem = LegalRequirementUiItem(
        type = LegalDocumentType.TermsOfUse,
        version = "2026-07-01",
        url = "https://example.test/terms",
        requiredAction = LegalDocumentAction.Accepted,
        recordedAction = null,
        actedAt = null,
        satisfied = true,
    )

    private fun RealsRootUiState.Ready.currentProfile(): Profile =
        (session.profileSnapshot as ProfileSnapshot.Found).profile
}
