package com.reals.app.ui.chat

import com.reals.app.domain.model.VisualProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerProfileScreenPresentationTest {
    @Test
    fun `personal message section is hidden when no message was submitted`() {
        assertFalse(
            shouldShowPartnerProfilePersonalMessageSection(
                profile = profile(partnerPersonalMessageSubmitted = false),
                partnerMessage = null,
                partnerMessageLoaded = false,
                loadingPartnerMessage = false,
                partnerMessageError = null,
            )
        )
    }

    @Test
    fun `personal message section is hidden when submitted message loads blank`() {
        assertFalse(
            shouldShowPartnerProfilePersonalMessageSection(
                profile = profile(partnerPersonalMessageSubmitted = true),
                partnerMessage = " ",
                partnerMessageLoaded = true,
                loadingPartnerMessage = false,
                partnerMessageError = null,
            )
        )
    }

    @Test
    fun `personal message section remains visible while submitted message is loading`() {
        assertTrue(
            shouldShowPartnerProfilePersonalMessageSection(
                profile = profile(partnerPersonalMessageSubmitted = true),
                partnerMessage = null,
                partnerMessageLoaded = false,
                loadingPartnerMessage = true,
                partnerMessageError = null,
            )
        )
    }

    @Test
    fun `personal message section is visible when submitted message has content`() {
        assertTrue(
            shouldShowPartnerProfilePersonalMessageSection(
                profile = profile(partnerPersonalMessageSubmitted = true),
                partnerMessage = "Hola",
                partnerMessageLoaded = true,
                loadingPartnerMessage = false,
                partnerMessageError = null,
            )
        )
    }

    private fun profile(
        partnerPersonalMessageSubmitted: Boolean,
    ) = VisualProfile(
        profileId = "profile-1",
        displayName = "Ana",
        age = 31,
        bio = null,
        photos = emptyList(),
        visualExpiresAt = null,
        myPersonalMessageSubmitted = false,
        partnerPersonalMessageSubmitted = partnerPersonalMessageSubmitted,
        partnerPersonalMessageRead = true,
        decisionRequiresPartnerPersonalMessageRead = false,
        affinityIndicators = emptyList(),
        profileQuestions = emptyList(),
    )
}
