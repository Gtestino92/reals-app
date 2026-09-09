package com.reals.app.data.repository

import com.reals.app.core.media.PreparedProfilePhotoUpload
import com.reals.app.core.media.PreparedUploadFileOwnership

internal suspend fun <T> PreparedProfilePhotoUpload.useDeletingFile(
    block: suspend (PreparedProfilePhotoUpload) -> T,
): T =
    try {
        block(this)
    } finally {
        if (fileOwnership == PreparedUploadFileOwnership.RepositoryOwned) {
            file.delete()
        }
    }
