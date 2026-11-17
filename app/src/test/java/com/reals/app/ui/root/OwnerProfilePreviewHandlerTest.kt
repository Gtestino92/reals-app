package com.reals.app.ui.root

import com.reals.app.core.network.*
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.*
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.ui.profile.ownerProfilePreview
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerProfilePreviewHandlerTest {
    @Test fun incompatibleProfileStatusClearsPreviewWithoutChangingAuthenticationIdentity() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val handler = handler(state) { ApiResult.Success(session) }
        handler.open(); advanceUntilIdle()
        val profile = (session.profileSnapshot as ProfileSnapshot.Found).profile
        val inactive = session.copy(profileSnapshot = ProfileSnapshot.Found(profile.copy(status = ProfileStatus.Inactive)))
        state.value = (state.value as RealsRootUiState.Ready).copy(session = inactive)
        handler.cancelForSessionReplacement(inactive)
        assertTrue(session.sameProfileOwner(inactive))
        assertNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
        assertFalse((state.value as RealsRootUiState.Ready).ownerPreview.open)
    }
    @Test fun revokedReadDropsCachedPreviewAndResumeCannotReopenClosedSurface() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        var calls = 0
        val handler = handler(state) {
            if (++calls == 1) ApiResult.Success(session) else ApiResult.Failure(ApiError.Backend(403, "ACCESS_DENIED", null, "denied"))
        }
        handler.open(); advanceUntilIdle(); handler.onResume(); advanceUntilIdle()
        assertNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
        handler.close(); handler.onResume(); advanceUntilIdle()
        assertEquals(2, calls)
        assertFalse((state.value as RealsRootUiState.Ready).ownerPreview.open)
    }
    @Test fun compatibleRenewalPreservesPendingReadAndScreen() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val gate = CompletableDeferred<Unit>()
        val handler = handler(state) { gate.await(); ApiResult.Success(session) }
        handler.open(); runCurrent()
        val renewed = session.copy(user = session.user.copy(email = "verified@example.test"))
        state.value = (state.value as RealsRootUiState.Ready).copy(session = renewed)
        handler.cancelForSessionReplacement(renewed)
        gate.complete(Unit); advanceUntilIdle()
        val preview = (state.value as RealsRootUiState.Ready).ownerPreview
        assertTrue(preview.open); assertFalse(preview.loading); assertNotNull(preview.profile)
    }

    @Test fun resumeKeepsSnapshotAndFailureDoesNotReplaceItWithLoading() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val handler = handler(state) {
            if (++calls == 1) ApiResult.Success(session) else { gate.await(); ApiResult.Failure(ApiError.Network("offline")) }
        }
        handler.open(); advanceUntilIdle()
        val confirmed = (state.value as RealsRootUiState.Ready).ownerPreview.profile
        handler.onResume(); runCurrent()
        val pending = (state.value as RealsRootUiState.Ready).ownerPreview
        assertSame(confirmed, pending.profile); assertFalse(pending.loading); assertTrue(pending.revalidating)
        gate.complete(Unit); advanceUntilIdle()
        val failed = (state.value as RealsRootUiState.Ready).ownerPreview
        assertSame(confirmed, failed.profile); assertFalse(failed.revalidating); assertNotNull(failed.error)
    }

    @Test fun realAuthenticationReplacementClearsSnapshotEvenForSameUser() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val handler = handler(state) { if (++calls > 1) gate.await(); ApiResult.Success(session) }
        handler.open(); advanceUntilIdle(); handler.onResume(); runCurrent()
        val replacement = session.copy(authenticationSessionId = "new-login")
        state.value = (state.value as RealsRootUiState.Ready).copy(session = replacement)
        handler.cancelForSessionReplacement(replacement)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse((state.value as RealsRootUiState.Ready).ownerPreview.open)
        assertNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
    }

    @Test fun newerOwnerSnapshotCausesAuthorizedReloadInsteadOfInstallingOldRead() = runTest {
        val session = TestDomain.session()
        val original = (session.profileSnapshot as ProfileSnapshot.Found).profile
        val newer = session.copy(profileSnapshot = ProfileSnapshot.Found(original.copy(displayName = "Nueva")))
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val handler = handler(state) { if (++calls == 1) { gate.await(); ApiResult.Success(session) } else ApiResult.Success(newer) }
        handler.open(); runCurrent()
        state.value = (state.value as RealsRootUiState.Ready).copy(session = newer)
        handler.cancelForSessionReplacement(newer)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("Nueva", (state.value as RealsRootUiState.Ready).ownerPreview.profile?.displayName)
        assertEquals(newer, (state.value as RealsRootUiState.Ready).session)
    }
    @Test fun rejectsMismatchedOwnerResponsesWithoutPublishingPhotosOrQuestions() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val other = (session.profileSnapshot as ProfileSnapshot.Found).profile.copy(userId = "other")
        val handler = handler(state) { ApiResult.Success(session.copy(profileSnapshot = ProfileSnapshot.Found(other))) }
        handler.open(); advanceUntilIdle()
        assertNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
        assertNotNull((state.value as RealsRootUiState.Ready).ownerPreview.error)
    }
    @Test fun projectionPreservesConfirmedOrderAndLimitsToThree() {
        val profile = TestDtos.profile().toDomain()
        val catalog = ProfileQuestionCatalog("1", (1..5).map { ProfileQuestion("q$it", 1, 1, "Pregunta $it", it) })
        val answers = (1..5).map { ProfileQuestionAnswer("q$it", 1, "Respuesta $it", 6 - it, true, "t", "t") }
        val photo = TestDtos.photo().toDomain()
        val result = ownerProfilePreview(profile, listOf(photo.copy(id = "last", position = 3), photo.copy(id = "first", position = 1)), catalog, answers)
        assertEquals(listOf("q5", "q4", "q3"), result.profileQuestions.map { it.questionId })
        assertEquals(listOf("first", "last"), result.photos.map { it.id })
    }

    @Test fun overlappingReopenRejectsEvenNonCooperativeLateCompletion() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val handler = handler(state) {
            val call = ++calls
            if (call == 1) withContext(NonCancellable) { gate.await() }
            val profile = (session.profileSnapshot as ProfileSnapshot.Found).profile.copy(displayName = "request-$call")
            ApiResult.Success(session.copy(profileSnapshot = ProfileSnapshot.Found(profile)))
        }
        handler.open(); runCurrent()
        handler.open(); runCurrent()
        assertEquals("request-2", (state.value as RealsRootUiState.Ready).ownerPreview.profile?.displayName)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("request-2", (state.value as RealsRootUiState.Ready).ownerPreview.profile?.displayName)
    }
    @Test fun projectionFiltersPrivateUnapprovedAndObsoleteData() {
        val profile = TestDtos.profile().toDomain()
        val catalog = TestDtos.profileQuestionCatalog().toDomain()
        val answer = TestDtos.profileQuestionAnswer(selectedPosition = 1).toDomain()
        val photo = TestDtos.photo().toDomain()
        val projected = ownerProfilePreview(profile, listOf(photo.copy(moderationStatus = "NEEDS_REVIEW"), photo.copy(id = "approved")), catalog, listOf(answer))
        assertEquals(listOf("approved"), projected.photos.map { it.id })
        assertEquals(listOf(answer.questionId), projected.profileQuestions.map { it.questionId })
        assertTrue(projected.affinityIndicators.isEmpty())
        assertFalse(projected.partnerPersonalMessageSubmitted)
        assertEquals(profile.city, projected.city)
        assertTrue(ownerProfilePreview(profile, emptyList(), catalog, listOf(answer.copy(questionSemanticVersion = 999))).profileQuestions.isEmpty())
        assertTrue(ownerProfilePreview(profile, emptyList(), catalog, listOf(answer.copy(current = false))).profileQuestions.isEmpty())
    }

    @Test fun opensDraftAndActiveWithFreshConfirmedData() = runTest {
        for (status in listOf(ProfileStatus.Draft, ProfileStatus.Active)) {
            val session = TestDomain.session()
            val profile = (session.profileSnapshot as ProfileSnapshot.Found).profile.copy(status = status)
            val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
            var loads = 0
            val handler = handler(state) { loads++; ApiResult.Success(session.copy(profileSnapshot = ProfileSnapshot.Found(profile))) }
            handler.open(); advanceUntilIdle()
            val preview = (state.value as RealsRootUiState.Ready).ownerPreview
            assertNotNull(preview.profile)
            assertEquals(status == ProfileStatus.Draft, preview.draft)
            handler.close(); handler.open(); advanceUntilIdle()
            assertEquals(2, loads)
        }
    }

    @Test fun closeAndIdentityReplacementNeverInstallLateResults() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        val gate = CompletableDeferred<Unit>()
        val handler = handler(state) { gate.await(); ApiResult.Success(session) }
        handler.open(); runCurrent(); handler.close(); gate.complete(Unit); advanceUntilIdle()
        assertFalse((state.value as RealsRootUiState.Ready).ownerPreview.open)
        val second = CompletableDeferred<Unit>()
        val replacing = handler(state) { second.await(); ApiResult.Success(session) }
        replacing.open(); runCurrent()
        state.value = RealsRootUiState.Ready(session.copy(user = session.user.copy(id = "other")))
        second.complete(Unit); advanceUntilIdle()
        assertNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
    }

    @Test fun failureNeverPublishesPartialSnapshotAndRetryWorks() = runTest {
        val session = TestDomain.session()
        val state = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Ready(session))
        var fail = true
        val handler = handler(state) { if (fail) ApiResult.Failure(ApiError.Network("offline")) else ApiResult.Success(session) }
        handler.open(); advanceUntilIdle()
        assertNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
        assertNotNull((state.value as RealsRootUiState.Ready).ownerPreview.error)
        fail = false; handler.open(); advanceUntilIdle()
        assertNotNull((state.value as RealsRootUiState.Ready).ownerPreview.profile)
    }

    private fun TestScope.handler(state: MutableStateFlow<RealsRootUiState>, profile: suspend (BackendUser) -> ApiResult<ProvisionedSession>) = OwnerProfilePreviewHandler(
        state, this, profile, { ApiResult.Success(emptyList()) },
        { ApiResult.Success(TestDtos.profileQuestionCatalog().toDomain()) }, { ApiResult.Success(emptyList()) },
    )
}
