package com.reals.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchFoundInvalidationStoreTest {
    private val now = Instant.parse("2026-08-01T12:00:00Z")
    private val future = Instant.parse("2026-08-01T12:05:00Z")

    @Test
    fun `future tombstone is active until but not at expiry`() {
        val store = InMemoryMatchFoundInvalidationStore()

        store.recordInvalidation(" match-1 ", future, now)

        assertTrue(store.isInvalidated("match-1", now))
        assertTrue(store.isInvalidated(" match-1 ", future.minusMillis(1)))
        assertFalse(store.isInvalidated("match-1", future))
    }

    @Test
    fun `expired tombstone is inactive and cleaned up`() {
        val backing = mutableMapOf<String, Long>()
        val store = InMemoryMatchFoundInvalidationStore(backing)

        store.recordInvalidation("match-1", future, now)

        assertFalse(store.isInvalidated("match-1", future.plusMillis(1)))
        assertFalse(backing.containsKey("match-1"))
    }

    @Test
    fun `past and exact expiry are not retained`() {
        val backing = mutableMapOf<String, Long>()
        val store = InMemoryMatchFoundInvalidationStore(backing)

        store.recordInvalidation("match-1", now, now)
        store.recordInvalidation("match-2", now.minusMillis(1), now)

        assertFalse(store.isInvalidated("match-1", now))
        assertFalse(store.isInvalidated("match-2", now))
        assertTrue(backing.isEmpty())
    }

    @Test
    fun `different match ids are isolated`() {
        val store = InMemoryMatchFoundInvalidationStore()

        store.recordInvalidation("match-1", future, now)

        assertTrue(store.isInvalidated("match-1", now))
        assertFalse(store.isInvalidated("match-2", now))
    }

    @Test
    fun `new store instance reads existing persisted backing`() {
        val backing = mutableMapOf<String, Long>()
        InMemoryMatchFoundInvalidationStore(backing)
            .recordInvalidation("match-1", future, now)

        assertTrue(
            InMemoryMatchFoundInvalidationStore(backing)
                .isInvalidated("match-1", now.plusSeconds(60)),
        )
    }
}
