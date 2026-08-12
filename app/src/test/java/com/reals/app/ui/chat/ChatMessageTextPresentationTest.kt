package com.reals.app.ui.chat

import com.reals.app.domain.model.ChatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTextPresentationTest {
    @Test
    fun `first chat phone-looking text remains plain selectable text`() {
        val text = "Escribime al +54 9 11 1234-5678"

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.FirstChat,
            phoneNumberCandidates = { candidateRange(it, "+54 9 11 1234-5678") },
        )

        assertEquals(text, presentation.text)
        assertTrue(presentation.phoneLinks.isEmpty())
    }

    @Test
    fun `second chat text without phone number has no telephone links`() {
        val presentation = chatMessageTextPresentation(
            content = "Nos vemos en la puerta",
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = { emptyList() },
        )

        assertEquals("Nos vemos en la puerta", presentation.text)
        assertTrue(presentation.phoneLinks.isEmpty())
    }

    @Test
    fun `second chat international phone format becomes tel link`() {
        val text = "Mi celu es +54 9 11 1234-5678"

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = { candidateRange(it, "+54 9 11 1234-5678") },
        )

        assertEquals(text, presentation.text)
        assertEquals(
            listOf(ChatMessagePhoneLink(start = 11, end = 29, uri = "tel:+5491112345678")),
            presentation.phoneLinks,
        )
    }

    @Test
    fun `second chat formatted local phone number preserves display and normalizes target`() {
        val text = "Mi número: 11 1234-5678"

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = { candidateRange(it, "11 1234-5678") },
        )

        assertEquals(text, presentation.text)
        assertEquals(
            listOf(ChatMessagePhoneLink(start = 11, end = 23, uri = "tel:1112345678")),
            presentation.phoneLinks,
        )
    }

    @Test
    fun `second chat surrounding prose remains unchanged`() {
        val text = "Hola, si querés hablame al +54 11 1234 5678 mañana."

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = { candidateRange(it, "+54 11 1234 5678") },
        )

        assertEquals(text, presentation.text)
    }

    @Test
    fun `second chat supports multiple recognized phone ranges`() {
        val text = "Casa 11 1234-5678 o celu +54 9 11 9876-5432"

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = {
                candidateRange(it, "11 1234-5678") + candidateRange(it, "+54 9 11 9876-5432")
            },
        )

        assertEquals(
            listOf(
                ChatMessagePhoneLink(start = 5, end = 17, uri = "tel:1112345678"),
                ChatMessagePhoneLink(start = 25, end = 43, uri = "tel:+5491198765432"),
            ),
            presentation.phoneLinks,
        )
    }

    @Test
    fun `short numeric content is not aggressively linkified`() {
        val text = "Llegué 10 de 10"

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = { candidateRange(it, "10") + candidateRange(it, "10") },
        )

        assertTrue(presentation.phoneLinks.isEmpty())
    }

    @Test
    fun `date-like numeric content is not linkified`() {
        val text = "Nos vemos 2026-08-12"

        val presentation = chatMessageTextPresentation(
            content = text,
            chatType = ChatType.SecondChat,
            phoneNumberCandidates = { candidateRange(it, "2026-08-12") },
        )

        assertTrue(presentation.phoneLinks.isEmpty())
    }

    private fun candidateRange(text: String, value: String): List<PhoneNumberCandidate> {
        val start = text.indexOf(value)
        return if (start == -1) {
            emptyList()
        } else {
            listOf(PhoneNumberCandidate(start = start, end = start + value.length))
        }
    }
}
