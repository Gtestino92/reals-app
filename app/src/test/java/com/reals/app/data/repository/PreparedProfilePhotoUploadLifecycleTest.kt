package com.reals.app.data.repository

import com.reals.app.core.media.PreparedProfilePhotoUpload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class PreparedProfilePhotoUploadLifecycleTest {
    @Test
    fun preparedFileIsDeletedAfterSuccess() = runTest {
        val prepared = preparedUpload(writeTempJpeg("success"))

        prepared.useDeletingFile { "ok" }

        assertFalse(prepared.file.exists())
    }

    @Test
    fun preparedFileIsDeletedAfterFailure() = runTest {
        val prepared = preparedUpload(writeTempJpeg("failure"))

        runCatching {
            prepared.useDeletingFile { throw IOException("server failed") }
        }

        assertFalse(prepared.file.exists())
    }

    @Test
    fun preparedFileIsDeletedAfterCancellation() = runTest {
        val prepared = preparedUpload(writeTempJpeg("cancel"))
        val started = CompletableDeferred<Unit>()

        val job = launch {
            prepared.useDeletingFile {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        assertTrue(prepared.file.exists())

        job.cancel()
        job.join()

        assertFalse(prepared.file.exists())
    }

    private fun preparedUpload(file: File): PreparedProfilePhotoUpload =
        PreparedProfilePhotoUpload(
            file = file,
            mimeType = "image/jpeg",
            filename = file.name,
            width = 100,
            height = 100,
            fileSizeBytes = file.length(),
        )

    private fun writeTempJpeg(prefix: String): File =
        File.createTempFile("prepared-$prefix", ".jpg").apply {
            writeText("jpeg")
            deleteOnExit()
        }
}
