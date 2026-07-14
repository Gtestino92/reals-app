package com.reals.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoCropGeometryTest {
    @Test
    fun portraitSourceCentersIntoFourByFiveViewport() {
        val transform = centeredCropTransform(1200, 1800, 400f, 500f)

        assertEquals(400f / 1200f, transform.scale, 0.0001f)
        assertEquals(0f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
    }

    @Test
    fun landscapeSourceCentersIntoFourByFiveViewport() {
        val transform = centeredCropTransform(2000, 1000, 400f, 500f)

        assertEquals(500f / 1000f, transform.scale, 0.0001f)
        assertEquals(800, transform.sourceCropRect().width)
        assertEquals(1000, transform.sourceCropRect().height)
    }

    @Test
    fun minimumScaleFullyCoversViewport() {
        val transform = centeredCropTransform(3000, 2000, 400f, 500f)

        assertTrue(transform.sourceWidth * transform.scale >= transform.viewportWidth)
        assertTrue(transform.sourceHeight * transform.scale >= transform.viewportHeight)
    }

    @Test
    fun horizontalPanClampsAtBothLimits() {
        val transform = centeredCropTransform(2000, 1000, 400f, 500f)

        assertEquals(transform.maxOffsetX, transform.panBy(10_000f, 0f).offsetX, 0.0001f)
        assertEquals(-transform.maxOffsetX, transform.panBy(-10_000f, 0f).offsetX, 0.0001f)
    }

    @Test
    fun verticalPanClampsAtBothLimits() {
        val transform = centeredCropTransform(1000, 2000, 400f, 500f)

        assertEquals(transform.maxOffsetY, transform.panBy(0f, 10_000f).offsetY, 0.0001f)
        assertEquals(-transform.maxOffsetY, transform.panBy(0f, -10_000f).offsetY, 0.0001f)
    }

    @Test
    fun zoomRecalculatesValidOffsetRanges() {
        val transform = centeredCropTransform(1000, 1000, 400f, 500f)
        val zoomed = transform.zoomBy(2f)

        assertTrue(zoomed.maxOffsetX > transform.maxOffsetX)
        assertTrue(zoomed.maxOffsetY > transform.maxOffsetY)
    }

    @Test
    fun resetReturnsCenteredMinimumFillState() {
        val transform = centeredCropTransform(2000, 1000, 400f, 500f)
            .zoomBy(2f)
            .panBy(100f, -75f)
            .reset()

        assertEquals(transform.minScale, transform.scale, 0.0001f)
        assertEquals(0f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
    }

    @Test
    fun generatedSourceRectStaysWithinBitmapBounds() {
        val rect = centeredCropTransform(2000, 1000, 400f, 500f)
            .zoomBy(3f)
            .panBy(10_000f, -10_000f)
            .sourceCropRect()

        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= 2000)
        assertTrue(rect.bottom <= 1000)
    }

    @Test
    fun generatedSourceRectRetainsFourByFiveRatio() {
        val rect = centeredCropTransform(2000, 1000, 400f, 500f).sourceCropRect()

        assertEquals(ProfilePhotoPresentationAspectRatio, rect.width.toFloat() / rect.height, 0.002f)
    }

    @Test
    fun centeredTransformGeneratesCenteredSourceCrop() {
        val rect = centeredCropTransform(2000, 1000, 400f, 500f).sourceCropRect()

        assertEquals(600, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1400, rect.right)
        assertEquals(1000, rect.bottom)
    }

    @Test
    fun pannedTransformMovesSourceCropInCorrectDirection() {
        val centered = centeredCropTransform(2000, 1000, 400f, 500f)
        val movedRight = centered.panBy(100f, 0f).sourceCropRect()

        assertTrue(movedRight.left < centered.sourceCropRect().left)
    }

    @Test
    fun viewportResizeReclampsOffsets() {
        val transform = centeredCropTransform(2000, 1000, 400f, 500f).panBy(10_000f, 0f)
        val resized = transform.resized(1000f, 500f)

        assertTrue(resized.offsetX <= resized.maxOffsetX)
        assertTrue(resized.offsetX >= -resized.maxOffsetX)
    }

    @Test
    fun maximumZoomIsEnforced() {
        val transform = centeredCropTransform(1000, 1000, 400f, 500f)
        val zoomed = transform.zoomBy(100f)

        assertEquals(transform.maxScale, zoomed.scale, 0.0001f)
    }
}
