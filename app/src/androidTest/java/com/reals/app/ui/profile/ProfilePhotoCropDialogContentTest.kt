package com.reals.app.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ProfilePhotoCropDialogContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalWidthAndFontScaleUsesNormalArrangement() {
        setCropContent(width = 360.dp, height = 640.dp, fontScale = 1f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsNormalTag).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfilePhotoCropConfirmActionTag).assertIsDisplayed().assertHasClickAction()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 240.dp)
    }

    @Test
    fun mediumFontScaleKeepsActionsReachableWithoutForcedReflow() {
        setCropContent(width = 360.dp, height = 640.dp, fontScale = 1.3f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsNormalTag).assertIsDisplayed()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 220.dp)
    }

    @Test
    fun largeFontScaleUsesConstrainedPrimaryFirstArrangement() {
        setCropContent(width = 360.dp, height = 640.dp, fontScale = 1.5f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsConstrainedTag).assertIsDisplayed()
        assertPrimaryActionFullWidth()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 160.dp)
    }

    @Test
    fun narrowWidthAndLargeFontScaleRemainUsable() {
        setCropContent(width = 320.dp, height = 640.dp, fontScale = 1.5f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsConstrainedTag).assertIsDisplayed()
        assertPrimaryActionFullWidth()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 160.dp)
    }

    @Test
    fun veryLargeFontScaleKeepsCriticalActionsVisible() {
        setCropContent(width = 360.dp, height = 720.dp, fontScale = 2f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsConstrainedTag).assertIsDisplayed()
        assertPrimaryActionFullWidth()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 128.dp)
    }

    @Test
    fun reducedHeightAndLargeFontScaleKeepActionsReachable() {
        setCropContent(width = 360.dp, height = 560.dp, fontScale = 1.5f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsConstrainedTag).assertIsDisplayed()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 128.dp)
    }

    @Test
    fun narrowReducedHeightAndVeryLargeFontScaleStackSecondaryActions() {
        setCropContent(width = 320.dp, height = 640.dp, fontScale = 2f)

        composeRule.onNodeWithTag(ProfilePhotoCropActionsConstrainedTag).assertIsDisplayed()
        assertPrimaryActionFullWidth()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 128.dp)
    }

    @Test
    fun visibleErrorDoesNotPushActionsOutsideRoot() {
        setCropContent(
            width = 360.dp,
            height = 640.dp,
            fontScale = 1.5f,
            errorText = "No pudimos preparar la foto.\nVolvé a intentarlo.",
        )

        composeRule.onNodeWithText("No pudimos preparar la foto.\nVolvé a intentarlo.").assertIsDisplayed()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 128.dp)
    }

    @Test
    fun processingDisablesActionsButKeepsThemVisible() {
        setCropContent(width = 360.dp, height = 640.dp, fontScale = 1.5f, processing = true)

        composeRule.onNodeWithTag(ProfilePhotoCropCancelActionTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfilePhotoCropResetActionTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfilePhotoCropConfirmActionTag).assertIsNotEnabled()
        assertActionsVisibleAndSeparate()
    }

    @Test
    fun loadingDisablesPrimaryAndResetRemainsUnavailableWithoutBitmap() {
        setCropContent(width = 360.dp, height = 640.dp, fontScale = 1.5f, loading = true, hasBitmap = false)

        composeRule.onNodeWithTag(ProfilePhotoCropCancelActionTag).assertIsEnabled()
        composeRule.onNodeWithTag(ProfilePhotoCropResetActionTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfilePhotoCropConfirmActionTag).assertIsNotEnabled()
        assertActionsVisibleAndSeparate()
        assertViewportUsable(minHeight = 160.dp)
    }

    @Test
    fun primaryActionIsClickableWhenLoaded() {
        var confirmCount = 0
        setCropContent(width = 360.dp, height = 640.dp, fontScale = 1.5f, onConfirm = { confirmCount++ })

        composeRule.onNodeWithTag(ProfilePhotoCropConfirmActionTag)
            .assertIsEnabled()
            .performClick()

        assertEquals(1, confirmCount)
    }

    private fun setCropContent(
        width: Dp,
        height: Dp,
        fontScale: Float,
        loading: Boolean = false,
        processing: Boolean = false,
        hasBitmap: Boolean = true,
        errorText: String? = null,
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredSize(width, height)
                            .testTag(ProfilePhotoCropRootTag),
                    ) {
                        TestCropDialogContent(
                            loading = loading,
                            processing = processing,
                            hasBitmap = hasBitmap,
                            errorText = errorText,
                            onConfirm = onConfirm,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TestCropDialogContent(
        loading: Boolean,
        processing: Boolean,
        hasBitmap: Boolean,
        errorText: String?,
        onConfirm: () -> Unit,
    ) {
        val bitmap = remember(hasBitmap) {
            if (hasBitmap) Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888) else null
        }
        var transform by remember(bitmap) {
            mutableStateOf(
                bitmap?.let {
                    centeredCropTransform(
                        sourceWidth = it.width,
                        sourceHeight = it.height,
                        viewportWidth = 240f,
                        viewportHeight = 300f,
                    )
                },
            )
        }
        DisposableEffect(bitmap) {
            onDispose { bitmap?.recycle() }
        }
        ProfilePhotoCropDialogContent(
            bitmap = bitmap,
            transform = transform,
            loading = loading,
            processing = processing,
            errorText = errorText,
            onCancel = {},
            onReset = { transform = transform?.reset() },
            onConfirm = onConfirm,
            onViewportSizeChanged = { size ->
                val currentBitmap = bitmap
                if (currentBitmap != null && size.width > 0 && size.height > 0) {
                    transform = transform
                        ?.resized(size.width.toFloat(), size.height.toFloat())
                        ?: centeredCropTransform(
                            sourceWidth = currentBitmap.width,
                            sourceHeight = currentBitmap.height,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                        )
                }
            },
            onGestureTransform = { pan: Offset, zoom: Float ->
                transform = transform?.zoomBy(zoom)?.panBy(pan.x, pan.y)
            },
        )
    }

    private fun assertActionsVisibleAndSeparate() {
        val root = composeRule.onNodeWithTag(ProfilePhotoCropRootTag).getUnclippedBoundsInRoot()
        val cancel = composeRule.onNodeWithTag(ProfilePhotoCropCancelActionTag).getUnclippedBoundsInRoot()
        val reset = composeRule.onNodeWithTag(ProfilePhotoCropResetActionTag).getUnclippedBoundsInRoot()
        val confirm = composeRule.onNodeWithTag(ProfilePhotoCropConfirmActionTag).getUnclippedBoundsInRoot()

        listOf(cancel, reset, confirm).forEach { bounds ->
            assertWithinRoot(root, bounds)
        }
        assertFalse(cancel.overlaps(reset))
        assertFalse(cancel.overlaps(confirm))
        assertFalse(reset.overlaps(confirm))
    }

    private fun assertViewportUsable(minHeight: Dp) {
        val root = composeRule.onNodeWithTag(ProfilePhotoCropRootTag).getUnclippedBoundsInRoot()
        val viewport = composeRule.onNodeWithTag(ProfilePhotoCropViewportTag).getUnclippedBoundsInRoot()

        assertWithinRoot(root, viewport)
        val viewportWidth = viewport.right - viewport.left
        val viewportHeight = viewport.bottom - viewport.top
        assertTrue(viewportHeight >= minHeight)
        assertTrue(viewportWidth >= minHeight * ProfilePhotoPresentationAspectRatio)
        assertTrue(abs((viewportWidth / viewportHeight) - ProfilePhotoPresentationAspectRatio) < 0.03f)
    }

    private fun assertPrimaryActionFullWidth() {
        val root = composeRule.onNodeWithTag(ProfilePhotoCropRootTag).getUnclippedBoundsInRoot()
        val confirm = composeRule.onNodeWithTag(ProfilePhotoCropConfirmActionTag).getUnclippedBoundsInRoot()

        val rootWidth = root.right - root.left
        val confirmWidth = confirm.right - confirm.left
        assertTrue(confirmWidth >= rootWidth - 40.dp)
    }

    private fun assertWithinRoot(root: DpRect, bounds: DpRect) {
        assertTrue(bounds.left >= root.left)
        assertTrue(bounds.top >= root.top)
        assertTrue(bounds.right <= root.right)
        assertTrue(bounds.bottom <= root.bottom)
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}
