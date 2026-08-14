package com.reals.app.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.reals.app.domain.model.ProfilePhoto

internal sealed interface ProfilePhotoSelectionTransaction {
    val target: ProfilePhotoSelectionTarget

    data class AwaitingPicker(
        override val target: ProfilePhotoSelectionTarget,
    ) : ProfilePhotoSelectionTransaction

    data class Cropping(
        override val target: ProfilePhotoSelectionTarget,
        val sourceUriString: String,
    ) : ProfilePhotoSelectionTransaction
}

internal data class ProfilePhotoInteractionTiming(
    val phase: String,
    val durationMs: Long,
)

internal data class ProfilePhotoCropConfirmation(
    val target: ProfilePhotoSelectionTarget,
    val cleanupUriString: String?,
)

internal data class ProfilePhotoUploadFailureTransition(
    val cleanupUriString: String?,
    val timing: ProfilePhotoInteractionTiming,
)

internal sealed interface ProfilePhotoUploadSuccessTransition {
    val timing: ProfilePhotoInteractionTiming

    data class Applied(
        val cleanupUriString: String?,
        override val timing: ProfilePhotoInteractionTiming,
    ) : ProfilePhotoUploadSuccessTransition

    data class AwaitingRemote(
        val prepared: PreparedProfilePhotoUploadSuccess,
        override val timing: ProfilePhotoInteractionTiming,
    ) : ProfilePhotoUploadSuccessTransition
}

internal data class PreparedProfilePhotoUploadSuccess(
    val transition: ProfilePhotoPreviewMutationResult,
    val expectedGeneration: String,
    val expectedAction: ProfilePhotoActionPresentation,
    val cacheDecision: ProfilePhotoCacheRefreshDecision,
)

internal data class ProfilePhotoRemoteHandoffTransition(
    val cleanupUriString: String?,
    val timing: ProfilePhotoInteractionTiming,
)

internal class ProfilePhotoInteractionState(
    localError: String? = null,
    selectionTransaction: ProfilePhotoSelectionTransaction? = null,
    activeAction: ProfilePhotoActionPresentation? = null,
    previewGenerationCounter: Int = 0,
    previewState: ProfilePhotoPreviewState = ProfilePhotoPreviewState.None,
) {
    var localError by mutableStateOf(localError)
        private set

    var selectionTransaction by mutableStateOf(selectionTransaction)
        private set

    var activeAction by mutableStateOf(activeAction)
        private set

    var previewGenerationCounter by mutableIntStateOf(previewGenerationCounter)
        private set

    var previewState by mutableStateOf(previewState)
        private set

    val cropTransaction: ProfilePhotoSelectionTransaction.Cropping?
        get() = selectionTransaction as? ProfilePhotoSelectionTransaction.Cropping

    fun visibleAction(photoActionLoading: Boolean): ProfilePhotoActionPresentation? =
        activeAction.takeIf { photoActionLoading }

    fun startAddSelection(position: Int) {
        localError = null
        selectionTransaction = ProfilePhotoSelectionTransaction.AwaitingPicker(
            ProfilePhotoSelectionTarget.Add(position),
        )
    }

    fun startReplacementSelection(photoId: String, position: Int) {
        localError = null
        selectionTransaction = ProfilePhotoSelectionTransaction.AwaitingPicker(
            ProfilePhotoSelectionTarget.Replace(photoId = photoId, position = position),
        )
    }

    fun onPickerResult(uriString: String?) {
        val awaitingPicker = selectionTransaction as? ProfilePhotoSelectionTransaction.AwaitingPicker
        if (uriString != null && awaitingPicker != null) {
            localError = null
            selectionTransaction = ProfilePhotoSelectionTransaction.Cropping(
                target = awaitingPicker.target,
                sourceUriString = uriString,
            )
        } else {
            selectionTransaction = null
        }
    }

    fun cancelCrop() {
        selectionTransaction = null
    }

    fun confirmCrop(
        croppedUriString: String,
        photos: List<ProfilePhoto>,
        cropConfirmedAtElapsedMillis: Long,
    ): ProfilePhotoCropConfirmation? {
        val crop = cropTransaction ?: return null
        val action = crop.target.toProfilePhotoActionPresentation()
        val cleanupUriString = clearLocalPreview()
        previewGenerationCounter += 1
        previewState = startProfilePhotoPreview(
            action = action,
            uriString = croppedUriString,
            generation = "local-$previewGenerationCounter",
            cropConfirmedAtElapsedMillis = cropConfirmedAtElapsedMillis,
            oldCanonicalCacheKey = crop.target.oldCanonicalCacheKey(photos),
        )
        activeAction = action
        selectionTransaction = null
        return ProfilePhotoCropConfirmation(
            target = crop.target,
            cleanupUriString = cleanupUriString,
        )
    }

    fun startDelete(photoId: String, position: Int): String? {
        val cleanupUriString = clearLocalPreview()
        localError = null
        activeAction = ProfilePhotoActionPresentation(
            kind = ProfilePhotoActionKind.Delete,
            position = position,
            photoId = photoId,
        )
        return cleanupUriString
    }

    fun startMove(): String? = clearLocalPreview()

    fun onMatchingUploadFailed(
        responseAtElapsedMillis: Long,
    ): ProfilePhotoUploadFailureTransition? {
        val uploading = previewState as? ProfilePhotoPreviewState.Uploading ?: return null
        val action = activeAction
        if (!uploading.preview.action.matches(action)) return null
        val cleanupUriString = applyPreviewMutation(previewState.onMatchingUploadFailed(action))
        return ProfilePhotoUploadFailureTransition(
            cleanupUriString = cleanupUriString,
            timing = uploading.preview.cropToResponseTiming(responseAtElapsedMillis),
        )
    }

    fun prepareMatchingUploadSucceeded(
        photos: List<ProfilePhoto>,
        uploadResponseAtElapsedMillis: Long,
    ): ProfilePhotoUploadSuccessTransition? {
        val uploading = previewState as? ProfilePhotoPreviewState.Uploading ?: return null
        val action = activeAction
        if (!uploading.preview.action.matches(action)) return null
        val transition = previewState.onMatchingUploadSucceeded(
            action = action,
            photos = photos,
            uploadResponseAtElapsedMillis = uploadResponseAtElapsedMillis,
        )
        val timing = uploading.preview.cropToResponseTiming(uploadResponseAtElapsedMillis)
        val awaiting = transition.state as? ProfilePhotoPreviewState.AwaitingRemote ?: return ProfilePhotoUploadSuccessTransition.Applied(
            cleanupUriString = applyPreviewMutation(transition),
            timing = timing,
        )
        return ProfilePhotoUploadSuccessTransition.AwaitingRemote(
            prepared = PreparedProfilePhotoUploadSuccess(
                transition = transition,
                expectedGeneration = uploading.preview.generation,
                expectedAction = uploading.preview.action,
                cacheDecision = profilePhotoReplacementCacheRefreshDecision(
                    action = awaiting.preview.action,
                    oldCanonicalCacheKey = awaiting.preview.oldCanonicalCacheKey,
                    newUrl = awaiting.remoteUrl,
                ),
            ),
            timing = timing,
        )
    }

    fun commitPreparedUploadSuccess(prepared: PreparedProfilePhotoUploadSuccess): String? {
        val currentUploading = previewState as? ProfilePhotoPreviewState.Uploading
        return if (
            currentUploading?.preview?.generation == prepared.expectedGeneration &&
            currentUploading.preview.action.matches(prepared.expectedAction)
        ) {
            applyPreviewMutation(prepared.transition)
        } else {
            null
        }
    }

    fun onRemotePreviewDisplayed(
        remotePhotoId: String,
        generation: String,
        displayedAtElapsedMillis: Long,
    ): ProfilePhotoRemoteHandoffTransition? {
        val awaiting = previewState as? ProfilePhotoPreviewState.AwaitingRemote ?: return null
        if (awaiting.preview.generation != generation || awaiting.remotePhotoId != remotePhotoId) return null
        return ProfilePhotoRemoteHandoffTransition(
            cleanupUriString = applyPreviewMutation(
                previewState.onMatchingRemoteSucceeded(remotePhotoId, generation),
            ),
            timing = ProfilePhotoInteractionTiming(
                phase = "remote_handoff",
                durationMs = displayedAtElapsedMillis - awaiting.uploadResponseAtElapsedMillis,
            ),
        )
    }

    fun clearOrphanedPreview(): String? =
        if (previewState is ProfilePhotoPreviewState.Orphaned) {
            clearLocalPreview()
        } else {
            null
        }

    fun clearCompletedActionIfTerminal(
        photoActionLoading: Boolean,
        photoActionMessage: String?,
        photoActionErrorPresent: Boolean,
    ) {
        if (!photoActionLoading && (photoActionMessage != null || photoActionErrorPresent)) {
            selectionTransaction = null
            activeAction = null
        }
    }

    private fun clearLocalPreview(): String? =
        applyPreviewMutation(previewState.clearForNewPhotoMutation())

    private fun applyPreviewMutation(result: ProfilePhotoPreviewMutationResult): String? {
        previewState = result.state
        return result.cleanupUriString
    }
}

@Composable
internal fun rememberProfilePhotoInteractionState(profileId: String): ProfilePhotoInteractionState =
    rememberSaveable(profileId, saver = ProfilePhotoInteractionStateSaver) {
        ProfilePhotoInteractionState()
    }

internal fun saveProfilePhotoInteractionState(state: ProfilePhotoInteractionState): List<Any?> =
    listOf(
        state.localError,
        state.selectionTransaction?.phaseName(),
        state.selectionTransaction?.target?.savedKind(),
        state.selectionTransaction?.target?.position,
        (state.selectionTransaction?.target as? ProfilePhotoSelectionTarget.Replace)?.photoId,
        (state.selectionTransaction as? ProfilePhotoSelectionTransaction.Cropping)?.sourceUriString,
        state.activeAction?.kind?.name,
        state.activeAction?.position,
        state.activeAction?.photoId,
        state.previewGenerationCounter,
    ) + saveProfilePhotoPreviewState(state.previewState)

internal fun restoreProfilePhotoInteractionState(values: List<Any?>): ProfilePhotoInteractionState =
    ProfilePhotoInteractionState(
        localError = values.getOrNull(0) as? String,
        selectionTransaction = restoreSelectionTransaction(values),
        activeAction = profilePhotoActionPresentation(
            kindName = values.getOrNull(6) as? String,
            position = values.getOrNull(7) as? Int,
            photoId = values.getOrNull(8) as? String,
        ),
        previewGenerationCounter = values.getOrNull(9) as? Int ?: 0,
        previewState = restoreProfilePhotoPreviewState(values.drop(10)),
    )

internal val ProfilePhotoInteractionStateSaver: Saver<ProfilePhotoInteractionState, Any> = listSaver(
    save = { state -> saveProfilePhotoInteractionState(state) },
    restore = { values -> restoreProfilePhotoInteractionState(values) },
)

private fun ProfilePhotoSelectionTransaction.phaseName(): String =
    when (this) {
        is ProfilePhotoSelectionTransaction.AwaitingPicker -> SelectionPhasePicker
        is ProfilePhotoSelectionTransaction.Cropping -> SelectionPhaseCrop
    }

private fun restoreSelectionTransaction(values: List<Any?>): ProfilePhotoSelectionTransaction? {
    val target = profilePhotoSelectionTarget(
        kind = values.getOrNull(2) as? String,
        position = values.getOrNull(3) as? Int,
        photoId = values.getOrNull(4) as? String,
    ) ?: return null
    return when (values.getOrNull(1) as? String) {
        SelectionPhasePicker -> ProfilePhotoSelectionTransaction.AwaitingPicker(target)
        SelectionPhaseCrop -> {
            val sourceUriString = (values.getOrNull(5) as? String)?.takeIf { it.isNotBlank() } ?: return null
            ProfilePhotoSelectionTransaction.Cropping(target, sourceUriString)
        }
        else -> null
    }
}

private fun ProfilePhotoSelectionTarget.oldCanonicalCacheKey(
    photos: List<ProfilePhoto>,
): String? =
    when (this) {
        is ProfilePhotoSelectionTarget.Add -> null
        is ProfilePhotoSelectionTarget.Replace -> photos
            .firstOrNull { it.id == photoId || it.position == position }
            ?.url
            ?.stableProfilePhotoCacheKey()
    }

private fun PendingProfilePhotoPreview.cropToResponseTiming(responseAtElapsedMillis: Long): ProfilePhotoInteractionTiming =
    ProfilePhotoInteractionTiming(
        phase = "crop_confirm_to_upload_response",
        durationMs = responseAtElapsedMillis - cropConfirmedAtElapsedMillis,
    )

private const val SelectionPhasePicker = "picker"
private const val SelectionPhaseCrop = "crop"
