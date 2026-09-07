package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.VisualProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingVisualReviewPhotoPrefetcherTest {
    @Test
    fun pendingVisualReviewMatchIdsFiltersOnlyVisualReviewActions() {
        val matchIds = pendingVisualReviewMatchIds(
            listOf(
                HomePendingAction.FirstChat("first-chat", "chat-1", partner = null),
                HomePendingAction.VisualReview(" visual-a ", partner = null),
                HomePendingAction.Unknown("SOMETHING_ELSE"),
                HomePendingAction.VisualReview("visual-b", partner = null),
            )
        )

        assertEquals(listOf("visual-a", "visual-b"), matchIds)
    }

    @Test
    fun roundRobinPrefetchCandidatesPrioritizeFirstPhotoPerProfile() {
        val candidates = pendingVisualReviewPhotoPrefetchCandidates(
            profiles = listOf(
                profilePhotos("A", "A1", "A2"),
                profilePhotos("B", "B1", "B2"),
                profilePhotos("C", "C1", "C2"),
            )
        )

        assertEquals(listOf("A1", "B1", "C1", "A2", "B2", "C2"), candidates.map { it.id })
    }

    @Test
    fun roundRobinPrefetchCandidatesRespectMaxBudgetSix() {
        val candidates = pendingVisualReviewPhotoPrefetchCandidates(
            profiles = listOf(
                profilePhotos("A", "A1", "A2", "A3"),
                profilePhotos("B", "B1", "B2", "B3"),
                profilePhotos("C", "C1", "C2", "C3"),
            )
        )

        assertEquals(6, candidates.size)
        assertEquals(listOf("A1", "B1", "C1", "A2", "B2", "C2"), candidates.map { it.id })
    }

    @Test
    fun roundRobinPrefetchCandidatesHandleUnevenAndEmptyProfiles() {
        val candidates = pendingVisualReviewPhotoPrefetchCandidates(
            profiles = listOf(
                profilePhotos("A", "A1", "A2", "A3"),
                profilePhotos("B"),
                profilePhotos("C", "C1"),
                profilePhotos("D", "D1", "D2"),
            )
        )

        assertEquals(listOf("A1", "C1", "D1", "A2", "D2", "A3"), candidates.map { it.id })
    }

    @Test
    fun roundRobinPrefetchCandidatesKeepPendingOrderStable() {
        val candidates = pendingVisualReviewPhotoPrefetchCandidates(
            profiles = listOf(
                profilePhotos("C", "C1"),
                profilePhotos("A", "A1"),
                profilePhotos("B", "B1"),
            )
        )

        assertEquals(listOf("C1", "A1", "B1"), candidates.map { it.id })
    }

    @Test
    fun visualProfilePhotosForPrefetchKeepsApprovedRenderablePhotosOnly() {
        val photos = visualReviewProfilePhotosForPrefetch(
            visualProfile(
                matchId = "match-a",
                photos = listOf(
                    photo("pending", position = 1, moderationStatus = "NEEDS_REVIEW"),
                    photo("relative", position = 2, url = "profile-photos/relative.jpg"),
                    photo("approved", position = 3),
                    photo("rejected", position = 4, moderationStatus = "REJECTED"),
                ),
            )
        )

        assertEquals(listOf("approved"), photos.map { it.id })
    }

    @Test
    fun failedVisualProfileFetchDoesNotCancelRemainingPrefetch() = runTest {
        val prefetched = mutableListOf<String>()
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = { photo -> prefetched += photo.id },
        )

        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = listOf(
                HomePendingAction.VisualReview("match-a", partner = null),
                HomePendingAction.VisualReview("match-b", partner = null),
                HomePendingAction.VisualReview("match-c", partner = null),
            ),
            getVisualProfile = { matchId ->
                if (matchId == "match-a") {
                    ApiResult.Failure(ApiError.Unexpected("boom"))
                } else {
                    ApiResult.Success(visualProfile(matchId, listOf(photo("$matchId-1"))))
                }
            },
        )

        testScheduler.advanceUntilIdle()

        assertEquals(listOf("match-b-1", "match-c-1"), prefetched)
    }

    @Test
    fun imagePrefetchConcurrencyIsLimitedToTwoActiveRequests() = runTest {
        var active = 0
        var maxActive = 0
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = {
                active += 1
                maxActive = maxOf(maxActive, active)
                delay(100)
                active -= 1
            },
        )

        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = listOf(
                HomePendingAction.VisualReview("match-a", partner = null),
                HomePendingAction.VisualReview("match-b", partner = null),
                HomePendingAction.VisualReview("match-c", partner = null),
            ),
            getVisualProfile = { matchId ->
                ApiResult.Success(
                    visualProfile(
                        matchId = matchId,
                        photos = listOf(photo("$matchId-1"), photo("$matchId-2"), photo("$matchId-3")),
                    )
                )
            },
        )

        testScheduler.advanceUntilIdle()

        assertEquals(PendingVisualReviewPhotoPrefetchMaxImageConcurrency, maxActive)
    }

    @Test
    fun visualProfileFetchConcurrencyIsLimitedToTwoActiveRequests() = runTest {
        var active = 0
        var maxActive = 0
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = {},
        )

        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = listOf(
                HomePendingAction.VisualReview("match-a", partner = null),
                HomePendingAction.VisualReview("match-b", partner = null),
                HomePendingAction.VisualReview("match-c", partner = null),
                HomePendingAction.VisualReview("match-d", partner = null),
            ),
            getVisualProfile = { matchId ->
                active += 1
                maxActive = maxOf(maxActive, active)
                delay(100)
                active -= 1
                ApiResult.Success(visualProfile(matchId, listOf(photo("$matchId-1"))))
            },
        )

        testScheduler.advanceUntilIdle()

        assertEquals(PendingVisualReviewProfileFetchMaxConcurrency, maxActive)
    }

    @Test
    fun visualProfileFetchesAreCappedByUsefulPhotoBudget() = runTest {
        val fetchCalls = mutableListOf<String>()
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = {},
        )

        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = (1..10).map { index ->
                HomePendingAction.VisualReview("match-$index", partner = null)
            },
            getVisualProfile = { matchId ->
                fetchCalls += matchId
                ApiResult.Success(visualProfile(matchId, listOf(photo("$matchId-1"))))
            },
        )
        testScheduler.advanceUntilIdle()

        assertEquals((1..6).map { "match-$it" }, fetchCalls)
    }

    @Test
    fun replacingPlanCancelsOldPlanBeforeImagePrefetch() = runTest {
        val oldProfile = CompletableDeferred<VisualProfile>()
        val newProfile = CompletableDeferred<VisualProfile>()
        val prefetched = mutableListOf<String>()
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = { photo -> prefetched += photo.id },
        )

        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = listOf(HomePendingAction.VisualReview("old-match", partner = null)),
            getVisualProfile = { oldProfile.await().let { ApiResult.Success(it) } },
        )
        runCurrent()
        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = listOf(HomePendingAction.VisualReview("new-match", partner = null)),
            getVisualProfile = { newProfile.await().let { ApiResult.Success(it) } },
        )

        oldProfile.complete(visualProfile("old-match", listOf(photo("old-photo"))))
        newProfile.complete(visualProfile("new-match", listOf(photo("new-photo"))))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("new-photo"), prefetched)
    }

    @Test
    fun activeIdenticalPlanDoesNotStartDuplicateWork() = runTest {
        val profile = CompletableDeferred<VisualProfile>()
        var fetchCalls = 0
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = {},
        )
        val actions = listOf(HomePendingAction.VisualReview("match-a", partner = null))
        val getProfile: suspend (String) -> ApiResult<VisualProfile> = {
            fetchCalls += 1
            ApiResult.Success(profile.await())
        }

        prefetcher.prefetchPendingVisualReviews(this, "user-1:profile-1", actions, getProfile)
        runCurrent()
        prefetcher.prefetchPendingVisualReviews(this, "user-1:profile-1", actions, getProfile)
        runCurrent()
        prefetcher.cancel()

        assertEquals(1, fetchCalls)
    }

    @Test
    fun firstChatOnlyPlanNeverCallsGetVisualProfile() = runTest {
        var fetchCalls = 0
        val prefetcher = CoroutinePendingVisualReviewPhotoPrefetcher(
            imagePrefetcher = {},
        )

        prefetcher.prefetchPendingVisualReviews(
            scope = this,
            sessionScopeKey = "user-1:profile-1",
            pendingActions = listOf(HomePendingAction.FirstChat("match-a", "chat-a", partner = null)),
            getVisualProfile = {
                fetchCalls += 1
                ApiResult.Success(visualProfile(it, listOf(photo("$it-1"))))
            },
        )
        testScheduler.advanceUntilIdle()

        assertEquals(0, fetchCalls)
    }

    private fun profilePhotos(matchId: String, vararg ids: String): PendingVisualReviewProfilePhotos =
        PendingVisualReviewProfilePhotos(
            matchId = matchId,
            photos = ids.mapIndexed { index, id -> photo(id, position = index + 1) },
        )

    private fun visualProfile(
        matchId: String,
        photos: List<ProfilePhoto>,
    ): VisualProfile = VisualProfile(
        profileId = "profile-$matchId",
        displayName = "Profile $matchId",
        age = 30,
        bio = null,
        photos = photos,
        visualExpiresAt = null,
        myPersonalMessageSubmitted = false,
        partnerPersonalMessageSubmitted = false,
        partnerPersonalMessageRead = false,
        decisionRequiresPartnerPersonalMessageRead = false,
    )

    private fun photo(
        id: String,
        position: Int = 1,
        url: String = "https://cdn.reals.local/photos/$id.jpg?X-Amz-Signature=test",
        moderationStatus: String = "APPROVED",
    ): ProfilePhoto = ProfilePhoto(
        id = id,
        url = url,
        position = position,
        isPersonPhoto = true,
        isFullBody = false,
        validationStatus = "VALIDATED",
        moderationStatus = moderationStatus,
    )
}
