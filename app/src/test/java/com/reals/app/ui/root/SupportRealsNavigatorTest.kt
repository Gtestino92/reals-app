package com.reals.app.ui.root

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportRealsNavigatorTest {
    @Test
    fun `support navigator uses canonical cafecito url`() {
        assertEquals("https://cafecito.app/reals-app", CafecitoSupportUrl)
    }
}
