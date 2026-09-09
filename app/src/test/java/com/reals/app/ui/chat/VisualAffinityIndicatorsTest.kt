package com.reals.app.ui.chat

import com.reals.app.domain.model.VisualAffinityIndicator
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualAffinityIndicatorsTest {
    @Test
    fun `empty input returns empty`() {
        assertEquals(emptyList<VisualAffinityIndicator>(), affinityIndicatorsForDisplay(emptyList()))
    }

    @Test
    fun `blank category ids are excluded`() {
        val indicators = affinityIndicatorsForDisplay(
            listOf(
                VisualAffinityIndicator(categoryId = "", title = "Música"),
                VisualAffinityIndicator(categoryId = "SPORTS", title = "Deportes"),
            )
        )

        assertEquals(listOf("SPORTS"), indicators.map { it.categoryId })
    }

    @Test
    fun `blank titles are excluded`() {
        val indicators = affinityIndicatorsForDisplay(
            listOf(
                VisualAffinityIndicator(categoryId = "MUSIC", title = " "),
                VisualAffinityIndicator(categoryId = "SPORTS", title = "Deportes"),
            )
        )

        assertEquals(listOf("SPORTS"), indicators.map { it.categoryId })
    }

    @Test
    fun `duplicate category ids keep first item`() {
        val indicators = affinityIndicatorsForDisplay(
            listOf(
                VisualAffinityIndicator(categoryId = "MUSIC", title = "Música"),
                VisualAffinityIndicator(categoryId = "MUSIC", title = "Otra música"),
                VisualAffinityIndicator(categoryId = "SPORTS", title = "Deportes"),
            )
        )

        assertEquals(
            listOf("Música", "Deportes"),
            indicators.map { it.title },
        )
    }

    @Test
    fun `backend order is preserved`() {
        val indicators = affinityIndicatorsForDisplay(
            listOf(
                VisualAffinityIndicator(categoryId = "CINEMA", title = "Cine"),
                VisualAffinityIndicator(categoryId = "MUSIC", title = "Música"),
                VisualAffinityIndicator(categoryId = "SPORTS", title = "Deportes"),
            )
        )

        assertEquals(
            listOf("CINEMA", "MUSIC", "SPORTS"),
            indicators.map { it.categoryId },
        )
    }

    @Test
    fun `more than three valid items are capped at three`() {
        val indicators = affinityIndicatorsForDisplay(
            listOf(
                VisualAffinityIndicator(categoryId = "MUSIC", title = "Música"),
                VisualAffinityIndicator(categoryId = "CINEMA", title = "Cine"),
                VisualAffinityIndicator(categoryId = "SPORTS", title = "Deportes"),
                VisualAffinityIndicator(categoryId = "BOOKS", title = "Libros"),
            )
        )

        assertEquals(
            listOf("MUSIC", "CINEMA", "SPORTS"),
            indicators.map { it.categoryId },
        )
    }

    @Test
    fun `valid Spanish titles remain unchanged`() {
        val indicators = affinityIndicatorsForDisplay(
            listOf(
                VisualAffinityIndicator(categoryId = "MUSIC", title = "Música"),
            )
        )

        assertEquals("Música", indicators.single().title)
    }
}
