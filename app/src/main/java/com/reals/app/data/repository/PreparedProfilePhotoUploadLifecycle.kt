package com.reals.app.data.repository

import com.reals.app.core.media.PreparedProfilePhotoUpload

internal suspend fun <T> PreparedProfilePhotoUpload.useDeletingFile(
    block: suspend (PreparedProfilePhotoUpload) -> T,
): T =
    try {
        block(this)
    } finally {
        file.delete()
    }
