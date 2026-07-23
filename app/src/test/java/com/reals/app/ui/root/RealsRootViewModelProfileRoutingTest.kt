package com.reals.app.ui.root

import com.reals.app.data.dto.HomeActiveInteractionsSummaryResponseDto
import com.reals.app.data.dto.HomeMatchmakingResponseDto
import com.reals.app.data.dto.HomePendingActionResponseDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
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
        assertEquals("Tu perfil está en borrador", ready.home.screenModel?.draftProfileWarning?.title)
        assertEquals(2, api.calls.count { it == "getHome" })
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
    ): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Found(profile)),
            photos = PhotoManagementUiState(
                profilePhotos = photos,
                addingPhoto = true,
            ),
            editingActiveProfile = true,
        )

    private fun activeProfile(photoCount: Int): Profile =
        TestDtos.profile(status = "ACTIVE").toDomain().copy(photoCount = photoCount)

    private fun draftSession() = TestDomain.session().copy(
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = "DRAFT").toDomain()),
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
