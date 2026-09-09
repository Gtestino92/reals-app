package com.reals.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoPreprocessorSizingTest {
    @Test
    fun landscapeResizeKeepsBothDimensionsWithinLimit() {
        val resized = resizedDimensions(4096, 1024)

        assertEquals(2048, resized.width)
        assertEquals(512, resized.height)
    }

    @Test
    fun portraitResizeKeepsBothDimensionsWithinLimit() {
        val resized = resizedDimensions(1024, 4096)

        assertEquals(512, resized.width)
        assertEquals(2048, resized.height)
    }

    @Test
    fun smallerImageIsNotEnlargedBySizingMath() {
        val resized = resizedDimensions(800, 1000)

        assertEquals(800, resized.width)
        assertEquals(1000, resized.height)
    }

    @Test
    fun sampleSizeAvoidsFullResolutionDecodeWhenInputExceedsLimit() {
        assertEquals(2, calculateSampleSize(4096, 1024))
        assertTrue(calculateSampleSize(8000, 6000) > 1)
    }
}
