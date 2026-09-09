package com.reals.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSafetyTest {
    @Test
    fun `normalizeMultiline keeps accented spanish characters`() {
        val value = "á é í ó ú ñ Á É Í Ó Ú Ñ ¿¡"

        assertEquals(value, TextSafety.normalizeMultiline(value, maxLength = 100))
    }

    @Test
    fun `normalizeSingleLine keeps accented spanish characters`() {
        val value = "á é í ó ú ñ Á É Í Ó Ú Ñ ¿¡"

        assertEquals(value, TextSafety.normalizeSingleLine(value, maxLength = 100))
    }

    @Test
    fun `normalizeMultiline removes unsafe invisible control chars`() {
        val normalized = TextSafety.normalizeMultiline("Ho\u0000la\u202E mun\u0008do", maxLength = 100)

        assertEquals("Hola mundo", normalized)
    }

    @Test
    fun `normalizeMultiline preserves new lines but collapses excessive blank lines`() {
        val normalized = TextSafety.normalizeMultiline("uno\n\n\n\n dos\n tres", maxLength = 100)

        assertEquals("uno\n\ndos\ntres", normalized)
    }

    @Test
    fun `containsHtmlLikeMarkup detects simple html`() {
        assertTrue(TextSafety.containsHtmlLikeMarkup("<b>hola</b>"))
        assertTrue(TextSafety.containsHtmlLikeMarkup("<script>alert(1)</script>"))
    }

    @Test
    fun `containsHtmlLikeMarkup does not reject normal spanish punctuation`() {
        assertFalse(TextSafety.containsHtmlLikeMarkup("Qué tal, mañana nos vemos ¿sí?"))
    }

    @Test
    fun normalizeSingleLineRemovesUnsafeCharactersAndLimitsLength() {
        val normalized = TextSafety.normalizeSingleLine("  Alice\u202E\n\t  Smith\u0000  ", maxLength = 10)

        assertEquals("Alice Smit", normalized)
    }

    @Test
    fun `normalizeSingleLine replaces line breaks with spaces`() {
        val normalized = TextSafety.normalizeSingleLine("hola\nmundo\r\nmañana", maxLength = 100)

        assertEquals("hola mundo mañana", normalized)
    }

    @Test
    fun `normalizeMultiline preserves allowed newlines`() {
        val normalized = TextSafety.normalizeMultiline("uno\ndos\n\ntres", maxLength = 100)

        assertEquals("uno\ndos\n\ntres", normalized)
    }

    @Test
    fun `safeDisplay trims and limits long content`() {
        val normalized = TextSafety.safeDisplay("   123456789   ", maxLength = 5)

        assertEquals("12345", normalized)
    }

    @Test
    fun normalizeMultilinePreservesSafeLineBreaksAndCollapsesBlankLines() {
        val normalized = TextSafety.normalizeMultiline("  Hola   mundo\r\n\r\n\r\n  linea\t dos  ", maxLength = 100)

        assertEquals("Hola mundo\n\nlinea dos", normalized)
    }

    @Test
    fun containsHtmlLikeMarkupDetectsTags() {
        assertTrue(TextSafety.containsHtmlLikeMarkup("<script>alert(1)</script>"))
        assertTrue(TextSafety.containsHtmlLikeMarkup("hola <img src=x onerror=alert(1)>"))
        assertFalse(TextSafety.containsHtmlLikeMarkup("2 < 3 y 5 > 4"))
    }
}
