package com.reals.app.ui.profile

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val ProfilePhotoPresentationAspectRatio: Float = 4f / 5f
internal const val ProfilePhotoOutputWidthPx: Int = 1080
internal const val ProfilePhotoOutputHeightPx: Int = 1350

internal data class ProfilePhotoCropTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    val minScale: Float = minimumFillScale(sourceWidth, sourceHeight, viewportWidth, viewportHeight)
    val maxScale: Float = minScale * MaxCropZoomMultiplier
    val maxOffsetX: Float = maxOffset(sourceWidth, viewportWidth, scale)
    val maxOffsetY: Float = maxOffset(sourceHeight, viewportHeight, scale)

    fun clamped(): ProfilePhotoCropTransform {
        val clampedScale = scale.coerceIn(minScale, maxScale)
        val clampedMaxOffsetX = maxOffset(sourceWidth, viewportWidth, clampedScale)
        val clampedMaxOffsetY = maxOffset(sourceHeight, viewportHeight, clampedScale)
        return copy(
            scale = clampedScale,
            offsetX = offsetX.coerceIn(-clampedMaxOffsetX, clampedMaxOffsetX),
            offsetY = offsetY.coerceIn(-clampedMaxOffsetY, clampedMaxOffsetY),
        )
    }

    fun panBy(deltaX: Float, deltaY: Float): ProfilePhotoCropTransform =
        copy(offsetX = offsetX + deltaX, offsetY = offsetY + deltaY).clamped()

    fun zoomBy(multiplier: Float): ProfilePhotoCropTransform =
        copy(scale = scale * multiplier).clamped()

    fun resized(viewportWidth: Float, viewportHeight: Float): ProfilePhotoCropTransform =
        copy(viewportWidth = viewportWidth, viewportHeight = viewportHeight).clamped()

    fun reset(): ProfilePhotoCropTransform =
        centeredCropTransform(sourceWidth, sourceHeight, viewportWidth, viewportHeight)

    fun sourceCropRect(): ProfilePhotoSourceCropRect {
        val cropWidth = min(sourceWidth.toFloat(), viewportWidth / scale)
        val cropHeight = min(sourceHeight.toFloat(), viewportHeight / scale)
        val left = ((sourceWidth - cropWidth) / 2f) - (offsetX / scale)
        val top = ((sourceHeight - cropHeight) / 2f) - (offsetY / scale)
        return ProfilePhotoSourceCropRect.fromFloatBounds(
            left = left.coerceIn(0f, sourceWidth - cropWidth),
            top = top.coerceIn(0f, sourceHeight - cropHeight),
            width = cropWidth,
            height = cropHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
    }
}

internal data class ProfilePhotoSourceCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    companion object {
        fun fromFloatBounds(
            left: Float,
            top: Float,
            width: Float,
            height: Float,
            sourceWidth: Int,
            sourceHeight: Int,
        ): ProfilePhotoSourceCropRect {
            val roundedLeft = left.roundToInt().coerceIn(0, sourceWidth - 1)
            val roundedTop = top.roundToInt().coerceIn(0, sourceHeight - 1)
            val roundedRight = (roundedLeft + width.roundToInt()).coerceIn(roundedLeft + 1, sourceWidth)
            val roundedBottom = (roundedTop + height.roundToInt()).coerceIn(roundedTop + 1, sourceHeight)
            return ProfilePhotoSourceCropRect(
                left = roundedLeft,
                top = roundedTop,
                right = roundedRight,
                bottom = roundedBottom,
            )
        }
    }
}

internal fun centeredCropTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): ProfilePhotoCropTransform =
    ProfilePhotoCropTransform(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        scale = minimumFillScale(sourceWidth, sourceHeight, viewportWidth, viewportHeight),
        offsetX = 0f,
        offsetY = 0f,
    )

internal fun minimumFillScale(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): Float = max(viewportWidth / sourceWidth, viewportHeight / sourceHeight)

private fun maxOffset(sourceSize: Int, viewportSize: Float, scale: Float): Float =
    max(0f, ((sourceSize * scale) - viewportSize) / 2f)

private const val MaxCropZoomMultiplier = 4f
