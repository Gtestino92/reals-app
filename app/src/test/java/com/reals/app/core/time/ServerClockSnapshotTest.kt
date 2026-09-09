package com.reals.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerClockSnapshotTest {
    @Test
    fun `estimated server time advances using elapsed realtime`() {
        val snapshot = ServerClockSnapshot(
            serverTimeEpochMillis = 1_000L,
            receivedAtElapsedRealtimeMillis = 200L,
        )

        assertEquals(1_300L, snapshot.estimatedServerTimeEpochMillis(500L))
    }

    @Test
    fun `device wall clock does not affect calculation`() {
        val snapshot = ServerClockSnapshot(
            serverTimeEpochMillis = 10_000L,
            receivedAtElapsedRealtimeMillis = 1_000L,
        )

        assertEquals(10_250L, snapshot.estimatedServerTimeEpochMillis(1_250L))
        assertEquals(10_250L, snapshot.estimatedServerTimeEpochMillis(1_250L))
    }

    @Test
    fun `negative elapsed delta is clamped`() {
        val snapshot = ServerClockSnapshot(
            serverTimeEpochMillis = 10_000L,
            receivedAtElapsedRealtimeMillis = 1_000L,
        )

        assertEquals(10_000L, snapshot.estimatedServerTimeEpochMillis(900L))
    }
}
