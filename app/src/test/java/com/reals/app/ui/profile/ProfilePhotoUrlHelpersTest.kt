package com.reals.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoUrlHelpersTest {
    @Test
    fun `presigned localhost URL remains unchanged`() {
        val url = "http://127.0.0.1:9000/reals-profile-photos/photo.jpg?X-Amz-Signature=abc123"

        assertEquals(url, url.toEmulatorReachableUrl())
    }

    @Test
    fun `presigned localhost URL is renderable after display URL resolution`() {
        val url = "http://127.0.0.1:9000/reals-profile-photos/photo.jpg?X-Amz-Signature=abc123"
        val displayUrl = url.toEmulatorReachableUrl()

        assertEquals(url, displayUrl)
        assertTrue(displayUrl.isRenderableImageUrl())
    }

    @Test
    fun `normal HTTPS URL remains renderable`() {
        assertTrue("https://storage.example.com/photo.jpg".isRenderableImageUrl())
    }

    @Test
    fun `non HTTP value remains non renderable`() {
        assertFalse("profile-photos/photo.jpg".isRenderableImageUrl())
    }

    @Test
    fun `non presigned localhost URL keeps emulator rewrite behavior`() {
        val url = "http://localhost:9000/photo.jpg"

        assertEquals("http://10.0.2.2:9000/photo.jpg", url.toEmulatorReachableUrl())
    }
}
