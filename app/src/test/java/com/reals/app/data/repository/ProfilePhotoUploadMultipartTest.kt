package com.reals.app.data.repository

import com.reals.app.core.media.PreparedProfilePhotoUpload
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfilePhotoUploadMultipartTest {
    @Test
    fun filePartUsesPreparedJpegContractAndReadsFromFileAtWriteTime() {
        val file = File.createTempFile("prepared-upload", ".jpg").apply {
            writeText("initial")
            deleteOnExit()
        }
        val prepared = preparedUpload(file, filename = "opaque-name.jpg")
        val part = ProfilePhotoUploadMultipart.filePart(prepared)

        file.writeText("streamed")

        assertTrue(part.headers.toString().contains("name=\"file\""))
        assertTrue(part.headers.toString().contains("filename=\"opaque-name.jpg\""))
        assertEquals("image/jpeg", part.body.contentType().toString())
        assertEquals("streamed", part.bodyText())
    }

    @Test
    fun positionPartKeepsExistingMultipartPositionContract() {
        val body = ProfilePhotoUploadMultipart.positionPart(4)

        assertTrue(body.contentType().toString().startsWith("text/plain"))
        assertEquals("4", body.bodyText())
    }

    private fun preparedUpload(file: File, filename: String): PreparedProfilePhotoUpload =
        PreparedProfilePhotoUpload(
            file = file,
            mimeType = "image/jpeg",
            filename = filename,
            width = 100,
            height = 100,
            fileSizeBytes = file.length(),
        )

    private fun okhttp3.MultipartBody.Part.bodyText(): String {
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun okhttp3.RequestBody.bodyText(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }
}
