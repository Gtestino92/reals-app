package com.reals.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoActionPresentationTest {
    @Test
    fun `progress titles map by action kind`() {
        assertEquals("Subiendo foto...", addAction.progressTitle())
        assertEquals("Reemplazando foto...", replaceAction.progressTitle())
        assertEquals("Eliminando foto...", deleteAction.progressTitle())
        assertEquals("Procesando foto...", (null as ProfilePhotoActionPresentation?).progressTitle())
    }

    @Test
    fun `slot state descriptions map by action kind`() {
        assertEquals("Subiendo foto", addAction.slotStateDescription())
        assertEquals("Reemplazando foto", replaceAction.slotStateDescription())
        assertEquals("Eliminando foto", deleteAction.slotStateDescription())
        assertEquals("Procesando foto", (null as ProfilePhotoActionPresentation?).slotStateDescription())
    }

    @Test
    fun `target matching is exact by position`() {
        assertTrue(addAction.targetsPosition(4))
        assertFalse(addAction.targetsPosition(3))
        assertFalse((null as ProfilePhotoActionPresentation?).targetsPosition(4))
    }

    @Test
    fun `add selection target converts to presentation action preserving position`() {
        val target = ProfilePhotoSelectionTarget.Add(position = 6)

        val action = target.toProfilePhotoActionPresentation()

        assertEquals(ProfilePhotoActionKind.Add, action.kind)
        assertEquals(6, action.position)
        assertEquals(null, action.photoId)
    }

    @Test
    fun `replace selection target converts to presentation action preserving photo id and position`() {
        val target = ProfilePhotoSelectionTarget.Replace(photoId = "photo-8", position = 8)

        val action = target.toProfilePhotoActionPresentation()

        assertEquals(ProfilePhotoActionKind.Replace, action.kind)
        assertEquals(8, action.position)
        assertEquals("photo-8", action.photoId)
    }

    @Test
    fun `saved primitive state restores presentation action`() {
        val action = profilePhotoActionPresentation(
            kindName = ProfilePhotoActionKind.Delete.name,
            position = 2,
            photoId = "photo-2",
        )

        assertEquals(deleteAction, action)
    }

    @Test
    fun `invalid saved primitive state restores no action`() {
        assertEquals(null, profilePhotoActionPresentation("Legacy", 1, "photo-1"))
        assertEquals(null, profilePhotoActionPresentation(ProfilePhotoActionKind.Add.name, null, null))
    }

    @Test
    fun `selection conversion does not mutate input objects`() {
        val target = ProfilePhotoSelectionTarget.Replace(photoId = "photo-7", position = 7)
        val action = target.toProfilePhotoActionPresentation()

        assertNotSame(target, action)
        assertEquals("photo-7", target.photoId)
        assertEquals(7, target.position)
    }

    private val addAction = ProfilePhotoActionPresentation(ProfilePhotoActionKind.Add, position = 4)
    private val replaceAction = ProfilePhotoActionPresentation(
        kind = ProfilePhotoActionKind.Replace,
        position = 2,
        photoId = "photo-2",
    )
    private val deleteAction = ProfilePhotoActionPresentation(
        kind = ProfilePhotoActionKind.Delete,
        position = 2,
        photoId = "photo-2",
    )
}
