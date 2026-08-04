package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePhotoGridAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wideNormalGridKeepsAccessibleActions() {
        setGrid(width = 327.dp, fontScale = 1f)

        assertGridStructure()
        assertOccupiedSlotActions()
        assertEmptySlotContent(position = 2)
    }

    @Test
    fun typicalConstrainedGridAtLargeFontKeepsActionsUsable() {
        setGrid(width = 276.dp, fontScale = 1.5f)

        assertGridStructure()
        assertOccupiedSlotActions()
        assertEmptySlotContent(position = 2)
    }

    @Test
    fun narrowGridAtVeryLargeFontKeepsCriticalControlsInsideSlots() {
        setGrid(width = 236.dp, fontScale = 2f)

        assertGridStructure()
        assertOccupiedSlotActions()
        assertEmptySlotContent(position = 2)
    }

    @Test
    fun narrowGridAtLargeFontKeepsEmptySlotContentVisible() {
        setGrid(width = 236.dp, fontScale = 1.5f)

        assertGridStructure()
        assertOccupiedSlotActions()
        assertEmptySlotContent(position = 2)
    }

    @Test
    fun typicalGridAtVeryLargeFontKeepsLabelsReachable() {
        setGrid(width = 276.dp, fontScale = 2f)

        assertGridStructure()
        assertOccupiedSlotActions()
        assertEmptySlotContent(position = 2)
    }

    @Test
    fun actionClicksInvokeOnlyTheirCallbacks() {
        val events = mutableListOf<String>()
        setGrid(
            width = 276.dp,
            fontScale = 1.5f,
            onPickNewFile = { events += "add-$it" },
            onPickReplacementFile = { photoId, position -> events += "replace-$photoId-$position" },
            onDeletePhoto = { photoId, position -> events += "delete-$photoId-$position" },
            onMovePhoto = { photoId, targetPosition -> events += "move-$photoId-$targetPosition" },
        )

        composeRule.onNodeWithTag(profilePhotoDeleteTag(1)).performClick()
        composeRule.onNodeWithTag(profilePhotoReplaceTag(1)).performClick()
        composeRule.onNodeWithTag(profilePhotoAddTag(2), useUnmergedTree = true).performClick()

        assertEquals(listOf("delete-photo-1-1", "replace-photo-1-1", "add-2"), events)
    }

    @Test
    fun busyStateDisablesPhotoGridMutations() {
        setGrid(width = 276.dp, fontScale = 1.5f, busy = true)

        composeRule.onNodeWithTag(profilePhotoDeleteTag(1)).assertIsNotEnabled()
        composeRule.onNodeWithTag(profilePhotoReplaceTag(1)).assertIsNotEnabled()
        composeRule.onNodeWithTag(profilePhotoAddTag(2), useUnmergedTree = true).assertIsNotEnabled()
    }

    private fun setGrid(
        width: Dp,
        fontScale: Float,
        busy: Boolean = false,
        onPickNewFile: (position: Int) -> Unit = {},
        onPickReplacementFile: (photoId: String, position: Int) -> Unit = { _, _ -> },
        onDeletePhoto: (photoId: String, position: Int) -> Unit = { _, _ -> },
        onMovePhoto: (photoId: String, targetPosition: Int) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(modifier = Modifier.requiredWidth(width)) {
                        PhotoGrid(
                            photos = listOf(
                                testPhoto(id = "photo-1", position = 1),
                                testPhoto(id = "photo-5", position = 5),
                            ),
                            busy = busy,
                            onPickNewFile = onPickNewFile,
                            onPickReplacementFile = onPickReplacementFile,
                            onDeletePhoto = onDeletePhoto,
                            onMovePhoto = onMovePhoto,
                        )
                    }
                }
            }
        }
    }

    private fun assertGridStructure() {
        val grid = composeRule.onNodeWithTag(ProfilePhotoGridRootTag).getUnclippedBoundsInRoot()
        val slots = ProfilePhotoGridPositions.map { position ->
            composeRule.onNodeWithTag(profilePhotoSlotTag(position)).assertIsDisplayed()
            composeRule.onNodeWithTag(profilePhotoSlotTag(position)).getUnclippedBoundsInRoot()
        }

        slots.forEach { assertWithin(grid, it) }
        assertSameColumn(slots[0], slots[3], slots[6])
        assertSameColumn(slots[1], slots[4], slots[7])
        assertSameColumn(slots[2], slots[5], slots[8])
        assertSameRow(slots[0], slots[1], slots[2])
        assertSameRow(slots[3], slots[4], slots[5])
        assertSameRow(slots[6], slots[7], slots[8])
        assertTrue(slots[1].left > slots[0].left)
        assertTrue(slots[2].left > slots[1].left)
        assertTrue(slots[3].top > slots[0].top)
    }

    private fun assertOccupiedSlotActions() {
        val grid = composeRule.onNodeWithTag(ProfilePhotoGridRootTag).getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag(profilePhotoSlotTag(1)).getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithTag(profilePhotoDeleteTag(1)).assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()
        val deleteVisual = composeRule.onNodeWithTag(profilePhotoDeleteVisualTag(1), useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val replace = composeRule.onNodeWithTag(profilePhotoReplaceTag(1)).assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()

        composeRule.onAllNodesWithText("Cambiar").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Borrar foto 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reemplazar foto 1").assertIsDisplayed()
        assertWithin(grid, delete)
        assertWithin(grid, replace)
        assertWithin(slot, delete)
        assertWithin(slot, replace)
        assertTrue(delete.width >= ProfilePhotoDeleteTouchTargetSize)
        assertTrue(delete.height >= ProfilePhotoDeleteTouchTargetSize)
        assertTrue(deleteVisual.width < delete.width)
        assertTrue(deleteVisual.height < delete.height)
        assertTrue(replace.height >= ProfilePhotoReplaceActionMinHeight)
        assertFalse(delete.overlaps(replace))
    }

    private fun assertEmptySlotContent(position: Int) {
        val slot = composeRule.onNodeWithTag(profilePhotoSlotTag(position)).getUnclippedBoundsInRoot()
        val add = composeRule.onNodeWithTag(profilePhotoAddTag(position), useUnmergedTree = true).assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag(profilePhotoAddPlusTag(position), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoAddLabelTag(position), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Agregar foto $position").assertIsDisplayed()
        assertWithin(slot, add)
    }

    private fun assertWithin(outer: DpRect, inner: DpRect) {
        assertTrue(inner.left >= outer.left)
        assertTrue(inner.top >= outer.top)
        assertTrue(inner.right <= outer.right)
        assertTrue(inner.bottom <= outer.bottom)
    }

    private fun assertSameColumn(first: DpRect, second: DpRect, third: DpRect) {
        assertClose(first.left, second.left)
        assertClose(second.left, third.left)
    }

    private fun assertSameRow(first: DpRect, second: DpRect, third: DpRect) {
        assertClose(first.top, second.top)
        assertClose(second.top, third.top)
    }

    private fun assertClose(first: Dp, second: Dp) {
        assertTrue(kotlin.math.abs((first - second).value) < 1f)
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    private val DpRect.width: Dp get() = right - left
    private val DpRect.height: Dp get() = bottom - top

    private fun testPhoto(id: String, position: Int): ProfilePhoto =
        ProfilePhoto(
            id = id,
            url = "",
            position = position,
            isPersonPhoto = true,
            isFullBody = false,
            validationStatus = "APPROVED",
            moderationStatus = "APPROVED",
        )
}
