package com.reals.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSafetyTest {
    @Test
    fun normalizeSingleLineRemovesUnsafeCharactersAndLimitsLength() {
        val normalized = TextSafety.normalizeSingleLine("  Alice\u202E\n\t  Smith\u0000  ", maxLength = 10)

        assertEquals("Alice Smit", normalized)
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
