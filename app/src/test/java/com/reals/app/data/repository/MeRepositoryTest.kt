package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.data.dto.NotificationPreferencesResponseDto
import com.reals.app.domain.model.NotificationPreferences
import com.reals.app.domain.model.HomeNextStep
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.failureError
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeRepositoryTest {
    private val api = FakeRealsApi()
    private val tokenProvider = FakeAuthTokenProvider()
    private val repository = MeRepository(api, tokenProvider, testApiExecutor())

    @Test
    fun `getHome maps pending actions next steps and passive notices`() = runBlocking {
        api.homeResponse = retrofit2.Response.success(TestDtos.home())

        val home = repository.getHome().successValue()

        assertEquals("getHome", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertTrue(home.pendingActions.first() is HomePendingAction.FirstChat)
        assertEquals(2, home.nextSteps.size)
        assertEquals(1, home.activeInteractionsSummary.activeInitialCount)
        assertEquals(1, home.passiveNotices.size)
    }

    @Test
    fun `getHomeStatus calls status endpoint with auth and maps response`() = runBlocking {
        api.homeStatusResponse = retrofit2.Response.success(
            TestDtos.homeStatus(
                version = 9,
                dirty = true,
                nextRefreshAt = "2026-06-18T21:05:00Z",
            )
        )

        val status = repository.getHomeStatus().successValue()

        assertEquals("getHomeStatus", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(9L, status.version)
        assertEquals(true, status.dirty)
        assertEquals("2026-06-18T21:05:00Z", status.nextRefreshAt)
        assertEquals(TestDtos.now, status.serverTime)
    }

    @Test
    fun `getHomePending calls pending endpoint with auth and maps response`() = runBlocking {
        val pending = repository.getHomePending().successValue()

        assertEquals("getHomePending", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(1L, pending.version)
        assertTrue(pending.pendingActions.first() is HomePendingAction.FirstChat)
        assertTrue(pending.nextSteps[1] is HomeNextStep.SecondChatAvailable)
        assertEquals(1, pending.passiveNotices.size)
    }

    @Test
    fun `provision and reactivate map users`() = runBlocking {
        assertEquals("user-1", repository.provisionMe().successValue().id)
        assertEquals("user-1", repository.reactivateMe().successValue().id)

        assertEquals(listOf("provisionMe", "reactivateMe"), api.calls)
    }

    @Test
    fun `finalize deletion parses user response and maps to unit`() = runBlocking {
        api.finalizeMyDeletionResponse = retrofit2.Response.success(TestDtos.user(status = "DELETED"))

        repository.finalizeMyDeletion().successValue()

        assertEquals(listOf("finalizeMyDeletion"), api.calls)
        assertEquals("Bearer test-token", api.lastAuthorization)
    }

    @Test
    fun `registerPushToken sends android platform and maps registered flag`() = runBlocking {
        val registered = repository.registerPushToken("fcm-token").successValue()

        assertTrue(registered)
        assertEquals("registerPushToken", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals("fcm-token", api.registerPushTokenBody?.token)
        assertEquals("ANDROID", api.registerPushTokenBody?.platform)
    }

    @Test
    fun `getNotificationPreferences maps all backend groups`() = runBlocking {
        api.notificationPreferencesResponse = retrofit2.Response.success(
            NotificationPreferencesResponseDto(
                activityEnabled = false,
                remindersEnabled = true,
                availabilityEnabled = false,
            )
        )

        val preferences = repository.getNotificationPreferences().successValue()

        assertEquals("getNotificationPreferences", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(false, preferences.activityEnabled)
        assertEquals(true, preferences.remindersEnabled)
        assertEquals(false, preferences.availabilityEnabled)
    }

    @Test
    fun `updateNotificationPreferences sends complete body and trusts response`() = runBlocking {
        api.updateNotificationPreferencesResponse = retrofit2.Response.success(
            NotificationPreferencesResponseDto(
                activityEnabled = true,
                remindersEnabled = true,
                availabilityEnabled = false,
            )
        )

        val result = repository.updateNotificationPreferences(
            NotificationPreferences(
                activityEnabled = true,
                remindersEnabled = false,
                availabilityEnabled = true,
            )
        ).successValue()

        assertEquals("updateNotificationPreferences", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(true, api.notificationPreferencesBody?.activityEnabled)
        assertEquals(false, api.notificationPreferencesBody?.remindersEnabled)
        assertEquals(true, api.notificationPreferencesBody?.availabilityEnabled)
        assertEquals(true, result.activityEnabled)
        assertEquals(true, result.remindersEnabled)
        assertEquals(false, result.availabilityEnabled)
    }

    @Test
    fun `local firebase email verification uses authenticated unit call`() = runBlocking {
        api.localFirebaseEmailVerificationResponse = retrofit2.Response.success(204, Unit)

        repository.markCurrentFirebaseEmailVerifiedForLocalDevelopment().successValue()

        assertEquals(listOf("markCurrentFirebaseEmailVerifiedForLocalDevelopment"), api.calls)
        assertEquals("Bearer test-token", api.lastAuthorization)
    }

    @Test
    fun `local firebase email verification backend failure maps through api error`() = runBlocking {
        api.localFirebaseEmailVerificationResponse = backendErrorResponse(403, "FORBIDDEN", "forbidden")

        val error = repository.markCurrentFirebaseEmailVerifiedForLocalDevelopment().failureError()

        assertTrue(error is ApiError.Backend)
        assertEquals(403, (error as ApiError.Backend).statusCode)
    }

    @Test
    fun `auth failure returns auth error without api call`() = runBlocking {
        tokenProvider.failMissingUser()

        val error = repository.getHome().failureError()

        assertTrue(error is ApiError.Auth)
        assertTrue(api.calls.isEmpty())
    }
}
