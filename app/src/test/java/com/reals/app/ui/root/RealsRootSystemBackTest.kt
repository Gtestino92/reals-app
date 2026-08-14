package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootSystemBackTest {
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
    fun `system back is handled for supported non root screens`() {
        val session = TestDomain.session()

        listOf(
            RealsRootUiState.SecondChat(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
            ),
            RealsRootUiState.VisualApproval(session = session, matchId = "match-1"),
            RealsRootUiState.Scheduling(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
            ),
            RealsRootUiState.PartnerProfile(session = session, matchId = "match-1"),
            RealsRootUiState.PendingEngagement(
                session = session,
                title = "Pendiente",
                body = "Hay una acción pendiente.",
            ),
            RealsRootUiState.ActivationComplete(
                session = session,
                result = ProfileActivationResult(
                    profile = TestDtos.profile().toDomain(),
                    addedPhotoCount = 0,
                    totalPhotoCount = 0,
                    generatedUrls = emptyList(),
                ),
            ),
        ).forEach { state ->
            assertTrue(state.canHandleSystemBack())
        }
    }

    @Test
    fun `system back is not handled for root onboarding first chat or loading states`() {
        val session = TestDomain.session()

        listOf(
            RealsRootUiState.Login(),
            RealsRootUiState.Checking,
            RealsRootUiState.LoadingSession(email = "alex@example.com"),
            RealsRootUiState.MissingFirebase("missing"),
            RealsRootUiState.Ready(session = session),
            RealsRootUiState.Ready(
                session = session,
                editingActiveProfile = true,
                photos = PhotoManagementUiState(reorderingPhotos = true),
            ),
            firstChat(chat = TestDtos.chat(status = "ACTIVE").toDomain()),
            firstChat(loading = true),
        ).forEach { state ->
            assertFalse(state.canHandleSystemBack())
        }
    }

    @Test
    fun `completed partial first chat can recover to Home`() {
        val state = firstChat()

        assertTrue(state.canRecoverFirstChatToHome())
        assertTrue(state.canHandleSystemBack())
    }

    @Test
    fun `first chat recovery is blocked while work is active`() {
        listOf(
            firstChat(loading = true),
            firstChat(refreshing = true),
            firstChat(sending = true),
            firstChat(actionLoading = true),
            firstChat(guidanceActionLoading = true),
            firstChat(manualBlock = ManualBlockUiState(loading = true)),
        ).forEach { state ->
            assertFalse(state.canRecoverFirstChatToHome())
            assertFalse(state.canHandleSystemBack())
        }
    }

    @Test
    fun `first chat visible and system back recovery share one rule`() {
        listOf(
            firstChat(),
            firstChat(loading = true),
            firstChat(chat = TestDtos.chat(status = "ACTIVE").toDomain()),
            firstChat(actionLoading = true),
        ).forEach { state ->
            assertEquals(state.canRecoverFirstChatToHome(), state.canHandleSystemBack())
        }
    }

    @Test
    fun `system back is handled for profile management even after draft photo mutation`() {
        listOf(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
            ),
            RealsRootUiState.Ready(
                session = draftSession(),
                editingActiveProfile = true,
            ),
        ).forEach { state ->
            assertTrue(state.canHandleSystemBack())
        }
    }

    @Test
    fun `system back is handled when questionnaire is open regardless of profile management state`() {
        listOf(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(open = true, profileId = "profile-1"),
            ),
            RealsRootUiState.Ready(
                session = draftSession(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(open = true, profileId = "profile-1"),
            ),
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
                photos = PhotoManagementUiState(reorderingPhotos = true),
                affinityQuestionnaire = AffinityQuestionnaireUiState(open = true, profileId = "profile-1"),
            ),
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(
                    open = true,
                    profileId = "profile-1",
                    mutation = AffinityAnswerMutationUiState(
                        questionId = "MUSIC_DISCOVERY_001",
                        pendingAnswerCode = "LOW",
                        requestId = 1L,
                    ),
                ),
            ),
        ).forEach { state ->
            assertTrue(state.canHandleSystemBack())
        }
    }

    @Test
    fun `questionnaire surface takes precedence over profile and Home routing`() {
        assertTrue(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(open = true, profileId = "profile-1"),
            ).shouldRenderAffinityQuestionnaireSurface()
        )
        assertTrue(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
                affinityQuestionnaire = AffinityQuestionnaireUiState(open = true, profileId = "profile-1"),
            ).shouldRenderAffinityQuestionnaireSurface()
        )
        assertFalse(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                affinityQuestionnaire = AffinityQuestionnaireUiState(open = false, profileId = "profile-1"),
            ).shouldRenderAffinityQuestionnaireSurface()
        )
    }

    @Test
    fun `onSystemBack navigates questionnaire hierarchy before profile management`() = runTest(dispatcher) {
        val viewModel = RealsRootViewModel(
            dependencies = rootViewModelTestDependencies(com.reals.app.testutil.FakeRealsApi()),
            autoRefreshSession = false,
        )
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
                affinityQuestionnaire = AffinityQuestionnaireUiState(
                    open = true,
                    profileId = "profile-1",
                    destination = AffinityQuestionnaireDestination.Question(
                        questionId = "MUSIC_DISCOVERY_001",
                        source = AffinityQuestionSource.Review,
                    ),
                    catalog = TestDtos.affinityQuestionCatalog().toDomain(),
                    answers = listOf(TestDtos.affinityAnswer().toDomain()),
                ),
            )
        )

        viewModel.onSystemBack()

        val review = viewModel.uiState.value as RealsRootUiState.Ready
        assertTrue(review.affinityQuestionnaire.open)
        assertEquals(AffinityQuestionnaireDestination.Review, review.affinityQuestionnaire.destination)

        viewModel.onSystemBack()

        val overview = viewModel.uiState.value as RealsRootUiState.Ready
        assertTrue(overview.affinityQuestionnaire.open)
        assertEquals(AffinityQuestionnaireDestination.Overview, overview.affinityQuestionnaire.destination)

        viewModel.onSystemBack()

        val closed = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(closed.affinityQuestionnaire.open)
        assertTrue(closed.editingActiveProfile)
        assertEquals(listOf("MUSIC_DISCOVERY_001"), closed.affinityQuestionnaire.answers.map { it.questionId })
    }

    @Test
    fun `system back on Home Pending returns to Overview`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setState(ready(surface = HomeSurface.Pending))

        assertTrue(viewModel.uiState.value.canHandleSystemBack())

        viewModel.onSystemBack()

        assertReadySurface(viewModel, HomeSurface.Overview)
    }

    @Test
    fun `Ready Pending back is ignored when Home is not the rendered surface`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setState(
            RealsRootUiState.Ready(
                session = draftSession(),
                home = HomeUiState(surface = HomeSurface.Pending),
            )
        )

        assertFalse(viewModel.uiState.value.canHandleSystemBack())

        viewModel.onSystemBack()

        assertReadySurface(viewModel, HomeSurface.Pending)
    }

    @Test
    fun `stale visual open result does not restore Visual Review after system Back`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = com.reals.app.testutil.FakeRealsApi().apply {
            matchResponse = retrofit2.Response.success(TestDtos.match("VISUAL_PHASE"))
            beforeGetMatchResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(ready(surface = HomeSurface.Pending))

        viewModel.openVisualApproval("match-1")
        runCurrent()
        started.await()

        val loading = viewModel.uiState.value as RealsRootUiState.VisualApproval
        assertEquals("match-1", loading.matchId)
        assertEquals(HomeSurface.Pending, loading.returnHomeSurface)
        assertTrue(loading.loading)

        viewModel.onSystemBack()
        advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Pending)

        release.complete(Unit)
        advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Pending)
    }

    @Test
    fun `stale visual refresh result does not restore Visual Review after system Back`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = com.reals.app.testutil.FakeRealsApi().apply {
            matchResponse = retrofit2.Response.success(TestDtos.match("VISUAL_PHASE"))
            beforeGetMatchResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            RealsRootUiState.VisualApproval(
                session = TestDomain.session(),
                matchId = "match-1",
                returnHomeSurface = HomeSurface.Pending,
            )
        )

        viewModel.refreshVisualApproval()
        runCurrent()
        started.await()

        val refreshing = viewModel.uiState.value as RealsRootUiState.VisualApproval
        assertTrue(refreshing.refreshing)

        viewModel.onSystemBack()
        advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Pending)

        release.complete(Unit)
        advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Pending)
    }

    @Test
    fun `stale Partner Profile open result does not overwrite Scheduling after system Back`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = com.reals.app.testutil.FakeRealsApi().apply {
            beforeGetVisualProfileResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val viewModel = viewModel(api)
        viewModel.setState(
            scheduling(
                connectionId = "connection-1",
                matchId = "match-scheduling",
                partnerName = "Alex",
                returnHomeSurface = HomeSurface.Pending,
            )
        )

        viewModel.openConnectionPartnerProfile("match-profile")
        runCurrent()
        started.await()

        val loading = viewModel.uiState.value as RealsRootUiState.PartnerProfile
        assertEquals("match-profile", loading.matchId)
        assertTrue(loading.loading)

        viewModel.onSystemBack()
        advanceUntilIdle()

        val scheduling = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals("connection-1", scheduling.connectionId)
        assertEquals("match-scheduling", scheduling.matchId)
        assertEquals(HomeSurface.Pending, scheduling.returnHomeSurface)

        release.complete(Unit)
        advanceUntilIdle()

        val final = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals("connection-1", final.connectionId)
        assertEquals("match-scheduling", final.matchId)
        assertEquals(HomeSurface.Pending, final.returnHomeSurface)
    }

    @Test
    fun `visual review system back returns to originating Home surface`() = runTest(dispatcher) {
        assertSystemBackReturnsHome(
            state = RealsRootUiState.VisualApproval(
                session = TestDomain.session(),
                matchId = "match-1",
                returnHomeSurface = HomeSurface.Overview,
            ),
            expectedSurface = HomeSurface.Overview,
        )
        assertSystemBackReturnsHome(
            state = RealsRootUiState.VisualApproval(
                session = TestDomain.session(),
                matchId = "match-1",
                returnHomeSurface = HomeSurface.Pending,
            ),
            expectedSurface = HomeSurface.Pending,
        )
    }

    @Test
    fun `visual review opened from First Chat installs Overview fallback`() = runTest(dispatcher) {
        val api = com.reals.app.testutil.FakeRealsApi().apply {
            matchResponse = retrofit2.Response.success(TestDtos.match("VISUAL_PHASE"))
        }
        val viewModel = viewModel(api)
        viewModel.setState(firstChat())

        viewModel.openVisualApproval("match-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.VisualApproval
        assertEquals(HomeSurface.Overview, state.returnHomeSurface)
    }

    @Test
    fun `visual review opened from Home installs current Home surface`() = runTest(dispatcher) {
        val api = com.reals.app.testutil.FakeRealsApi().apply {
            matchResponse = retrofit2.Response.success(TestDtos.match("VISUAL_PHASE"))
        }
        val viewModel = viewModel(api)
        viewModel.setState(ready(surface = HomeSurface.Pending))

        viewModel.openVisualApproval("match-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.VisualApproval
        assertEquals(HomeSurface.Pending, state.returnHomeSurface)
    }

    @Test
    fun `scheduling system back returns to originating Home surface`() = runTest(dispatcher) {
        assertSystemBackReturnsHome(
            state = scheduling(returnHomeSurface = HomeSurface.Overview),
            expectedSurface = HomeSurface.Overview,
        )
        assertSystemBackReturnsHome(
            state = scheduling(returnHomeSurface = HomeSurface.Pending),
            expectedSurface = HomeSurface.Pending,
        )
    }

    @Test
    fun `scheduling opened from Home installs current Home surface`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setState(ready(surface = HomeSurface.Pending))

        viewModel.openScheduling("connection-1", "match-1", "Alex")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals(HomeSurface.Pending, state.returnHomeSurface)
    }

    @Test
    fun `partner profile direct system back returns to originating Home surface`() = runTest(dispatcher) {
        assertSystemBackReturnsHome(
            state = RealsRootUiState.PartnerProfile(
                session = TestDomain.session(),
                matchId = "match-1",
                fallbackHomeSurface = HomeSurface.Overview,
            ),
            expectedSurface = HomeSurface.Overview,
        )
        assertSystemBackReturnsHome(
            state = RealsRootUiState.PartnerProfile(
                session = TestDomain.session(),
                matchId = "match-1",
                fallbackHomeSurface = HomeSurface.Pending,
            ),
            expectedSurface = HomeSurface.Pending,
        )
    }

    @Test
    fun `partner profile opened from Home installs fallback without scheduling context`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setState(ready(surface = HomeSurface.Pending))

        viewModel.openConnectionPartnerProfile("match-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.PartnerProfile
        assertEquals(HomeSurface.Pending, state.fallbackHomeSurface)
        assertEquals(null, state.schedulingReturnContext)
    }

    @Test
    fun `partner profile from Scheduling returns to Scheduling then Home Pending`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setState(
            scheduling(
                connectionId = "connection-1",
                matchId = "match-scheduling",
                partnerName = "Alex",
                returnHomeSurface = HomeSurface.Pending,
            )
        )

        viewModel.openConnectionPartnerProfile("match-profile")
        advanceUntilIdle()

        val partnerProfile = viewModel.uiState.value as RealsRootUiState.PartnerProfile
        assertEquals(HomeSurface.Pending, partnerProfile.fallbackHomeSurface)
        assertEquals(
            SchedulingReturnContext(
                connectionId = "connection-1",
                matchId = "match-scheduling",
                partnerName = "Alex",
                homeSurface = HomeSurface.Pending,
            ),
            partnerProfile.schedulingReturnContext,
        )

        viewModel.onSystemBack()
        advanceUntilIdle()

        val scheduling = viewModel.uiState.value as RealsRootUiState.Scheduling
        assertEquals("connection-1", scheduling.connectionId)
        assertEquals("match-scheduling", scheduling.matchId)
        assertEquals("Alex", scheduling.partnerName)
        assertEquals(HomeSurface.Pending, scheduling.returnHomeSurface)

        viewModel.onSystemBack()
        advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Pending)
    }

    @Test
    fun `partner profile from Scheduling returns ultimately to Home Overview`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setState(scheduling(returnHomeSurface = HomeSurface.Overview))

        viewModel.openConnectionPartnerProfile("match-profile")
        advanceUntilIdle()
        viewModel.onSystemBack()
        advanceUntilIdle()
        viewModel.onSystemBack()
        advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Overview)
    }

    @Test
    fun `explicit Home actions still force Overview`() = runTest(dispatcher) {
        assertExplicitCloseReturnsOverview(
            state = RealsRootUiState.VisualApproval(
                session = TestDomain.session(),
                matchId = "match-1",
                returnHomeSurface = HomeSurface.Pending,
            ),
            close = RealsRootViewModel::closeVisualApproval,
        )
        assertExplicitCloseReturnsOverview(
            state = scheduling(returnHomeSurface = HomeSurface.Pending),
            close = RealsRootViewModel::closeScheduling,
        )
        assertExplicitCloseReturnsOverview(
            state = RealsRootUiState.PartnerProfile(
                session = TestDomain.session(),
                matchId = "match-1",
                fallbackHomeSurface = HomeSurface.Pending,
            ),
            close = RealsRootViewModel::closePartnerProfile,
        )
    }

    @Test
    fun `permitted second chat system back still returns to Overview`() = runTest(dispatcher) {
        assertSystemBackReturnsHome(
            state = RealsRootUiState.SecondChat(
                session = TestDomain.session(),
                connectionId = "connection-1",
                matchId = "match-1",
            ),
            expectedSurface = HomeSurface.Overview,
        )
    }

    @Test
    fun `system back is not handled while guarded operations are active`() {
        val session = TestDomain.session()

        assertFalse(
            RealsRootUiState.SecondChat(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
                sending = true,
            ).canHandleSystemBack()
        )
        assertFalse(
            RealsRootUiState.VisualApproval(
                session = session,
                matchId = "match-1",
                deciding = true,
            ).canHandleSystemBack()
        )
        assertFalse(
            RealsRootUiState.Scheduling(
                session = session,
                connectionId = "connection-1",
                matchId = "match-1",
                submitting = true,
            ).canHandleSystemBack()
        )
    }

    private fun draftSession() = TestDomain.session().copy(
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile(status = "DRAFT").toDomain()),
    )

    private fun firstChat(
        chat: com.reals.app.domain.model.Chat? = null,
        loading: Boolean = false,
        refreshing: Boolean = false,
        sending: Boolean = false,
        actionLoading: Boolean = false,
        guidanceActionLoading: Boolean = false,
        manualBlock: ManualBlockUiState = ManualBlockUiState(),
    ) = RealsRootUiState.FirstChat(
        session = TestDomain.session(),
        matchId = "match-1",
        chatId = chat?.id,
        chat = chat,
        loading = loading,
        refreshing = refreshing,
        sending = sending,
        actionLoading = actionLoading,
        guidanceActionLoading = guidanceActionLoading,
        manualBlock = manualBlock,
    )

    private fun ready(surface: HomeSurface): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session(),
            home = HomeUiState(surface = surface),
        )

    private fun scheduling(
        connectionId: String = "connection-1",
        matchId: String = "match-1",
        partnerName: String? = "Alex",
        returnHomeSurface: HomeSurface,
    ): RealsRootUiState.Scheduling =
        RealsRootUiState.Scheduling(
            session = TestDomain.session(),
            connectionId = connectionId,
            matchId = matchId,
            partnerName = partnerName,
            returnHomeSurface = returnHomeSurface,
        )

    private fun viewModel(
        api: com.reals.app.testutil.FakeRealsApi = com.reals.app.testutil.FakeRealsApi(),
    ): RealsRootViewModel =
        RealsRootViewModel(
            dependencies = rootViewModelTestDependencies(api),
            autoRefreshSession = false,
        )

    private fun assertSystemBackReturnsHome(
        state: RealsRootUiState,
        expectedSurface: HomeSurface,
    ) {
        val viewModel = viewModel()
        viewModel.setState(state)

        viewModel.onSystemBack()
        dispatcher.scheduler.advanceUntilIdle()

        assertReadySurface(viewModel, expectedSurface)
    }

    private fun assertExplicitCloseReturnsOverview(
        state: RealsRootUiState,
        close: RealsRootViewModel.() -> Unit,
    ) {
        val viewModel = viewModel()
        viewModel.setState(state)

        viewModel.close()
        dispatcher.scheduler.advanceUntilIdle()

        assertReadySurface(viewModel, HomeSurface.Overview)
    }

    private fun assertReadySurface(
        viewModel: RealsRootViewModel,
        expectedSurface: HomeSurface,
    ) {
        val ready = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(expectedSurface, ready.home.surface)
    }

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }
}
