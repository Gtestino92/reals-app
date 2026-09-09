package com.reals.app.ui.profile

internal enum class ProfilePhotoActionKind {
    Add,
    Replace,
    Delete,
}

internal data class ProfilePhotoActionPresentation(
    val kind: ProfilePhotoActionKind,
    val position: Int,
    val photoId: String? = null,
)

internal fun ProfilePhotoSelectionTarget.toProfilePhotoActionPresentation(): ProfilePhotoActionPresentation =
    when (this) {
        is ProfilePhotoSelectionTarget.Add -> ProfilePhotoActionPresentation(
            kind = ProfilePhotoActionKind.Add,
            position = position,
        )

        is ProfilePhotoSelectionTarget.Replace -> ProfilePhotoActionPresentation(
            kind = ProfilePhotoActionKind.Replace,
            position = position,
            photoId = photoId,
        )
    }

internal fun profilePhotoActionPresentation(
    kindName: String?,
    position: Int?,
    photoId: String?,
): ProfilePhotoActionPresentation? {
    val kind = kindName?.let { runCatching { ProfilePhotoActionKind.valueOf(it) }.getOrNull() }
    return if (kind != null && position != null) {
        ProfilePhotoActionPresentation(kind = kind, position = position, photoId = photoId)
    } else {
        null
    }
}

internal fun ProfilePhotoActionPresentation?.progressTitle(): String =
    when (this?.kind) {
        ProfilePhotoActionKind.Add -> "Subiendo foto..."
        ProfilePhotoActionKind.Replace -> "Reemplazando foto..."
        ProfilePhotoActionKind.Delete -> "Eliminando foto..."
        null -> "Procesando foto..."
    }

internal fun ProfilePhotoActionPresentation?.progressMessage(): String =
    when (this?.kind) {
        ProfilePhotoActionKind.Add,
        ProfilePhotoActionKind.Replace -> "Estamos cargando la imagen. Puede tardar unos segundos."

        ProfilePhotoActionKind.Delete,
        null -> "Esta operación puede tardar unos segundos."
    }

internal fun ProfilePhotoActionPresentation?.slotStateDescription(): String =
    when (this?.kind) {
        ProfilePhotoActionKind.Add -> "Subiendo foto"
        ProfilePhotoActionKind.Replace -> "Reemplazando foto"
        ProfilePhotoActionKind.Delete -> "Eliminando foto"
        null -> "Procesando foto"
    }

internal fun ProfilePhotoActionPresentation?.targetsPosition(position: Int): Boolean =
    this?.position == position

internal fun ProfilePhotoActionPresentation.matches(other: ProfilePhotoActionPresentation?): Boolean =
    other != null &&
        kind == other.kind &&
        position == other.position &&
        photoId == other.photoId
