package com.reals.app.ui.root

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileOperationHandlerPhotoMutationTest {
    @Test
    fun `add photo success state inserts returned file photo without full reload data`() {
        val existingPhoto = testPhoto(id = "photo-1", position = 1)
        val addedPhoto = testPhoto(id = "photo-2", position = 2)
        val previous = readyState(
            profile = activeProfile(photoCount = 1),
            photos = listOf(existingPhoto),
        )

        val updated = photoAddedState(previous, addedPhoto, "Foto subida correctamente.")

        assertEquals(listOf("photo-1", "photo-2"), updated.profilePhotos.map { it.id })
        assertEquals(2, updated.currentProfile().photoCount)
        assertEquals(ProfileStatus.Draft, updated.currentProfile().status)
        assertEquals("Foto subida correctamente.", updated.photoActionMessage)
        assertFalse(updated.addingPhoto)
    }

    @Test
    fun `replace photo success state replaces returned file photo without changing count`() {
        val firstPhoto = testPhoto(id = "photo-1", position = 1, url = "https://example.com/old.jpg")
        val secondPhoto = testPhoto(id = "photo-2", position = 2)
        val replacedPhoto = testPhoto(id = "photo-1", position = 1, url = "https://example.com/new.jpg")
        val previous = readyState(
            profile = activeProfile(photoCount = 2),
            photos = listOf(firstPhoto, secondPhoto),
        )

        val updated = photoReplacedState(previous, replacedPhoto, "Foto reemplazada correctamente.")

        assertEquals(listOf("photo-1", "photo-2"), updated.profilePhotos.map { it.id })
        assertEquals("https://example.com/new.jpg", updated.profilePhotos.first { it.id == "photo-1" }.url)
        assertEquals(2, updated.currentProfile().photoCount)
        assertEquals(ProfileStatus.Draft, updated.currentProfile().status)
        assertEquals("Foto reemplazada correctamente.", updated.photoActionMessage)
        assertFalse(updated.addingPhoto)
    }

    @Test
    fun `delete photo success state removes deleted photo and uses returned profile`() {
        val firstPhoto = testPhoto(id = "photo-1", position = 1)
        val secondPhoto = testPhoto(id = "photo-2", position = 2)
        val updatedProfile = activeProfile(photoCount = 1).copy(status = ProfileStatus.Draft)
        val previous = readyState(
            profile = activeProfile(photoCount = 2),
            photos = listOf(firstPhoto, secondPhoto),
        )

        val updated = photoDeletedState(previous, "photo-1", updatedProfile, "Foto eliminada.")

        assertEquals(listOf("photo-2"), updated.profilePhotos.map { it.id })
        assertEquals(1, updated.currentProfile().photoCount)
        assertEquals(ProfileStatus.Draft, updated.currentProfile().status)
        assertEquals("Foto eliminada.", updated.photoActionMessage)
        assertFalse(updated.addingPhoto)
    }

    private fun readyState(
        profile: Profile,
        photos: List<ProfilePhoto>,
    ): RealsRootUiState.Ready =
        RealsRootUiState.Ready(
            session = TestDomain.session().copy(profileSnapshot = ProfileSnapshot.Found(profile)),
            photos = PhotoManagementUiState(
                profilePhotos = photos,
                addingPhoto = true,
            ),
        )

    private fun activeProfile(photoCount: Int): Profile =
        TestDtos.profile(status = "ACTIVE").toDomain().copy(photoCount = photoCount)

    private fun testPhoto(
        id: String,
        position: Int,
        url: String = "https://example.com/$id.jpg",
    ): ProfilePhoto =
        TestDtos.photo(id = id, position = position).toDomain().copy(url = url)

    private fun RealsRootUiState.Ready.currentProfile(): Profile =
        (session.profileSnapshot as ProfileSnapshot.Found).profile
}
