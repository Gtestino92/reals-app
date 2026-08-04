package com.reals.app.ui.profile

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.reals.app.domain.model.ProfilePhoto
import java.util.UUID

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

    data class Orphaned(
        val uriString: String,
    ) : ProfilePhotoPreviewState
}

internal object ProfilePhotoPreviewProcessSession {
    val id: String = UUID.randomUUID().toString()
}

internal data class ProfilePhotoPreviewMutationResult(
    val state: ProfilePhotoPreviewState,
    val cleanupUriString: String? = null,
)

internal val ProfilePhotoPreviewState.preview: PendingProfilePhotoPreview?
    get() = when (this) {
        ProfilePhotoPreviewState.None -> null
        is ProfilePhotoPreviewState.Uploading -> preview
        is ProfilePhotoPreviewState.AwaitingRemote -> preview
        is ProfilePhotoPreviewState.Orphaned -> null
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

internal fun ProfilePhotoPreviewState.clearForNewPhotoMutation(): ProfilePhotoPreviewMutationResult =
    when (this) {
        ProfilePhotoPreviewState.None -> ProfilePhotoPreviewMutationResult(ProfilePhotoPreviewState.None)
        is ProfilePhotoPreviewState.Uploading -> ProfilePhotoPreviewMutationResult(
            state = ProfilePhotoPreviewState.None,
            cleanupUriString = preview.uriString,
        )
        is ProfilePhotoPreviewState.AwaitingRemote -> ProfilePhotoPreviewMutationResult(
            state = ProfilePhotoPreviewState.None,
            cleanupUriString = preview.uriString,
        )
        is ProfilePhotoPreviewState.Orphaned -> ProfilePhotoPreviewMutationResult(
            state = ProfilePhotoPreviewState.None,
            cleanupUriString = uriString,
        )
    }

internal fun ProfilePhotoPreviewState.onMatchingUploadFailed(
    action: ProfilePhotoActionPresentation?,
): ProfilePhotoPreviewMutationResult =
    when (this) {
        is ProfilePhotoPreviewState.Uploading ->
            if (preview.action.matches(action)) {
                ProfilePhotoPreviewMutationResult(ProfilePhotoPreviewState.None, preview.uriString)
            } else {
                ProfilePhotoPreviewMutationResult(this)
            }
        else -> ProfilePhotoPreviewMutationResult(this)
    }

internal fun ProfilePhotoPreviewState.onMatchingUploadSucceeded(
    action: ProfilePhotoActionPresentation?,
    photos: List<ProfilePhoto>,
    uploadResponseAtElapsedMillis: Long,
): ProfilePhotoPreviewMutationResult =
    when (this) {
        is ProfilePhotoPreviewState.Uploading ->
            if (preview.action.matches(action)) {
                val remotePhoto = matchingRemotePhoto(photos)
                if (remotePhoto == null) {
                    ProfilePhotoPreviewMutationResult(ProfilePhotoPreviewState.None, preview.uriString)
                } else {
                    ProfilePhotoPreviewMutationResult(
                        ProfilePhotoPreviewState.AwaitingRemote(
                            preview = preview,
                            remotePhotoId = remotePhoto.id,
                            remoteUrl = remotePhoto.url,
                            uploadResponseAtElapsedMillis = uploadResponseAtElapsedMillis,
                        ),
                    )
                }
            } else {
                ProfilePhotoPreviewMutationResult(this)
            }
        else -> ProfilePhotoPreviewMutationResult(this)
    }

internal fun ProfilePhotoPreviewState.onMatchingRemoteSucceeded(
    remotePhotoId: String,
    generation: String,
): ProfilePhotoPreviewMutationResult =
    when (this) {
        is ProfilePhotoPreviewState.AwaitingRemote ->
            if (preview.generation == generation && this.remotePhotoId == remotePhotoId) {
                ProfilePhotoPreviewMutationResult(ProfilePhotoPreviewState.None, preview.uriString)
            } else {
                ProfilePhotoPreviewMutationResult(this)
            }
        else -> ProfilePhotoPreviewMutationResult(this)
    }

internal fun ProfilePhotoPreviewState.previewForPosition(position: Int): PendingProfilePhotoPreview? =
    preview?.takeIf { it.action.targetsPosition(position) }

internal fun ProfilePhotoPreviewState.awaitingRemoteForPhoto(
    photoId: String,
): ProfilePhotoPreviewState.AwaitingRemote? =
    (this as? ProfilePhotoPreviewState.AwaitingRemote)
        ?.takeIf { it.remotePhotoId == photoId }

internal fun saveProfilePhotoPreviewState(
    state: ProfilePhotoPreviewState,
    processSessionId: String = ProfilePhotoPreviewProcessSession.id,
): List<Any?> =
    when (state) {
        ProfilePhotoPreviewState.None -> listOf(PhaseNone)
        is ProfilePhotoPreviewState.Uploading -> listOf(
            PhaseUploading,
            processSessionId,
            state.preview.action.kind.name,
            state.preview.action.position,
            state.preview.action.photoId,
            state.preview.uriString,
            state.preview.generation,
            state.preview.cropConfirmedAtElapsedMillis,
            state.preview.oldCanonicalCacheKey,
        )
        is ProfilePhotoPreviewState.AwaitingRemote -> listOf(
            PhaseAwaiting,
            processSessionId,
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
        is ProfilePhotoPreviewState.Orphaned -> listOf(PhaseOrphaned, state.uriString)
    }

internal fun restoreProfilePhotoPreviewState(
    values: List<Any?>,
    currentProcessSessionId: String = ProfilePhotoPreviewProcessSession.id,
): ProfilePhotoPreviewState =
    when (values.getOrNull(0) as? String) {
        PhaseNone -> ProfilePhotoPreviewState.None
        PhaseUploading -> restoreSavedPreview(values, currentProcessSessionId)?.let { (preview, sameProcess) ->
            if (sameProcess) ProfilePhotoPreviewState.Uploading(preview) else ProfilePhotoPreviewState.Orphaned(preview.uriString)
        } ?: ProfilePhotoPreviewState.None
        PhaseAwaiting -> restoreSavedPreview(values, currentProcessSessionId)?.let { (preview, sameProcess) ->
            val remotePhotoId = values.getOrNull(9) as? String
            val remoteUrl = values.getOrNull(10) as? String
            val responseAt = values.getOrNull(11) as? Long
            if (remotePhotoId == null || remoteUrl == null || responseAt == null) {
                ProfilePhotoPreviewState.None
            } else if (!sameProcess) {
                ProfilePhotoPreviewState.Orphaned(preview.uriString)
            } else {
                ProfilePhotoPreviewState.AwaitingRemote(preview, remotePhotoId, remoteUrl, responseAt)
            }
        } ?: ProfilePhotoPreviewState.None
        PhaseOrphaned -> (values.getOrNull(1) as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let(ProfilePhotoPreviewState::Orphaned)
            ?: ProfilePhotoPreviewState.None
        else -> ProfilePhotoPreviewState.None
    }

internal val ProfilePhotoPreviewStateSaver: Saver<ProfilePhotoPreviewState, Any> = listSaver(
    save = { state -> saveProfilePhotoPreviewState(state) },
    restore = { values -> restoreProfilePhotoPreviewState(values) },
)

private data class RestoredPreview(
    val preview: PendingProfilePhotoPreview,
    val sameProcess: Boolean,
)

private fun restoreSavedPreview(
    values: List<Any?>,
    currentProcessSessionId: String,
): RestoredPreview? {
    val savedProcessSessionId = values.getOrNull(1) as? String ?: return null
    val kind = (values.getOrNull(2) as? String)
        ?.let { runCatching { ProfilePhotoActionKind.valueOf(it) }.getOrNull() }
        ?: return null
    val position = values.getOrNull(3) as? Int ?: return null
    val uriString = (values.getOrNull(5) as? String)?.takeIf { it.isNotBlank() } ?: return null
    val generation = (values.getOrNull(6) as? String)?.takeIf { it.isNotBlank() } ?: return null
    val cropConfirmedAt = values.getOrNull(7) as? Long ?: return null
    return RestoredPreview(
        preview = PendingProfilePhotoPreview(
            action = ProfilePhotoActionPresentation(
                kind = kind,
                position = position,
                photoId = values.getOrNull(4) as? String,
            ),
            uriString = uriString,
            generation = generation,
            cropConfirmedAtElapsedMillis = cropConfirmedAt,
            oldCanonicalCacheKey = values.getOrNull(8) as? String,
        ),
        sameProcess = savedProcessSessionId == currentProcessSessionId,
    )
}

private fun ProfilePhotoPreviewState.Uploading.matchingRemotePhoto(
    photos: List<ProfilePhoto>,
): ProfilePhoto? =
    when (preview.action.kind) {
        ProfilePhotoActionKind.Add -> photos.firstOrNull { it.position == preview.action.position }
        ProfilePhotoActionKind.Replace -> {
            val photoId = preview.action.photoId
            if (photoId != null) {
                photos.firstOrNull { it.id == photoId }
            } else {
                photos.firstOrNull { it.position == preview.action.position }
            }
        }
        ProfilePhotoActionKind.Delete -> null
    }

private const val PhaseNone = "none"
private const val PhaseUploading = "uploading"
private const val PhaseAwaiting = "awaiting"
private const val PhaseOrphaned = "orphaned"
