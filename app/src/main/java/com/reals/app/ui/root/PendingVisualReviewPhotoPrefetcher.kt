package com.reals.app.ui.root

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.reals.app.core.network.ApiResult
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.model.isApprovedForExternalDisplay
import com.reals.app.ui.profile.ProfilePhotoImageVariant
import com.reals.app.ui.profile.isRenderableImageUrl
import com.reals.app.ui.profile.profilePhotoImageRequest
import com.reals.app.ui.profile.profilePhotoMemoryCacheKey
import com.reals.app.ui.profile.toEmulatorReachableUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val PendingVisualReviewPhotoPrefetchMaxPhotos = 6
internal const val PendingVisualReviewPhotoPrefetchMaxImageConcurrency = 2
internal const val PendingVisualReviewProfileFetchMaxConcurrency = 2

internal data class PendingVisualReviewProfilePhotos(
    val matchId: String,
    val photos: List<ProfilePhoto>,
)

interface PendingVisualReviewPhotoPrefetcher {
    fun prefetchPendingVisualReviews(
        scope: CoroutineScope,
        sessionScopeKey: String,
        pendingActions: List<HomePendingAction>,
        getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
    )

    fun cancel()
}

object NoOpPendingVisualReviewPhotoPrefetcher : PendingVisualReviewPhotoPrefetcher {
    override fun prefetchPendingVisualReviews(
        scope: CoroutineScope,
        sessionScopeKey: String,
        pendingActions: List<HomePendingAction>,
        getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
    ) = Unit

    override fun cancel() = Unit
}

internal class CoroutinePendingVisualReviewPhotoPrefetcher(
    private val imagePrefetcher: suspend (ProfilePhoto) -> Unit,
    private val maxPhotos: Int = PendingVisualReviewPhotoPrefetchMaxPhotos,
    private val maxImageConcurrency: Int = PendingVisualReviewPhotoPrefetchMaxImageConcurrency,
    private val maxProfileConcurrency: Int = PendingVisualReviewProfileFetchMaxConcurrency,
) : PendingVisualReviewPhotoPrefetcher {
    private var activeJob: Job? = null
    private var activePlanKey: String? = null
    private var generation = 0L

    override fun prefetchPendingVisualReviews(
        scope: CoroutineScope,
        sessionScopeKey: String,
        pendingActions: List<HomePendingAction>,
        getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
    ) {
        if (maxPhotos <= 0) {
            cancel()
            return
        }
        val matchIds = pendingVisualReviewMatchIds(pendingActions)
            .take(maxPhotos)
        if (matchIds.isEmpty()) {
            cancel()
            return
        }

        val planKey = pendingVisualReviewPrefetchPlanKey(sessionScopeKey, matchIds)
        if (activePlanKey == planKey && activeJob?.isActive == true) return

        activeJob?.cancel()
        generation += 1
        val planGeneration = generation
        activePlanKey = planKey
        val job = scope.launch {
            val profiles = fetchVisualReviewProfilePhotos(
                matchIds = matchIds,
                maxConcurrency = maxProfileConcurrency,
                getVisualProfile = getVisualProfile,
            )
            if (planGeneration != generation) return@launch
            val candidates = pendingVisualReviewPhotoPrefetchCandidates(
                profiles = profiles,
                maxPhotos = maxPhotos,
            )
            prefetchPhotos(
                photos = candidates,
                maxConcurrency = maxImageConcurrency,
                planGeneration = planGeneration,
            )
        }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob == job) {
                activeJob = null
            }
        }
    }

    override fun cancel() {
        generation += 1
        activeJob?.cancel()
        activeJob = null
        activePlanKey = null
    }

    private suspend fun fetchVisualReviewProfilePhotos(
        matchIds: List<String>,
        maxConcurrency: Int,
        getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
    ): List<PendingVisualReviewProfilePhotos> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        matchIds.map { matchId ->
            async {
                semaphore.withPermit {
                    try {
                        when (val result = getVisualProfile(matchId)) {
                            is ApiResult.Success -> PendingVisualReviewProfilePhotos(
                                matchId = matchId,
                                photos = visualReviewProfilePhotosForPrefetch(result.value),
                            )

                            is ApiResult.Failure -> null
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun prefetchPhotos(
        photos: List<ProfilePhoto>,
        maxConcurrency: Int,
        planGeneration: Long,
    ) = coroutineScope {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        photos.map { photo ->
            async {
                semaphore.withPermit {
                    if (planGeneration != generation) return@withPermit
                    try {
                        imagePrefetcher(photo)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Throwable) {
                        Unit
                    }
                }
            }
        }.awaitAll()
    }
}

internal class AndroidPendingVisualReviewPhotoPrefetcher(
    context: Context,
    imageLoader: ImageLoader = SingletonImageLoader.get(context),
) : PendingVisualReviewPhotoPrefetcher {
    private val appContext = context.applicationContext
    private val delegate = CoroutinePendingVisualReviewPhotoPrefetcher(
        imagePrefetcher = { photo -> prefetchPhoto(imageLoader, photo) },
    )

    override fun prefetchPendingVisualReviews(
        scope: CoroutineScope,
        sessionScopeKey: String,
        pendingActions: List<HomePendingAction>,
        getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
    ) = delegate.prefetchPendingVisualReviews(
        scope = scope,
        sessionScopeKey = sessionScopeKey,
        pendingActions = pendingActions,
        getVisualProfile = getVisualProfile,
    )

    override fun cancel() = delegate.cancel()

    private suspend fun prefetchPhoto(imageLoader: ImageLoader, photo: ProfilePhoto) {
        val displayUrl = photo.url.toEmulatorReachableUrl()
        if (!displayUrl.isRenderableImageUrl()) return
        val memoryCacheKey = profilePhotoMemoryCacheKey(
            photo = photo,
            variant = ProfilePhotoImageVariant.Full,
            displayUrl = displayUrl,
        )
        if (imageLoader.memoryCache?.get(memoryCacheKey) != null) return
        imageLoader.execute(
            profilePhotoImageRequest(
                context = appContext,
                photo = photo,
                variant = ProfilePhotoImageVariant.Full,
            )
        )
    }
}

internal fun pendingVisualReviewMatchIds(actions: List<HomePendingAction>): List<String> =
    actions
        .mapNotNull { action ->
            (action as? HomePendingAction.VisualReview)
                ?.matchId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        .distinct()

internal fun visualReviewProfilePhotosForPrefetch(profile: VisualProfile): List<ProfilePhoto> =
    profile.photos
        .filter { it.isApprovedForExternalDisplay() }
        .filter { it.url.toEmulatorReachableUrl().isRenderableImageUrl() }
        .sortedBy { it.position }

internal fun pendingVisualReviewPhotoPrefetchCandidates(
    profiles: List<PendingVisualReviewProfilePhotos>,
    maxPhotos: Int = PendingVisualReviewPhotoPrefetchMaxPhotos,
): List<ProfilePhoto> {
    if (maxPhotos <= 0) return emptyList()
    val candidates = mutableListOf<ProfilePhoto>()
    var photoIndex = 0
    while (candidates.size < maxPhotos) {
        var addedAtThisIndex = false
        profiles.forEach { profile ->
            val photo = profile.photos.getOrNull(photoIndex)
            if (photo != null && candidates.size < maxPhotos) {
                candidates += photo
                addedAtThisIndex = true
            }
        }
        if (!addedAtThisIndex) break
        photoIndex += 1
    }
    return candidates
}

internal fun pendingVisualReviewPrefetchPlanKey(
    sessionScopeKey: String,
    matchIds: List<String>,
): String =
    buildString {
        append(sessionScopeKey.trim())
        append(':')
        append(matchIds.joinToString(separator = "|"))
    }
