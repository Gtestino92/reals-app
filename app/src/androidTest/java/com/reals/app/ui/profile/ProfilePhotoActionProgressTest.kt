package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ProfilePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePhotoActionProgressTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    @Test
    fun addProgressShowsCardAndOnlyTargetSlotIndicator() {
        setProgressContent(
            loading = true,
            action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4),
        )

        assertProgressCard(title = "Subiendo foto...")
        composeRule.onNodeWithTag(profilePhotoActionTargetIndicatorTag(4)).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoSlotTag(4))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Subiendo foto"))
        assertNoTargetIndicatorExcept(4)
        composeRule.onNodeWithTag(profilePhotoAddTag(4), useUnmergedTree = true).assertIsNotEnabled()
        composeRule.onNodeWithTag(profilePhotoReplaceTag(2)).assertIsNotEnabled()
        composeRule.onNodeWithTag(profilePhotoDeleteTag(2)).assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun replaceProgressIdentifiesOnlyReplacementSlot() {
        setProgressContent(
            loading = true,
            action = ProfilePhotoActionPresentation(
                kind = ProfilePhotoActionKind.Replace,
                position = 2,
                photoId = "photo-2",
            ),
        )

        assertProgressCard(title = "Reemplazando foto...")
        composeRule.onNodeWithTag(profilePhotoActionTargetIndicatorTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoSlotTag(2))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reemplazando foto"))
        assertNoTargetIndicatorExcept(2)
    }

    @Test
    fun deleteProgressIdentifiesOnlyDeletedSlotAndDisablesFilledActions() {
        setProgressContent(
            loading = true,
            action = ProfilePhotoActionPresentation(
                kind = ProfilePhotoActionKind.Delete,
                position = 3,
                photoId = "photo-3",
            ),
        )

        assertProgressCard(title = "Eliminando foto...")
        composeRule.onNodeWithTag(profilePhotoActionTargetIndicatorTag(3)).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoSlotTag(3))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Eliminando foto"))
        composeRule.onNodeWithTag(profilePhotoDeleteTag(3)).assertIsNotEnabled()
        composeRule.onNodeWithTag(profilePhotoReplaceTag(3)).assertIsNotEnabled()
        assertNoTargetIndicatorExcept(3)
    }

    @Test
    fun genericFallbackShowsProgressWithoutTargetSpinner() {
        setProgressContent(loading = true, action = null)

        assertProgressCard(title = "Procesando foto...")
        ProfilePhotoGridPositions.forEach { position ->
            composeRule.onAllNodesWithTag(profilePhotoActionTargetIndicatorTag(position)).assertCountEquals(0)
        }
    }

    @Test
    fun loadingFalseRemovesProgressAndRestoresSlotActions() {
        setProgressContent(
            loading = false,
            action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4),
        )

        composeRule.onAllNodesWithTag(ProfilePhotoActionProgressTag).assertCountEquals(0)
        ProfilePhotoGridPositions.forEach { position ->
            composeRule.onAllNodesWithTag(profilePhotoActionTargetIndicatorTag(position)).assertCountEquals(0)
        }
        composeRule.onNodeWithTag(profilePhotoAddTag(4), useUnmergedTree = true).assertIsEnabled()
        composeRule.onNodeWithTag(profilePhotoReplaceTag(2)).assertIsEnabled()
        composeRule.onNodeWithTag(profilePhotoDeleteTag(2)).assertIsEnabled()
    }

    @Test
    fun addProgressShowsLocalPreviewOnlyInTargetEmptySlot() {
        val action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4)
        setProgressContent(
            loading = true,
            action = action,
            previewState = startProfilePhotoPreview(action, "file:///tmp/crop-add.jpg", "generation-add", 10L, null),
        )

        composeRule.onNodeWithTag(profilePhotoLocalPreviewTag(4), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoActionTargetIndicatorTag(4)).assertIsDisplayed()
        ProfilePhotoGridPositions
            .filterNot { it == 4 }
            .forEach { position ->
                composeRule.onAllNodesWithTag(profilePhotoLocalPreviewTag(position), useUnmergedTree = true)
                    .assertCountEquals(0)
            }
        assertProgressCard(title = "Subiendo foto...")
        composeRule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun replaceProgressShowsLocalPreviewOverTargetPhoto() {
        val action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, position = 2, photoId = "photo-2")
        setProgressContent(
            loading = true,
            action = action,
            previewState = startProfilePhotoPreview(action, "file:///tmp/crop-replace.jpg", "generation-replace", 10L, "old-key"),
        )

        composeRule.onNodeWithTag(profilePhotoLocalPreviewTag(2), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoActionTargetIndicatorTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoReplaceTag(2)).assertIsNotEnabled()
    }

    @Test
    fun deleteAfterUploadSuccessClearsAwaitingPreviewAndLeavesEmptySlot() {
        val cleanupRequests = mutableListOf<String>()
        lateinit var harness: InteractiveGridHarness

        harness = setInteractiveGridContent(
            photos = testPhotos,
            previewState = awaitingPreview(),
            cleanupRequests = cleanupRequests,
            onDelete = { _, _ -> harness.photos.value = testPhotos.filterNot { it.id == "photo-2" } },
        )

        composeRule.onNodeWithTag(profilePhotoLocalPreviewTag(2), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(profilePhotoDeleteTag(2)).performClick()

        composeRule.onAllNodesWithTag(profilePhotoLocalPreviewTag(2), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag(profilePhotoAddTag(2), useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("delete-photo-2-2"), events)
            assertEquals(listOf("file:///tmp/crop-awaiting.jpg"), cleanupRequests)
            assertEquals(ProfilePhotoPreviewState.None, harness.previewState.value)
        }
    }

    @Test
    fun deleteFailureClearsPreviewAndKeepsAuthoritativePhoto() {
        val cleanupRequests = mutableListOf<String>()

        val harness = setInteractiveGridContent(
            photos = testPhotos,
            previewState = awaitingPreview(),
            cleanupRequests = cleanupRequests,
            error = ApiError.Network("Sin conexión"),
        )

        composeRule.onNodeWithTag(profilePhotoDeleteTag(2)).performClick()

        composeRule.onAllNodesWithTag(profilePhotoLocalPreviewTag(2), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag(profilePhotoReplaceTag(2)).assertIsDisplayed()
        composeRule.onNodeWithText("No pudimos subir la foto").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf("delete-photo-2-2"), events)
            assertEquals(listOf("file:///tmp/crop-awaiting.jpg"), cleanupRequests)
            assertEquals(ProfilePhotoPreviewState.None, harness.previewState.value)
        }
    }

    @Test
    fun reorderBeforeRemoteHandoffClearsAwaitingPreviewOnce() {
        val cleanupRequests = mutableListOf<String>()

        val harness = setInteractiveGridContent(
            photos = testPhotos,
            previewState = awaitingPreview(),
            cleanupRequests = cleanupRequests,
        )

        composeRule.runOnIdle { harness.move() }

        composeRule.onAllNodesWithTag(profilePhotoLocalPreviewTag(2), useUnmergedTree = true).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(listOf("move-photo-2-3"), events)
            assertEquals(listOf("file:///tmp/crop-awaiting.jpg"), cleanupRequests)
            assertEquals(ProfilePhotoPreviewState.None, harness.previewState.value)
        }
    }

    @Test
    fun staleTerminalResultDoesNotRemoveCurrentPreviewGeneration() {
        val currentAction = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4)
        val staleAction = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, position = 2, photoId = "photo-2")
        val currentPreview = startProfilePhotoPreview(
            action = currentAction,
            uriString = "file:///tmp/crop-current.jpg",
            generation = "current-generation",
            cropConfirmedAtElapsedMillis = 10L,
            oldCanonicalCacheKey = null,
        )
        val cleanupRequests = mutableListOf<String>()

        val harness = setInteractiveGridContent(
            photos = testPhotos,
            previewState = currentPreview,
            cleanupRequests = cleanupRequests,
        )

        composeRule.runOnIdle {
            val result = harness.previewState.value.onMatchingUploadFailed(staleAction)
            harness.previewState.value = result.state
            result.cleanupUriString?.let(cleanupRequests::add)
        }

        composeRule.onNodeWithTag(profilePhotoLocalPreviewTag(4), useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(currentPreview, harness.previewState.value)
            assertEquals(emptyList<String>(), cleanupRequests)
        }
    }

    @Test
    fun successFeedbackAppearsAfterLoadingCompletes() {
        setProgressContent(
            loading = false,
            action = null,
            message = "Foto subida correctamente.",
        )

        composeRule.onAllNodesWithTag(ProfilePhotoActionProgressTag).assertCountEquals(0)
        composeRule.onNodeWithText("Foto subida correctamente.").assertIsDisplayed()
    }

    @Test
    fun errorFeedbackReplacesProgressAndSuppressesStaleSuccess() {
        setProgressContent(
            loading = false,
            action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, position = 2),
            message = "Foto reemplazada correctamente.",
            error = ApiError.Network("Sin conexión"),
        )

        composeRule.onAllNodesWithTag(ProfilePhotoActionProgressTag).assertCountEquals(0)
        composeRule.onNodeWithText("No pudimos subir la foto").assertIsDisplayed()
        composeRule.onAllNodesWithText("Foto reemplazada correctamente.").assertCountEquals(0)
        ProfilePhotoGridPositions.forEach { position ->
            composeRule.onAllNodesWithTag(profilePhotoActionTargetIndicatorTag(position)).assertCountEquals(0)
        }
    }

    @Test
    fun largeFontProgressCardAndTargetSlotStayInsideRootWithoutOverlap() {
        setProgressContent(
            width = 276.dp,
            fontScale = 2f,
            loading = true,
            action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4),
        )

        val root = composeRule.onNodeWithTag(ProfilePhotoActionProgressRootTag).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithTag(ProfilePhotoActionProgressTag).getUnclippedBoundsInRoot()
        val indicator = composeRule.onNodeWithTag(ProfilePhotoActionProgressIndicatorTag).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithTag(ProfilePhotoActionProgressTitleTag).assertTextEquals("Subiendo foto...")
            .getUnclippedBoundsInRoot()
        val message = composeRule.onNodeWithTag(ProfilePhotoActionProgressMessageTag).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag(profilePhotoSlotTag(4)).getUnclippedBoundsInRoot()

        assertWithin(root, card)
        assertWithin(root, slot)
        assertWithin(card, indicator)
        assertWithin(card, title)
        assertWithin(card, message)
        assertFalse(indicator.overlaps(title))
        assertFalse(indicator.overlaps(message))
    }

    private fun setProgressContent(
        width: Dp = 327.dp,
        fontScale: Float = 1f,
        loading: Boolean,
        action: ProfilePhotoActionPresentation?,
        previewState: ProfilePhotoPreviewState = ProfilePhotoPreviewState.None,
        message: String? = null,
        error: ApiError? = null,
    ) {
        events.clear()
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .testTag(ProfilePhotoActionProgressRootTag),
                    ) {
                        Column {
                            if (loading) {
                                ProfilePhotoActionProgressCard(action = action)
                            }
                            PhotoGrid(
                                photos = testPhotos,
                                busy = loading,
                                pendingAction = action.takeIf { loading },
                                previewState = previewState,
                                onPickNewFile = { events += "add-$it" },
                                onPickReplacementFile = { photoId, position ->
                                    events += "replace-$photoId-$position"
                                },
                                onDeletePhoto = { photoId, position -> events += "delete-$photoId-$position" },
                                onMovePhoto = { photoId, targetPosition -> events += "move-$photoId-$targetPosition" },
                            )
                            ProfilePhotoActionFeedback(
                                photoActionLoading = loading,
                                photoActionError = error,
                                photoActionMessage = message,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setInteractiveGridContent(
        photos: List<ProfilePhoto>,
        previewState: ProfilePhotoPreviewState,
        cleanupRequests: MutableList<String>,
        error: ApiError? = null,
        onDelete: (photoId: String, position: Int) -> Unit = { _, _ -> },
    ): InteractiveGridHarness {
        events.clear()
        val harness = InteractiveGridHarness(
            photos = mutableStateOf(photos),
            previewState = mutableStateOf(previewState),
            cleanupRequests = cleanupRequests,
        )
        fun clearPreview() {
            val result = harness.previewState.value.clearForNewPhotoMutation()
            harness.previewState.value = result.state
            result.cleanupUriString?.let(cleanupRequests::add)
        }
        harness.move = {
            clearPreview()
            events += "move-photo-2-3"
        }
        composeRule.setContent {
            MaterialTheme {
                Column {
                    PhotoGrid(
                        photos = harness.photos.value,
                        busy = false,
                        previewState = harness.previewState.value,
                        onPickNewFile = { events += "add-$it" },
                        onPickReplacementFile = { photoId, position ->
                            events += "replace-$photoId-$position"
                        },
                        onDeletePhoto = { photoId, position ->
                            clearPreview()
                            events += "delete-$photoId-$position"
                            onDelete(photoId, position)
                        },
                        onMovePhoto = { photoId, targetPosition ->
                            clearPreview()
                            events += "move-$photoId-$targetPosition"
                        },
                    )
                    ProfilePhotoActionFeedback(
                        photoActionLoading = false,
                        photoActionError = error,
                        photoActionMessage = null,
                    )
                }
            }
        }
        return harness
    }

    private fun assertProgressCard(title: String) {
        composeRule.onNodeWithTag(ProfilePhotoActionProgressTag).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfilePhotoActionProgressIndicatorTag).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfilePhotoActionProgressTitleTag).assertTextEquals(title)
        composeRule.onNodeWithTag(ProfilePhotoActionProgressMessageTag).assertIsDisplayed()
    }

    private fun assertNoTargetIndicatorExcept(activePosition: Int) {
        ProfilePhotoGridPositions
            .filterNot { it == activePosition }
            .forEach { position ->
                composeRule.onAllNodesWithTag(profilePhotoActionTargetIndicatorTag(position)).assertCountEquals(0)
            }
    }

    private fun assertWithin(outer: DpRect, inner: DpRect) {
        assertTrue(inner.left >= outer.left)
        assertTrue(inner.top >= outer.top)
        assertTrue(inner.right <= outer.right)
        assertTrue(inner.bottom <= outer.bottom)
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    private val testPhotos = listOf(
        testPhoto(id = "photo-1", position = 1),
        testPhoto(id = "photo-2", position = 2),
        testPhoto(id = "photo-3", position = 3),
    )

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

    private fun awaitingPreview(): ProfilePhotoPreviewState.AwaitingRemote =
        ProfilePhotoPreviewState.AwaitingRemote(
            preview = PendingProfilePhotoPreview(
                action = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Replace, position = 2, photoId = "photo-2"),
                uriString = "file:///tmp/crop-awaiting.jpg",
                generation = "awaiting-generation",
                cropConfirmedAtElapsedMillis = 10L,
                oldCanonicalCacheKey = "old-key",
            ),
            remotePhotoId = "photo-2",
            remoteUrl = "https://static.reals.local/photo-2.jpg",
            uploadResponseAtElapsedMillis = 42L,
        )

    private class InteractiveGridHarness(
        val photos: MutableState<List<ProfilePhoto>>,
        val previewState: MutableState<ProfilePhotoPreviewState>,
        val cleanupRequests: MutableList<String>,
        var move: () -> Unit = {},
    )
}

private const val ProfilePhotoActionProgressRootTag = "profile_photo_action_progress_root"
