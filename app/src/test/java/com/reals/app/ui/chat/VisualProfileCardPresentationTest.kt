package com.reals.app.ui.chat

import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.PublicProfileQuestion
import com.reals.app.domain.model.VisualAffinityIndicator
import com.reals.app.domain.model.VisualProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualProfileCardPresentationTest {
    @Test
    fun `review mode uses vertical editorial photo blocks and keeps optional content in sequence`() {
        val blocks = visualProfileContentBlocks(
            profile = profile(
                photos = photos(4),
                bio = "Me gusta caminar.",
                affinityIndicators = listOf(VisualAffinityIndicator("MUSIC", "Música")),
                profileQuestions = listOf(publicQuestion()),
            ),
            presentationMode = ProfilePresentationMode.Review,
        )

        assertEquals(
            listOf(
                VisualProfileContentBlock.Photo(photo(1), index = 0, total = 4),
                VisualProfileContentBlock.Identity,
                VisualProfileContentBlock.Bio,
                VisualProfileContentBlock.Photo(photo(2), index = 1, total = 4),
                VisualProfileContentBlock.Affinities,
                VisualProfileContentBlock.Photo(photo(3), index = 2, total = 4),
                VisualProfileContentBlock.Questions,
                VisualProfileContentBlock.Photo(photo(4), index = 3, total = 4),
            ),
            blocks,
        )
        assertFalse(blocks.contains(VisualProfileContentBlock.CompactPhotos))
    }

    @Test
    fun `browse mode uses compact photo presentation and then profile content`() {
        val blocks = visualProfileContentBlocks(
            profile = profile(
                photos = photos(3),
                bio = "Bio",
                affinityIndicators = listOf(VisualAffinityIndicator("BOOKS", "Libros")),
                profileQuestions = listOf(publicQuestion()),
            ),
            presentationMode = ProfilePresentationMode.Browse,
        )

        assertEquals(
            listOf(
                VisualProfileContentBlock.CompactPhotos,
                VisualProfileContentBlock.Identity,
                VisualProfileContentBlock.Bio,
                VisualProfileContentBlock.Affinities,
                VisualProfileContentBlock.Questions,
            ),
            blocks,
        )
    }

    @Test
    fun `optional profile content does not create empty sections`() {
        val reviewBlocks = visualProfileContentBlocks(
            profile = profile(
                photos = photos(1),
                bio = null,
                affinityIndicators = emptyList(),
                profileQuestions = emptyList(),
            ),
            presentationMode = ProfilePresentationMode.Review,
        )
        val browseBlocks = visualProfileContentBlocks(
            profile = profile(
                photos = emptyList(),
                bio = " ",
                affinityIndicators = listOf(VisualAffinityIndicator("", "Música")),
                profileQuestions = listOf(publicQuestion(answer = "")),
            ),
            presentationMode = ProfilePresentationMode.Browse,
        )

        assertEquals(
            listOf(
                VisualProfileContentBlock.Photo(photo(1), index = 0, total = 1),
                VisualProfileContentBlock.Identity,
            ),
            reviewBlocks,
        )
        assertEquals(listOf(VisualProfileContentBlock.Identity), browseBlocks)
    }

    @Test
    fun `browse and review handle different photo counts without duplicated vertical browse photos`() {
        val noPhotoBrowse = visualProfileContentBlocks(profile(photos = emptyList()), ProfilePresentationMode.Browse)
        val onePhotoBrowse = visualProfileContentBlocks(profile(photos = photos(1)), ProfilePresentationMode.Browse)
        val manyPhotoReview = visualProfileContentBlocks(profile(photos = photos(5)), ProfilePresentationMode.Review)

        assertFalse(noPhotoBrowse.contains(VisualProfileContentBlock.CompactPhotos))
        assertTrue(onePhotoBrowse.contains(VisualProfileContentBlock.CompactPhotos))
        assertEquals(5, manyPhotoReview.filterIsInstance<VisualProfileContentBlock.Photo>().size)
    }

    @Test
    fun `content description includes photo position count and partner name`() {
        assertEquals(
            "Foto 2 de 4 de Ana",
            visualProfilePhotoContentDescription(profile = profile(displayName = "Ana"), photoIndex = 1, totalPhotos = 4),
        )
    }

    @Test
    fun `public profile questions filter empty rows and preserve display order`() {
        val questions = publicProfileQuestionsForDisplay(
            listOf(
                publicQuestion(questionId = "B", prompt = "Prompt B", answer = "Respuesta B", position = 2),
                publicQuestion(questionId = "", prompt = "Prompt", answer = "Respuesta", position = 1),
                publicQuestion(questionId = "A", prompt = "Prompt A", answer = "Respuesta A", position = 1),
                publicQuestion(questionId = "C", prompt = "Prompt C", answer = "", position = 3),
            ),
        )

        assertEquals(listOf("A", "B"), questions.map { it.questionId })
    }

    private fun profile(
        displayName: String = "Ana",
        photos: List<ProfilePhoto> = photos(2),
        bio: String? = "Bio",
        affinityIndicators: List<VisualAffinityIndicator> = emptyList(),
        profileQuestions: List<PublicProfileQuestion> = emptyList(),
    ) = VisualProfile(
        profileId = "profile-1",
        displayName = displayName,
        age = 31,
        bio = bio,
        photos = photos,
        visualExpiresAt = null,
        myPersonalMessageSubmitted = false,
        partnerPersonalMessageSubmitted = false,
        partnerPersonalMessageRead = true,
        decisionRequiresPartnerPersonalMessageRead = false,
        affinityIndicators = affinityIndicators,
        profileQuestions = profileQuestions,
    )

    private fun photos(count: Int): List<ProfilePhoto> = (1..count).map(::photo)

    private fun photo(position: Int) = ProfilePhoto(
        id = "photo-$position",
        url = "https://example.com/$position.jpg",
        position = position,
        isPersonPhoto = true,
        isFullBody = position > 1,
        validationStatus = "VALIDATED",
        moderationStatus = "APPROVED",
    )

    private fun publicQuestion(
        questionId: String = "QUESTION_1",
        prompt: String = "Mi plan ideal",
        answer: String = "Café",
        position: Int = 1,
    ) = PublicProfileQuestion(
        questionId = questionId,
        prompt = prompt,
        answer = answer,
        position = position,
    )
}
