package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFirebaseEmailVerificationCoordinatorTest {
    @Test
    fun `feature disabled performs no backend local verification call`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.NotVerified)
        val mark = FakeMarkLocalFirebaseEmailVerified()
        val coordinator = coordinator(enabled = false, auth = auth, mark = mark)

        val result = coordinator.ensureVerifiedForLocalBootstrap()

        assertEquals(LocalFirebaseEmailVerificationResult.Verified, result)
        assertEquals(0, auth.reloadCalls)
        assertEquals(0, mark.calls)
    }

    @Test
    fun `already verified returns success without backend update`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.Verified)
        val mark = FakeMarkLocalFirebaseEmailVerified()

        val result = coordinator(auth = auth, mark = mark).ensureVerifiedForLocalBootstrap()

        assertEquals(LocalFirebaseEmailVerificationResult.Verified, result)
        assertEquals(1, auth.reloadCalls)
        assertEquals(0, mark.calls)
    }

    @Test
    fun `initially unverified calls backend once then reloads and returns verified`() = runTest {
        val auth = FakeAuth(
            EmailVerificationCheckResult.NotVerified,
            EmailVerificationCheckResult.Verified,
        )
        val mark = FakeMarkLocalFirebaseEmailVerified()

        val result = coordinator(auth = auth, mark = mark).ensureVerifiedForLocalBootstrap()

        assertEquals(LocalFirebaseEmailVerificationResult.Verified, result)
        assertEquals(2, auth.reloadCalls)
        assertEquals(1, mark.calls)
    }

    @Test
    fun `backend success with final unverified returns controlled failure`() = runTest {
        val auth = FakeAuth(
            EmailVerificationCheckResult.NotVerified,
            EmailVerificationCheckResult.NotVerified,
        )

        val result = coordinator(auth = auth).ensureVerifiedForLocalBootstrap()

        assertTrue(result is LocalFirebaseEmailVerificationResult.Failure)
        assertEquals(ApiError.LocalFirebaseEmailVerification, (result as LocalFirebaseEmailVerificationResult.Failure).error)
    }

    @Test
    fun `backend failure returns controlled failure without false verification`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.NotVerified)
        val mark = FakeMarkLocalFirebaseEmailVerified(ApiResult.Failure(ApiError.Backend(500, null, null, "boom")))

        val result = coordinator(auth = auth, mark = mark).ensureVerifiedForLocalBootstrap()

        assertTrue(result is LocalFirebaseEmailVerificationResult.Failure)
        assertEquals(1, auth.reloadCalls)
        assertEquals(1, mark.calls)
    }

    @Test
    fun `firebase refresh failure returns controlled failure`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.Failure)

        val result = coordinator(auth = auth).ensureVerifiedForLocalBootstrap()

        assertTrue(result is LocalFirebaseEmailVerificationResult.Failure)
    }

    @Test
    fun `no signed in user returns terminal auth result`() = runTest {
        val auth = FakeAuth(EmailVerificationCheckResult.NotSignedIn)

        val result = coordinator(auth = auth).ensureVerifiedForLocalBootstrap()

        assertEquals(LocalFirebaseEmailVerificationResult.NotSignedIn, result)
    }

    @Test
    fun `repeated call after verified skips backend update`() = runTest {
        val auth = FakeAuth(
            EmailVerificationCheckResult.NotVerified,
            EmailVerificationCheckResult.Verified,
            EmailVerificationCheckResult.Verified,
        )
        val mark = FakeMarkLocalFirebaseEmailVerified()
        val coordinator = coordinator(auth = auth, mark = mark)

        assertEquals(LocalFirebaseEmailVerificationResult.Verified, coordinator.ensureVerifiedForLocalBootstrap())
        assertEquals(LocalFirebaseEmailVerificationResult.Verified, coordinator.ensureVerifiedForLocalBootstrap())

        assertEquals(1, mark.calls)
        assertEquals(3, auth.reloadCalls)
    }

    @Test
    fun `concurrent calls serialize backend update`() = runTest {
        val auth = FakeAuth(
            EmailVerificationCheckResult.NotVerified,
            EmailVerificationCheckResult.Verified,
            EmailVerificationCheckResult.Verified,
        )
        val mark = FakeMarkLocalFirebaseEmailVerified()
        val coordinator = coordinator(auth = auth, mark = mark)

        awaitAll(
            async { coordinator.ensureVerifiedForLocalBootstrap() },
            async { coordinator.ensureVerifiedForLocalBootstrap() },
        )

        assertEquals(1, mark.calls)
    }

    private fun coordinator(
        enabled: Boolean = true,
        auth: FakeAuth = FakeAuth(EmailVerificationCheckResult.Verified),
        mark: FakeMarkLocalFirebaseEmailVerified = FakeMarkLocalFirebaseEmailVerified(),
    ): LocalFirebaseEmailVerificationCoordinator =
        LocalFirebaseEmailVerificationCoordinator(enabled, auth, mark)

    private class FakeAuth(
        vararg results: EmailVerificationCheckResult,
    ) : FirebaseAuthRepository(ContextWrapper(null)) {
        private val queue = ArrayDeque(results.toList())
        var reloadCalls = 0
            private set

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult {
            reloadCalls++
            return if (queue.isEmpty()) EmailVerificationCheckResult.Verified else queue.removeFirst()
        }
    }

    private class FakeMarkLocalFirebaseEmailVerified(
        private val result: ApiResult<Unit> = ApiResult.Success(Unit),
    ) : MarkLocalFirebaseEmailVerified {
        var calls = 0
            private set

        override suspend fun invoke(): ApiResult<Unit> {
            calls++
            return result
        }
    }
}
