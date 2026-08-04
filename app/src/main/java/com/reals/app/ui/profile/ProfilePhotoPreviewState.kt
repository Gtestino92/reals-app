package com.reals.app.ui.profile

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

internal data class PendingProfilePhotoPreview(
    val action: ProfilePhotoActionPresentation,
    val uriString: String,
    val generation: String,
    val cropConfirmedAtElapsedMillis: Long,
    val oldCanonicalCacheKey: String? = null,
)

internal sealed interface ProfilePhotoPreviewState {
    data object None : ProfilePhotoPreviewState

    data class Uploading(
        val preview: PendingProfilePhotoPreview,
    ) : ProfilePhotoPreviewState

    data class AwaitingRemote(
        val preview: PendingProfilePhotoPreview,
        val remotePhotoId: String,
        val remoteUrl: String,
        val uploadResponseAtElapsedMillis: Long,
    ) : ProfilePhotoPreviewState
}

internal val ProfilePhotoPreviewState.preview: PendingProfilePhotoPreview?
    get() = when (this) {
        ProfilePhotoPreviewState.None -> null
        is ProfilePhotoPreviewState.Uploading -> preview
        is ProfilePhotoPreviewState.AwaitingRemote -> preview
    }

internal fun startProfilePhotoPreview(
    action: ProfilePhotoActionPresentation,
    uriString: String,
    generation: String,
    cropConfirmedAtElapsedMillis: Long,
    oldCanonicalCacheKey: String?,
): ProfilePhotoPreviewState.Uploading =
    ProfilePhotoPreviewState.Uploading(
        PendingProfilePhotoPreview(
            action = action,
            uriString = uriString,
            generation = generation,
            cropConfirmedAtElapsedMillis = cropConfirmedAtElapsedMillis,
            oldCanonicalCacheKey = oldCanonicalCacheKey,
        ),
    )

internal fun ProfilePhotoPreviewState.onUploadSucceeded(
    photos: List<com.reals.app.domain.model.ProfilePhoto>,
    uploadResponseAtElapsedMillis: Long,
): ProfilePhotoPreviewState =
    when (this) {
        is ProfilePhotoPreviewState.Uploading -> {
            val remotePhoto = when (preview.action.kind) {
                ProfilePhotoActionKind.Add -> photos.firstOrNull { it.position == preview.action.position }
                ProfilePhotoActionKind.Replace -> {
                    val photoId = preview.action.photoId
                    photos.firstOrNull { it.id == photoId }
                        ?: photos.firstOrNull { it.position == preview.action.position }
                }
                ProfilePhotoActionKind.Delete -> null
            }
            if (remotePhoto == null) {
                ProfilePhotoPreviewState.None
            } else {
                ProfilePhotoPreviewState.AwaitingRemote(
                    preview = preview,
                    remotePhotoId = remotePhoto.id,
                    remoteUrl = remotePhoto.url,
                    uploadResponseAtElapsedMillis = uploadResponseAtElapsedMillis,
                )
            }
        }
        else -> this
    }

internal fun ProfilePhotoPreviewState.onUploadFailed(): ProfilePhotoPreviewState =
    if (this is ProfilePhotoPreviewState.Uploading) ProfilePhotoPreviewState.None else this

internal fun ProfilePhotoPreviewState.onRemoteSucceeded(generation: String): ProfilePhotoPreviewState =
    when (this) {
        is ProfilePhotoPreviewState.AwaitingRemote ->
            if (preview.generation == generation) ProfilePhotoPreviewState.None else this
        else -> this
    }

internal fun ProfilePhotoPreviewState.previewForPosition(position: Int): PendingProfilePhotoPreview? =
    preview?.takeIf { it.action.targetsPosition(position) }

internal fun ProfilePhotoPreviewState.awaitingRemoteForPhoto(
    photoId: String,
): ProfilePhotoPreviewState.AwaitingRemote? =
    (this as? ProfilePhotoPreviewState.AwaitingRemote)
        ?.takeIf { it.remotePhotoId == photoId }

internal val ProfilePhotoPreviewStateSaver: Saver<ProfilePhotoPreviewState, Any> = listSaver(
    save = { state ->
        when (state) {
            ProfilePhotoPreviewState.None -> listOf("none")
            is ProfilePhotoPreviewState.Uploading -> listOf(
                "uploading",
                state.preview.action.kind.name,
                state.preview.action.position,
                state.preview.action.photoId,
                state.preview.uriString,
                state.preview.generation,
                state.preview.cropConfirmedAtElapsedMillis,
                state.preview.oldCanonicalCacheKey,
            )
            is ProfilePhotoPreviewState.AwaitingRemote -> listOf(
                "awaiting",
                state.preview.action.kind.name,
                state.preview.action.position,
                state.preview.action.photoId,
                state.preview.uriString,
                state.preview.generation,
                state.preview.cropConfirmedAtElapsedMillis,
                state.preview.oldCanonicalCacheKey,
                state.remotePhotoId,
                state.remoteUrl,
                state.uploadResponseAtElapsedMillis,
            )
        }
    },
    restore = { values ->
        val phase = values.getOrNull(0) as? String
        if (phase == "none") {
            ProfilePhotoPreviewState.None
        } else {
            val kind = (values.getOrNull(1) as? String)
                ?.let { runCatching { ProfilePhotoActionKind.valueOf(it) }.getOrNull() }
            val position = values.getOrNull(2) as? Int
            val uriString = values.getOrNull(4) as? String
            val generation = values.getOrNull(5) as? String
            val cropConfirmedAt = values.getOrNull(6) as? Long
            if (kind == null || position == null || uriString == null || generation == null || cropConfirmedAt == null) {
                ProfilePhotoPreviewState.None
            } else {
                val preview = PendingProfilePhotoPreview(
                    action = ProfilePhotoActionPresentation(
                        kind = kind,
                        position = position,
                        photoId = values.getOrNull(3) as? String,
                    ),
                    uriString = uriString,
                    generation = generation,
                    cropConfirmedAtElapsedMillis = cropConfirmedAt,
                    oldCanonicalCacheKey = values.getOrNull(7) as? String,
                )
                if (phase == "awaiting") {
                    val remotePhotoId = values.getOrNull(8) as? String
                    val remoteUrl = values.getOrNull(9) as? String
                    val responseAt = values.getOrNull(10) as? Long
                    if (remotePhotoId != null && remoteUrl != null && responseAt != null) {
                        ProfilePhotoPreviewState.AwaitingRemote(preview, remotePhotoId, remoteUrl, responseAt)
                    } else {
                        ProfilePhotoPreviewState.None
                    }
                } else {
                    ProfilePhotoPreviewState.Uploading(preview)
                }
            }
        }
    },
)
