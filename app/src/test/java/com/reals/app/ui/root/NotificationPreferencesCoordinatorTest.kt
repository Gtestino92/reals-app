package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.data.dto.NotificationPreferencesResponseDto
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.domain.model.NotificationPreferenceGroup
import com.reals.app.domain.model.NotificationPreferences
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.FinalizeAccountDeletionUseCase
import com.reals.app.domain.usecase.GetNotificationPreferencesUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.UpdateNotificationPreferencesUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPreferencesCoordinatorTest {
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
    fun `opening notifications loads backend preferences`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, false))
        }
        val harness = harness(api)

        harness.coordinator.open()
        advanceUntilIdle()

        val state = harness.ready().notificationPreferences
        assertEquals(listOf("getNotificationPreferences"), api.calls)
        assertTrue(state.open)
        assertFalse(state.loading)
        assertEquals(NotificationPreferences(false, true, false), state.preferences)
        assertEquals(NotificationPreferences(false, true, false), state.confirmedPreferences)
        assertNull(state.loadError)
    }

    @Test
    fun `GET failure exposes error without fabricated preferences`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = backendErrorResponse(500, "SERVER_ERROR", "server error")
        }
        val harness = harness(api)

        harness.coordinator.open()
        advanceUntilIdle()

        val state = harness.ready().notificationPreferences
        assertTrue(state.open)
        assertFalse(state.loading)
        assertTrue(state.loadError is ApiError.Backend)
        assertNull(state.preferences)
        assertNull(state.confirmedPreferences)
    }

    @Test
    fun `retry after GET failure succeeds`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = backendErrorResponse(500, "SERVER_ERROR", "server error")
        }
        val harness = harness(api)

        harness.coordinator.open()
        advanceUntilIdle()
        api.notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, false, true))
        harness.coordinator.retryLoad()
        advanceUntilIdle()

        val state = harness.ready().notificationPreferences
        assertEquals(listOf("getNotificationPreferences", "getNotificationPreferences"), api.calls)
        assertNull(state.loadError)
        assertEquals(NotificationPreferences(true, false, true), state.preferences)
    }

    @Test
    fun `toggle activity saves complete state and trusts backend response`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, false))
            updateNotificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, false, true))
            beforeUpdateNotificationPreferencesResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Activity, true)
        runCurrent()
        started.await()

        val saving = harness.ready().notificationPreferences
        assertTrue(saving.saving)
        assertEquals(NotificationPreferences(true, true, false), saving.preferences)
        assertEquals(true, api.notificationPreferencesBody?.activityEnabled)
        assertEquals(true, api.notificationPreferencesBody?.remindersEnabled)
        assertEquals(false, api.notificationPreferencesBody?.availabilityEnabled)

        release.complete(Unit)
        advanceUntilIdle()

        val saved = harness.ready().notificationPreferences
        assertFalse(saved.saving)
        assertEquals(NotificationPreferences(true, false, true), saved.preferences)
        assertEquals(NotificationPreferences(true, false, true), saved.confirmedPreferences)
    }

    @Test
    fun `toggle reminders uses the same complete update behavior`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, false, true))
            updateNotificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Reminders, true)
        advanceUntilIdle()

        assertEquals(true, api.notificationPreferencesBody?.activityEnabled)
        assertEquals(true, api.notificationPreferencesBody?.remindersEnabled)
        assertEquals(true, api.notificationPreferencesBody?.availabilityEnabled)
        assertEquals(NotificationPreferences(true, true, true), harness.ready().notificationPreferences.preferences)
    }

    @Test
    fun `toggle availability uses the same complete update behavior`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, false))
            updateNotificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Availability, true)
        advanceUntilIdle()

        assertEquals(true, api.notificationPreferencesBody?.activityEnabled)
        assertEquals(true, api.notificationPreferencesBody?.remindersEnabled)
        assertEquals(true, api.notificationPreferencesBody?.availabilityEnabled)
        assertEquals(NotificationPreferences(true, true, true), harness.ready().notificationPreferences.preferences)
    }

    @Test
    fun `save failure rolls back confirmed values and re-enables controls`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
            updateNotificationPreferencesResponse = backendErrorResponse(500, "SERVER_ERROR", "server error")
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Activity, false)
        advanceUntilIdle()

        val state = harness.ready().notificationPreferences
        assertFalse(state.saving)
        assertEquals(NotificationPreferences(true, true, true), state.preferences)
        assertEquals(NotificationPreferences(true, true, true), state.confirmedPreferences)
        assertTrue(state.saveError is ApiError.Backend)
    }

    @Test
    fun `saving blocks overlapping PUT requests`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, true))
            updateNotificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
            beforeUpdateNotificationPreferencesResponse = {
                started.complete(Unit)
                release.await()
            }
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Activity, true)
        runCurrent()
        started.await()
        harness.coordinator.update(NotificationPreferenceGroup.Reminders, false)
        runCurrent()

        assertEquals(1, api.calls.count { it == "updateNotificationPreferences" })
        assertEquals(NotificationPreferences(true, true, true), harness.ready().notificationPreferences.preferences)

        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `re-entering notifications reloads authoritative backend state`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
        }
        val harness = harness(api)

        harness.coordinator.open()
        advanceUntilIdle()
        harness.coordinator.close()
        api.notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, false))
        harness.coordinator.open()
        advanceUntilIdle()

        assertEquals(2, api.calls.count { it == "getNotificationPreferences" })
        assertEquals(NotificationPreferences(false, true, false), harness.ready().notificationPreferences.preferences)
    }

    @Test
    fun `reopening waits for in-flight save before authoritative GET`() = runTest(dispatcher) {
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
            updateNotificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, true))
            beforeUpdateNotificationPreferencesResponse = {
                saveStarted.complete(Unit)
                releaseSave.await()
            }
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Activity, false)
        runCurrent()
        saveStarted.await()
        harness.coordinator.close()
        harness.coordinator.open()
        runCurrent()

        val reopened = harness.ready().notificationPreferences
        assertTrue(reopened.open)
        assertTrue(reopened.loading)
        assertEquals(
            listOf("getNotificationPreferences", "updateNotificationPreferences"),
            api.calls,
        )

        api.notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, true))
        releaseSave.complete(Unit)
        advanceUntilIdle()

        val finalState = harness.ready().notificationPreferences
        assertEquals(
            listOf("getNotificationPreferences", "updateNotificationPreferences", "getNotificationPreferences"),
            api.calls,
        )
        assertEquals(NotificationPreferences(false, true, true), finalState.preferences)
        assertEquals(NotificationPreferences(false, true, true), finalState.confirmedPreferences)
        assertFalse(finalState.loading)
        assertFalse(finalState.saving)
    }

    @Test
    fun `old save result does not directly mutate reopened screen`() = runTest(dispatcher) {
        val releaseSave = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(true, true, true))
            updateNotificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, false, false))
            beforeUpdateNotificationPreferencesResponse = { releaseSave.await() }
        }
        val harness = harness(api)
        harness.coordinator.open()
        advanceUntilIdle()

        harness.coordinator.update(NotificationPreferenceGroup.Activity, false)
        runCurrent()
        harness.coordinator.close()
        harness.coordinator.open()
        runCurrent()

        api.notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, true))
        releaseSave.complete(Unit)
        advanceUntilIdle()

        val finalState = harness.ready().notificationPreferences
        assertEquals(
            listOf("getNotificationPreferences", "updateNotificationPreferences", "getNotificationPreferences"),
            api.calls,
        )
        assertEquals(NotificationPreferences(false, true, true), finalState.preferences)
        assertEquals(NotificationPreferences(false, true, true), finalState.confirmedPreferences)
    }

    @Test
    fun `closing cancels obsolete load without republishing after release`() = runTest(dispatcher) {
        val getStarted = CompletableDeferred<Unit>()
        val releaseGet = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            notificationPreferencesResponse = retrofit2.Response.success(preferencesDto(false, true, false))
            beforeGetNotificationPreferencesResponse = {
                getStarted.complete(Unit)
                releaseGet.await()
            }
        }
        val harness = harness(api)

        harness.coordinator.open()
        runCurrent()
        getStarted.await()
        harness.coordinator.close()
        releaseGet.complete(Unit)
        advanceUntilIdle()

        val state = harness.ready().notificationPreferences
        assertFalse(state.open)
        assertFalse(state.loading)
        assertNull(state.preferences)
        assertNull(state.confirmedPreferences)
    }

    @Test
    fun `system back closes notification settings`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = RealsRootViewModel(rootViewModelTestDependencies(api), autoRefreshSession = false)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.openNotificationPreferences()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as RealsRootUiState.Ready).notificationPreferences.open)
        assertTrue(viewModel.uiState.value.canHandleSystemBack())

        viewModel.onSystemBack()

        assertFalse((viewModel.uiState.value as RealsRootUiState.Ready).notificationPreferences.open)
    }

    @Test
    fun `sign out does not retain notification settings UI state`() = runTest(dispatcher) {
        val authRepository = object : FirebaseAuthRepository(ContextWrapper(null)) {
            override fun signOut() = Unit
        }
        val viewModel = RealsRootViewModel(
            dependencies = rootViewModelTestDependencies(
                api = FakeRealsApi(),
                authRepositoryOverride = authRepository,
            ),
            autoRefreshSession = false,
        )
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                notificationPreferences = NotificationPreferencesUiState(
                    open = true,
                    preferences = NotificationPreferences(false, true, false),
                    confirmedPreferences = NotificationPreferences(false, true, false),
                ),
            )
        )

        viewModel.signOut()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value is RealsRootUiState.Ready)
    }

    private fun CoroutineScope.harness(api: FakeRealsApi): Harness {
        val state = MutableStateFlow<RealsRootUiState>(
            RealsRootUiState.Ready(session = TestDomain.session())
        )
        val meRepository = MeRepository(api, FakeAuthTokenProvider(), testApiExecutor())
        val dependencies = AccountFeatureDependencies(
            reactivateAccount = ReactivateAccountUseCase(meRepository),
            deleteAccount = DeleteAccountUseCase(meRepository),
            finalizeAccountDeletion = FinalizeAccountDeletionUseCase(meRepository),
            getNotificationPreferences = GetNotificationPreferencesUseCase(meRepository),
            updateNotificationPreferences = UpdateNotificationPreferencesUseCase(meRepository),
        )
        return Harness(
            state = state,
            coordinator = NotificationPreferencesCoordinator(state, dependencies, this),
        )
    }

    private fun preferencesDto(
        activityEnabled: Boolean,
        remindersEnabled: Boolean,
        availabilityEnabled: Boolean,
    ) = NotificationPreferencesResponseDto(
        activityEnabled = activityEnabled,
        remindersEnabled = remindersEnabled,
        availabilityEnabled = availabilityEnabled,
    )

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private data class Harness(
        val state: MutableStateFlow<RealsRootUiState>,
        val coordinator: NotificationPreferencesCoordinator,
    ) {
        fun ready(): RealsRootUiState.Ready = state.value as RealsRootUiState.Ready
    }
}
