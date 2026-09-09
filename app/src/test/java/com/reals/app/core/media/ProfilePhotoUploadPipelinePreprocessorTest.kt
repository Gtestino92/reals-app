package com.reals.app.core.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfilePhotoUploadPipelinePreprocessorTest {
    @Test
    fun trustedCropUsesByteCopyWithoutFallbackPreprocessing() = runTest {
        val source = File.createTempFile("trusted-crop", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val preparedDir = Files.createTempDirectory("prepared-profile-photo").toFile()
        val fallback = RecordingFallbackPreprocessor()
        val pipeline = ProfilePhotoUploadPipelinePreprocessor(
            fallbackPreprocessor = fallback,
            cropInspector = RealsCropUploadInspector {
                RealsCropInspection.Trusted(
                    file = source,
                    width = 1080,
                    height = 1350,
                    fileSizeBytes = source.length(),
                )
            },
            preparedCacheDir = preparedDir,
        )

        val prepared = pipeline.prepare(null).getOrThrow()

        assertFalse(fallback.called)
        assertTrue(prepared.usedTrustedCropFastPath)
        assertEquals(PreparedUploadFileOwnership.RepositoryOwned, prepared.fileOwnership)
        assertArrayEquals(source.readBytes(), prepared.file.readBytes())
        assertTrue(prepared.file.parentFile == preparedDir)
    }

    @Test
    fun untrustedSourceUsesFallbackPreprocessor() = runTest {
        val fallbackFile = File.createTempFile("fallback-prepared", ".jpg").apply { writeText("fallback") }
        val fallback = RecordingFallbackPreprocessor(preparedFile = fallbackFile)
        val pipeline = ProfilePhotoUploadPipelinePreprocessor(
            fallbackPreprocessor = fallback,
            cropInspector = RealsCropUploadInspector { RealsCropInspection.NotTrusted },
            preparedCacheDir = Files.createTempDirectory("prepared-profile-photo").toFile(),
        )

        val prepared = pipeline.prepare(null).getOrThrow()

        assertTrue(fallback.called)
        assertFalse(prepared.usedTrustedCropFastPath)
        assertEquals(fallbackFile, prepared.file)
    }

    @Test
    fun trustedCropCopyFailureDoesNotLeakIntermediateFile() = runTest {
        val source = File.createTempFile("trusted-crop", ".jpg").apply { writeText("jpeg") }
        val preparedDir = File.createTempFile("not-a-directory", ".tmp")
        val pipeline = ProfilePhotoUploadPipelinePreprocessor(
            fallbackPreprocessor = RecordingFallbackPreprocessor(),
            cropInspector = RealsCropUploadInspector {
                RealsCropInspection.Trusted(source, width = 1080, height = 1350, fileSizeBytes = source.length())
            },
            preparedCacheDir = preparedDir,
        )

        val failure = pipeline.prepare(null).exceptionOrNull()

        assertTrue(failure is ProfilePhotoPreprocessingException)
    }

    private class RecordingFallbackPreprocessor(
        private val preparedFile: File = File.createTempFile("fallback-prepared", ".jpg").apply { writeText("jpeg") },
    ) : ProfilePhotoUploadPreprocessor {
        var called = false

        override suspend fun prepare(sourceUri: android.net.Uri?): Result<PreparedProfilePhotoUpload> {
            called = true
            return Result.success(
                PreparedProfilePhotoUpload(
                    file = preparedFile,
                    mimeType = PreparedUploadMimeType,
                    filename = preparedFile.name,
                    width = 10,
                    height = 10,
                    fileSizeBytes = preparedFile.length(),
                ),
            )
        }
    }

}
