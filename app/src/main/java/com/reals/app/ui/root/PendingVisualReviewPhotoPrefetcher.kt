package com.reals.app.ui.root

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.reals.app.core.network.ApiResult
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.model.isApprovedForExternalDisplay
import com.reals.app.ui.profile.ProfilePhotoPresentationAspectRatio
import com.reals.app.ui.profile.ProfilePhotoImageVariant
import com.reals.app.ui.profile.isRenderableImageUrl
import com.reals.app.ui.profile.profilePhotoImageRequest
import com.reals.app.ui.profile.profilePhotoMemoryCacheKey
import com.reals.app.ui.profile.stableProfilePhotoCacheKey
import com.reals.app.ui.profile.toEmulatorReachableUrl
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val PendingVisualReviewPhotoPrefetchMaxPhotos = 6
internal const val PendingVisualReviewPhotoPrefetchMaxImageConcurrency = 2
internal const val PendingVisualReviewProfileFetchMaxConcurrency = 2
internal const val PendingVisualReviewFullPrefetchMaxWidthPx = 1440
private const val PendingVisualReviewHorizontalChromeDp = 84

internal data class PendingVisualReviewProfilePhotos(
    val matchId: String,
    val photos: List<ProfilePhoto>,
)

internal data class PendingVisualReviewFullPrefetchSize(
    val widthPx: Int,
    val heightPx: Int,
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
            runVisualReviewPhotoPrefetchPipeline(
                matchIds = matchIds,
                maxConcurrency = maxProfileConcurrency,
                maxImageConcurrency = maxImageConcurrency,
                maxPhotos = maxPhotos,
                planGeneration = planGeneration,
                getVisualProfile = getVisualProfile,
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

    private suspend fun runVisualReviewPhotoPrefetchPipeline(
        matchIds: List<String>,
        maxConcurrency: Int,
        maxImageConcurrency: Int,
        maxPhotos: Int,
        planGeneration: Long,
        getVisualProfile: suspend (String) -> ApiResult<VisualProfile>,
    ) = coroutineScope {
        val profileResults = Channel<PendingVisualReviewProfilePhotos?>(Channel.UNLIMITED)
        val imageSemaphore = Semaphore(maxImageConcurrency.coerceAtLeast(1))
        val profileJobs = mutableListOf<Job>()
        val imageJobs = mutableListOf<Job>()
        val discoveredProfiles = mutableListOf<PendingVisualReviewProfilePhotos>()
        val scheduledPhotoKeys = linkedSetOf<String>()
        var nextProfileIndex = 0
        var activeProfileFetches = 0
        var scheduledImageCount = 0

        fun scheduleImagePrefetch(photo: ProfilePhoto): Boolean {
            if (scheduledImageCount >= maxPhotos || planGeneration != generation) return false
            val photoKey = photo.stableProfilePhotoCacheKey()
            if (!scheduledPhotoKeys.add(photoKey)) return false
            scheduledImageCount += 1
            imageJobs += launch {
                imageSemaphore.withPermit {
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
            return true
        }

        fun launchNextProfileFetches() {
            while (
                activeProfileFetches < maxConcurrency.coerceAtLeast(1) &&
                nextProfileIndex < matchIds.size &&
                scheduledImageCount + activeProfileFetches < maxPhotos &&
                planGeneration == generation
            ) {
                val matchId = matchIds[nextProfileIndex]
                nextProfileIndex += 1
                activeProfileFetches += 1
                profileJobs += launch {
                    val profilePhotos = try {
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
                    try {
                        profileResults.send(profilePhotos)
                    } catch (exception: CancellationException) {
                        throw exception
                    }
                }
            }
        }

        launchNextProfileFetches()
        while (activeProfileFetches > 0 && planGeneration == generation) {
            val profilePhotos = profileResults.receive()
            activeProfileFetches -= 1
            if (profilePhotos != null) {
                discoveredProfiles += profilePhotos
                profilePhotos.photos.firstOrNull()?.let(::scheduleImagePrefetch)
            }
            launchNextProfileFetches()
        }

        if (
            scheduledImageCount < maxPhotos &&
            nextProfileIndex >= matchIds.size &&
            planGeneration == generation
        ) {
            pendingVisualReviewPhotoPrefetchCandidates(
                profiles = discoveredProfiles.sortedBy { matchIds.indexOf(it.matchId) },
                maxPhotos = maxPhotos - scheduledImageCount,
                startingPhotoIndex = 1,
                excludedPhotoKeys = scheduledPhotoKeys,
            ).forEach(::scheduleImagePrefetch)
        }

        profileResults.close()
        if (scheduledImageCount >= maxPhotos) {
            profileJobs.filter { it.isActive }.forEach { it.cancel() }
        }
        imageJobs.joinAll()
    }
}

internal class AndroidPendingVisualReviewPhotoPrefetcher(
    context: Context,
    imageLoader: ImageLoader = SingletonImageLoader.get(context),
) : PendingVisualReviewPhotoPrefetcher {
    private val appContext = context.applicationContext
    private val prefetchSize = pendingVisualReviewFullPrefetchSize(
        screenWidthPx = context.resources.displayMetrics.widthPixels,
        density = context.resources.displayMetrics.density,
    )
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
            widthPx = prefetchSize.widthPx,
            heightPx = prefetchSize.heightPx,
            displayUrl = displayUrl,
        )
        if (imageLoader.memoryCache?.get(memoryCacheKey) != null) return
        imageLoader.execute(
            profilePhotoImageRequest(
                context = appContext,
                photo = photo,
                variant = ProfilePhotoImageVariant.Full,
                widthPx = prefetchSize.widthPx,
                heightPx = prefetchSize.heightPx,
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
    startingPhotoIndex: Int = 0,
    excludedPhotoKeys: Set<String> = emptySet(),
): List<ProfilePhoto> {
    if (maxPhotos <= 0) return emptyList()
    val candidates = mutableListOf<ProfilePhoto>()
    val candidateKeys = excludedPhotoKeys.toMutableSet()
    var photoIndex = startingPhotoIndex.coerceAtLeast(0)
    while (candidates.size < maxPhotos) {
        var addedAtThisIndex = false
        profiles.forEach { profile ->
            val photo = profile.photos.getOrNull(photoIndex)
            if (
                photo != null &&
                candidateKeys.add(photo.stableProfilePhotoCacheKey()) &&
                candidates.size < maxPhotos
            ) {
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

/**
 * Approximates the large visual-review photo viewport from device width minus
 * the screen/card horizontal chrome, then caps width to avoid oversized decodes.
 */
internal fun pendingVisualReviewFullPrefetchSize(
    screenWidthPx: Int,
    density: Float,
    maxWidthPx: Int = PendingVisualReviewFullPrefetchMaxWidthPx,
): PendingVisualReviewFullPrefetchSize {
    val horizontalChromePx = (PendingVisualReviewHorizontalChromeDp * density.coerceAtLeast(1f))
        .roundToInt()
    val widthPx = (screenWidthPx - horizontalChromePx)
        .coerceAtLeast(1)
        .coerceAtMost(maxWidthPx.coerceAtLeast(1))
    val heightPx = (widthPx / ProfilePhotoPresentationAspectRatio)
        .roundToInt()
        .coerceAtLeast(1)
    return PendingVisualReviewFullPrefetchSize(widthPx = widthPx, heightPx = heightPx)
}
