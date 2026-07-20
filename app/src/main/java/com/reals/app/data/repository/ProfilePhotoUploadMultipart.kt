package com.reals.app.data.repository

import com.reals.app.core.media.PreparedProfilePhotoUpload
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal object ProfilePhotoUploadMultipart {
    fun filePart(prepared: PreparedProfilePhotoUpload): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            name = "file",
            filename = prepared.filename,
            body = prepared.file.asRequestBody(prepared.mimeType.toMediaType()),
        )

    fun positionPart(position: Int): RequestBody =
        position.toString().toRequestBody("text/plain".toMediaType())
}
