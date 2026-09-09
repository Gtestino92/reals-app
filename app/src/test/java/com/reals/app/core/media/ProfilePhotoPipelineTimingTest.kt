package com.reals.app.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoPipelineTimingTest {
    @Test
    fun formattedTimingContainsOnlyAllowedFields() {
        val formatted = ProfilePhotoTimingFields(
            phase = "upload_prepare",
            durationMs = 42,
            bytes = 183_422,
            fastPath = true,
        ).format()

        assertTrue(formatted.contains("phase=upload_prepare"))
        assertTrue(formatted.contains("durationMs=42"))
        assertTrue(formatted.contains("bytes=183422"))
        assertTrue(formatted.contains("fastPath=true"))
        assertFalse(formatted.contains("file://"))
        assertFalse(formatted.contains("https://"))
        assertFalse(formatted.contains(".jpg"))
        assertFalse(formatted.contains("token"))
        assertFalse(formatted.contains("user-"))
        assertFalse(formatted.contains("photo-"))
    }
}
