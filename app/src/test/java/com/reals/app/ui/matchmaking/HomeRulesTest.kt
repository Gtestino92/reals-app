package com.reals.app.ui.matchmaking

import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRulesTest {
    @Test
    fun `home status polling remains enabled for idle empty home`() {
        val model = emptyHomeScreenModel()

        assertTrue(model.shouldPollHome())
    }

    @Test
    fun `home polling interval is five seconds`() {
        org.junit.Assert.assertEquals(5_000L, HOME_POLL_INTERVAL_MILLIS)
    }
}
