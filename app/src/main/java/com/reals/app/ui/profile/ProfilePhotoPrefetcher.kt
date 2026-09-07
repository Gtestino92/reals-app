package com.reals.app.ui.profile

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.reals.app.domain.model.ProfilePhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal const val ProfilePhotoPrefetchMaxConcurrency = 2

internal data class ProfilePhotoPrefetchPlan(
    val profileScopeKey: String,
    val maxConcurrency: Int,
    val thumbnailDecodeSizePx: Int,
    val photos: List<ProfilePhoto>,
)

interface ProfilePhotoPrefetcher {
    fun prefetchOwnProfilePhotos(
        scope: CoroutineScope,
        profileId: String,
        photos: List<ProfilePhoto>,
    )

    fun cancel()
}

object NoOpProfilePhotoPrefetcher : ProfilePhotoPrefetcher {
    override fun prefetchOwnProfilePhotos(
        scope: CoroutineScope,
        profileId: String,
        photos: List<ProfilePhoto>,
    ) = Unit

    override fun cancel() = Unit
}

internal class AndroidProfilePhotoPrefetcher(
    context: Context,
    private val imageLoader: ImageLoader = SingletonImageLoader.get(context),
) : ProfilePhotoPrefetcher {
    private val appContext = context.applicationContext
    private var activeJob: Job? = null
    private var activeScopeKey: String? = null

    override fun prefetchOwnProfilePhotos(
        scope: CoroutineScope,
        profileId: String,
        photos: List<ProfilePhoto>,
    ) {
        val plan = ownProfilePhotoPrefetchPlan(profileId = profileId, photos = photos)
        if (plan == null) {
            cancel()
            return
        }
        if (activeScopeKey == plan.profileScopeKey && activeJob?.isActive == true) return

        activeJob?.cancel()
        activeScopeKey = plan.profileScopeKey
        activeJob = scope.launch {
            plan.photos
                .chunked(plan.maxConcurrency)
                .forEach { batch ->
                    coroutineScope {
                        batch.map { photo ->
                            async { prefetchPhoto(photo, plan.thumbnailDecodeSizePx) }
                        }.awaitAll()
                    }
                }
        }
    }

    override fun cancel() {
        activeJob?.cancel()
        activeJob = null
        activeScopeKey = null
    }

    private suspend fun prefetchPhoto(photo: ProfilePhoto, thumbnailDecodeSizePx: Int) {
        if (!photo.url.toEmulatorReachableUrl().isRenderableImageUrl()) return
        val memoryCacheKey = profilePhotoMemoryCacheKey(
            photo = photo,
            variant = ProfilePhotoImageVariant.Thumbnail,
            widthPx = thumbnailDecodeSizePx,
            heightPx = thumbnailDecodeSizePx,
            displayUrl = photo.url.toEmulatorReachableUrl(),
        )
        if (imageLoader.memoryCache?.get(memoryCacheKey) != null) return
        imageLoader.execute(
            profilePhotoImageRequest(
                context = appContext,
                photo = photo,
                variant = ProfilePhotoImageVariant.Thumbnail,
                widthPx = thumbnailDecodeSizePx,
                heightPx = thumbnailDecodeSizePx,
            )
        )
    }
}

internal fun ownProfilePhotoPrefetchPlan(
    profileId: String,
    photos: List<ProfilePhoto>,
    maxConcurrency: Int = ProfilePhotoPrefetchMaxConcurrency,
    thumbnailDecodeSizePx: Int = ProfilePhotoGridThumbnailDecodeSizePx,
): ProfilePhotoPrefetchPlan? {
    val orderedPhotos = photos
        .filter { it.position in ProfilePhotoGridPositions }
        .sortedBy { it.position }
    if (orderedPhotos.isEmpty()) return null

    return ProfilePhotoPrefetchPlan(
        profileScopeKey = ownProfilePhotoPrefetchScopeKey(profileId, orderedPhotos),
        maxConcurrency = maxConcurrency.coerceAtLeast(1),
        thumbnailDecodeSizePx = thumbnailDecodeSizePx,
        photos = orderedPhotos,
    )
}

internal fun ownProfilePhotoPrefetchScopeKey(
    profileId: String,
    photos: List<ProfilePhoto>,
): String =
    buildString {
        append(profileId.trim())
        append(':')
        append(photos.joinToString(separator = "|") { it.stableProfilePhotoCacheKey() })
    }
