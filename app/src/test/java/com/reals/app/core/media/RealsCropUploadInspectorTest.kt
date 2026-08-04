package com.reals.app.core.media

import com.reals.app.ui.profile.ProfilePhotoOutputHeightPx
import com.reals.app.ui.profile.ProfilePhotoOutputWidthPx
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RealsCropUploadInspectorTest {
    @Test
    fun canonicalCropCacheChildIsAccepted() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.jpg").apply { writeText("jpeg") }
        val inspector = inspector(cacheDir)

        val result = inspector.inspect("file", file.absolutePath)

        assertTrue(result is RealsCropInspection.Trusted)
    }

    @Test
    fun pathPrefixCollisionIsRejected() {
        val parent = Files.createTempDirectory("cache-root").toFile()
        val cacheDir = File(parent, "profile-photo-crops").apply { mkdirs() }
        val sibling = File(parent, "profile-photo-crops-sibling").apply { mkdirs() }
        val file = File(sibling, "crop.jpg").apply { writeText("jpeg") }

        assertNotTrusted(cacheDir, file)
    }

    @Test
    fun parentTraversalEscapeIsRejected() {
        val parent = Files.createTempDirectory("cache-root").toFile()
        val cacheDir = File(parent, "profile-photo-crops").apply { mkdirs() }
        File(parent, "external.jpg").writeText("jpeg")

        assertNotTrusted(cacheDir, File(cacheDir, "../external.jpg"))
    }

    @Test
    fun symlinkEscapeIsRejectedWhereSupported() {
        val parent = Files.createTempDirectory("cache-root").toFile()
        val cacheDir = File(parent, "profile-photo-crops").apply { mkdirs() }
        val external = File(parent, "external.jpg").apply { writeText("jpeg") }
        val link = File(cacheDir, "link.jpg")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), external.toPath()) }.isSuccess
        assumeTrue(created)

        assertNotTrusted(cacheDir, link)
    }

    @Test
    fun nonFileUriIsRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.jpg").apply { writeText("jpeg") }
        val result = inspector(cacheDir).inspect("content", file.absolutePath)

        assertTrue(result is RealsCropInspection.NotTrusted)
    }

    @Test
    fun nonexistentFileIsRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()

        assertNotTrusted(cacheDir, File(cacheDir, "missing.jpg"))
    }

    @Test
    fun directoryIsRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val directory = File(cacheDir, "directory").apply { mkdirs() }

        assertNotTrusted(cacheDir, directory)
    }

    @Test
    fun emptyFileIsRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "empty.jpg").apply { writeBytes(ByteArray(0)) }

        assertNotTrusted(cacheDir, file)
    }

    @Test
    fun wrongMimeIsRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.png").apply { writeText("png") }

        assertNotTrusted(cacheDir, file, mimeType = "image/png")
    }

    @Test
    fun wrongDimensionsAreRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.jpg").apply { writeText("jpeg") }

        assertNotTrusted(cacheDir, file, width = 100, height = 100)
    }

    @Test
    fun oversizedFileIsRejected() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.jpg").apply { writeText("jpeg") }

        assertNotTrusted(cacheDir, file, maxFileSizeBytes = file.length() - 1)
    }

    private fun assertNotTrusted(
        cacheDir: File,
        file: File,
        width: Int = ProfilePhotoOutputWidthPx,
        height: Int = ProfilePhotoOutputHeightPx,
        mimeType: String = PreparedUploadMimeType,
        maxFileSizeBytes: Long = MaxPreparedUploadFileSizeBytes,
    ) {
        val result = inspector(cacheDir, width, height, mimeType, maxFileSizeBytes).inspect("file", file.path)
        assertTrue(result is RealsCropInspection.NotTrusted)
    }

    private fun inspector(
        cacheDir: File,
        width: Int = ProfilePhotoOutputWidthPx,
        height: Int = ProfilePhotoOutputHeightPx,
        mimeType: String = PreparedUploadMimeType,
        maxFileSizeBytes: Long = MaxPreparedUploadFileSizeBytes,
    ): DefaultRealsCropUploadInspector =
        DefaultRealsCropUploadInspector(
            cropCacheDir = cacheDir,
            metadataReader = EncodedImageMetadataReader {
                EncodedImageMetadata(width = width, height = height, mimeType = mimeType)
            },
            maxFileSizeBytes = maxFileSizeBytes,
        )
}
