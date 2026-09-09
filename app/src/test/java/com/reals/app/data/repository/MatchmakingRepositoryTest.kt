package com.reals.app.data.repository

import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchmakingRepositoryTest {
    private val api = FakeRealsApi()
    private val repository = MatchmakingRepository(api, FakeAuthTokenProvider(), testApiExecutor())

    @Test
    fun `enqueue sends latitude longitude and accuracy`() = runBlocking {
        val status = repository.enqueue(
            SearchLocationInput(
                latitude = -34.6037,
                longitude = -58.3816,
                accuracyMeters = 25,
            )
        ).successValue()

        assertEquals("enqueueMatchmaking", api.calls.single())
        assertEquals(-34.6037, api.enqueueBody?.latitude ?: 0.0, 0.0)
        assertEquals(-58.3816, api.enqueueBody?.longitude ?: 0.0, 0.0)
        assertEquals(25, api.enqueueBody?.accuracyMeters)
        assertEquals(true, status.inQueue)
    }

    @Test
    fun `leave queue and get status map responses`() = runBlocking {
        repository.leaveQueue().successValue()
        repository.getQueueStatus().successValue()

        assertEquals(listOf("leaveMatchmakingQueue", "getMatchmakingQueueStatus"), api.calls)
    }
}
