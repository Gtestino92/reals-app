package com.reals.app.ui.profile

import android.net.Uri

internal sealed interface ProfilePhotoSelectionTarget {
    val position: Int

    data class Add(
        override val position: Int,
    ) : ProfilePhotoSelectionTarget

    data class Replace(
        val photoId: String,
        override val position: Int,
    ) : ProfilePhotoSelectionTarget
}

internal data class ProfilePhotoCropRequest(
    val sourceUri: Uri,
    val target: ProfilePhotoSelectionTarget,
)

private const val ProfilePhotoAddTargetKind = "add"
private const val ProfilePhotoReplaceTargetKind = "replace"

internal fun profilePhotoSelectionTarget(
    kind: String?,
    position: Int?,
    photoId: String?,
): ProfilePhotoSelectionTarget? =
    when (kind) {
        ProfilePhotoAddTargetKind ->
            position?.let(ProfilePhotoSelectionTarget::Add)

        ProfilePhotoReplaceTargetKind ->
            if (position != null && !photoId.isNullOrBlank()) {
                ProfilePhotoSelectionTarget.Replace(photoId = photoId, position = position)
            } else {
                null
            }

        else -> null
    }

internal fun ProfilePhotoSelectionTarget.savedKind(): String =
    when (this) {
        is ProfilePhotoSelectionTarget.Add -> ProfilePhotoAddTargetKind
        is ProfilePhotoSelectionTarget.Replace -> ProfilePhotoReplaceTargetKind
    }

internal fun dispatchCroppedProfilePhoto(
    target: ProfilePhotoSelectionTarget,
    croppedUri: Uri,
    onAddPhotoFile: (position: Int, fileUri: Uri) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, fileUri: Uri) -> Unit,
) {
    dispatchCroppedProfilePhotoValue(
        target = target,
        croppedValue = croppedUri,
        onAddPhotoFile = onAddPhotoFile,
        onReplacePhotoFile = onReplacePhotoFile,
    )
}

internal fun <T> dispatchCroppedProfilePhotoValue(
    target: ProfilePhotoSelectionTarget,
    croppedValue: T,
    onAddPhotoFile: (position: Int, croppedValue: T) -> Unit,
    onReplacePhotoFile: (photoId: String, position: Int, croppedValue: T) -> Unit,
) {
    when (target) {
        is ProfilePhotoSelectionTarget.Add ->
            onAddPhotoFile(target.position, croppedValue)

        is ProfilePhotoSelectionTarget.Replace ->
            onReplacePhotoFile(target.photoId, target.position, croppedValue)
    }
}
