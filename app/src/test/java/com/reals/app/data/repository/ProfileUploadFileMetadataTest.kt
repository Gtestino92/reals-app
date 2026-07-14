package com.reals.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUploadFileMetadataTest {
    @Test
    fun resolverProvidedMimeWins() {
        assertEquals(
            "image/webp",
            ProfileUploadFileMetadata.contentType("image/webp", "photo.jpg"),
        )
    }

    @Test
    fun jpgFallbackBecomesImageJpeg() {
        assertEquals("image/jpeg", ProfileUploadFileMetadata.contentType(null, "crop.jpg"))
    }

    @Test
    fun jpegFallbackBecomesImageJpeg() {
        assertEquals("image/jpeg", ProfileUploadFileMetadata.contentType(null, "crop.jpeg"))
    }

    @Test
    fun pngFallbackRemainsImagePng() {
        assertEquals("image/png", ProfileUploadFileMetadata.contentType(null, "crop.png"))
    }

    @Test
    fun unknownExtensionFallsBackSafely() {
        assertEquals(
            ProfileUploadFileMetadata.DefaultMimeType,
            ProfileUploadFileMetadata.contentType(null, "crop.unknown"),
        )
    }

    @Test
    fun contentDisplayNameIsUsedWhenAvailable() {
        assertEquals(
            "selected.jpg",
            ProfileUploadFileMetadata.displayName("selected.jpg", "ignored.png"),
        )
    }

    @Test
    fun fileUriLastPathSegmentIsUsedWhenQueryMetadataUnavailable() {
        assertEquals(
            "profile-photo-crop-1.jpg",
            ProfileUploadFileMetadata.displayName(null, "profile-photo-crop-1.jpg"),
        )
    }

    @Test
    fun finalFallbackIncludesValidImageExtension() {
        assertEquals(
            "profile-photo.jpg",
            ProfileUploadFileMetadata.displayName(null, null),
        )
    }
}
