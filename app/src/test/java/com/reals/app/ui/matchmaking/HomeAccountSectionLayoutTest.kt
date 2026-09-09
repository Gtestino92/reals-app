package com.reals.app.ui.matchmaking

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAccountSectionLayoutTest {
    @Test
    fun `normal account header is used for wide normal text`() {
        assertEquals(
            AccountSectionHeaderLayout.Normal,
            accountSectionHeaderLayout(maxWidth = 327.dp, fontScale = 1f),
        )
    }

    @Test
    fun `account header stacks below narrow width boundary`() {
        assertEquals(
            AccountSectionHeaderLayout.Constrained,
            accountSectionHeaderLayout(maxWidth = 299.dp, fontScale = 1f),
        )
    }

    @Test
    fun `account header stacks for moderate width with large text`() {
        assertEquals(
            AccountSectionHeaderLayout.Constrained,
            accountSectionHeaderLayout(maxWidth = 327.dp, fontScale = 1.3f),
        )
    }

    @Test
    fun `account header stacks for very large text even with wider content`() {
        assertEquals(
            AccountSectionHeaderLayout.Constrained,
            accountSectionHeaderLayout(maxWidth = 360.dp, fontScale = 1.8f),
        )
    }
}
