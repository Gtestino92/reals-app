package com.reals.app.data.mapper

import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.QueueStatus
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchMappersTest {
    @Test
    fun `MatchResponseDto maps state and connection`() {
        val match = TestDtos.match(state = "VISUAL_PHASE").toDomain()

        assertEquals("match-1", match.id)
        assertEquals("user-1", match.userAId)
        assertEquals("user-2", match.userBId)
        assertEquals(MatchState.VisualPhase, match.state)
        assertEquals("connection-1", match.connectionId)
    }

    @Test
    fun `MatchResponseDto preserves unknown state`() {
        val match = TestDtos.match(state = "NEW_STATE").toDomain()

        assertTrue(match.state is MatchState.Unknown)
        assertEquals("NEW_STATE", match.state.rawValue)
    }

    @Test
    fun `QueueStatusResponseDto maps queue status`() {
        val status: QueueStatus = TestDtos.queueStatus(inQueue = true).toDomain()

        assertEquals("user-1", status.userId)
        assertEquals(true, status.inQueue)
    }
}
