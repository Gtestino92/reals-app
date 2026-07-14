package com.reals.app.data.repository

import android.webkit.MimeTypeMap

internal object ProfileUploadFileMetadata {
    const val DefaultProfilePhotoFilename: String = "profile-photo.jpg"
    const val DefaultMimeType: String = "application/octet-stream"

    fun contentType(resolverMimeType: String?, filename: String?): String {
        resolverMimeType?.takeIf { it.isSafeImageMimeType() }?.let { return it }
        return filename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.let { extension ->
                imageMimeTypeFromExtension(extension)
            }
            ?.takeIf { it.isSafeImageMimeType() }
            ?: DefaultMimeType
    }

    fun displayName(queryDisplayName: String?, lastPathSegment: String?): String =
        queryDisplayName?.takeIf { it.isValidFilename() }
            ?: lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.takeIf { it.isValidFilename() }
            ?: DefaultProfilePhotoFilename

    private fun String.isSafeImageMimeType(): Boolean =
        startsWith("image/") && !contains('\n') && !contains('\r')

    private fun String.isValidFilename(): Boolean =
        isNotBlank() && !contains('/') && !contains('\\')

    private fun imageMimeTypeFromExtension(extension: String): String? =
        when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> runCatching {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }.getOrNull()
        }
}
