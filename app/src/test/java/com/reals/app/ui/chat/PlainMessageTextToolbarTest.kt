package com.reals.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainMessageTextToolbarTest {
    @Test
    fun `message text toolbar keeps selection copy actions without phone actions`() {
        assertEquals(listOf("Copiar", "Seleccionar todo"), PlainMessageTextToolbarActionLabels)

        val normalizedLabels = PlainMessageTextToolbarActionLabels.map { it.lowercase() }
        assertFalse(normalizedLabels.any { it.contains("tel") })
        assertFalse(normalizedLabels.any { it.contains("phone") })
        assertTrue(normalizedLabels.contains("copiar"))
    }
}
