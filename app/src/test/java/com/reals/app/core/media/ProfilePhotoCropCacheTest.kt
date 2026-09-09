package com.reals.app.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfilePhotoCropCacheTest {
    @Test
    fun fileInsideCropCacheIsOwned() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.jpg").apply { writeText("jpg") }

        assertTrue(isOwnedProfilePhotoCropFile(file, cacheDir))
    }

    @Test
    fun siblingDirectoryWithSimilarPrefixIsNotOwned() {
        val parent = Files.createTempDirectory("cache-root").toFile()
        val cacheDir = File(parent, "profile-photo-crops").apply { mkdirs() }
        val sibling = File(parent, "profile-photo-crops-sibling").apply { mkdirs() }
        val file = File(sibling, "crop.jpg").apply { writeText("jpg") }

        assertFalse(isOwnedProfilePhotoCropFile(file, cacheDir))
    }

    @Test
    fun parentTraversalCannotBeTreatedAsOwned() {
        val parent = Files.createTempDirectory("cache-root").toFile()
        val cacheDir = File(parent, "profile-photo-crops").apply { mkdirs() }
        val external = File(parent, "external.jpg").apply { writeText("jpg") }
        val traversal = File(cacheDir, "../external.jpg")

        assertTrue(external.exists())
        assertFalse(isOwnedProfilePhotoCropFile(traversal, cacheDir))
    }

    @Test
    fun contentUriIsNeverDeleted() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val file = File(cacheDir, "crop.jpg").apply { writeText("jpg") }

        assertFalse(
            isOwnedProfilePhotoCropFile(
                scheme = "content",
                path = file.absolutePath,
                cacheDir = cacheDir,
            ),
        )
    }

    @Test
    fun arbitraryExternalFileIsNeverDeleted() {
        val cacheDir = Files.createTempDirectory("profile-photo-crops").toFile()
        val external = Files.createTempFile("external-profile-photo", ".jpg").toFile()

        assertFalse(isOwnedProfilePhotoCropFile(external, cacheDir))
    }
}
