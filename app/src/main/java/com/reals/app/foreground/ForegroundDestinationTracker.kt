package com.reals.app.foreground

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface ForegroundDestinationTracker {
    fun publish(destination: ForegroundDestination?)
    fun current(): ForegroundDestination?
    fun register(): ForegroundDestinationRegistration
}

interface ForegroundDestinationRegistration {
    fun publish(destination: ForegroundDestination?)
    fun clear()
    fun dispose()
}

class AtomicForegroundDestinationTracker : ForegroundDestinationTracker {
    private val nextOwnerId = AtomicLong(1L)
    private val state = AtomicReference(TrackedDestination())

    override fun publish(destination: ForegroundDestination?) {
        state.set(TrackedDestination(destination = destination))
    }

    override fun current(): ForegroundDestination? = state.get().destination

    override fun register(): ForegroundDestinationRegistration =
        Registration(ownerId = nextOwnerId.getAndIncrement())

    private inner class Registration(
        private val ownerId: Long,
    ) : ForegroundDestinationRegistration {
        override fun publish(destination: ForegroundDestination?) {
            state.set(TrackedDestination(ownerId = ownerId, destination = destination))
        }

        override fun clear() {
            clearOwnedDestination(ownerId)
        }

        override fun dispose() {
            clearOwnedDestination(ownerId)
        }
    }

    private fun clearOwnedDestination(ownerId: Long) {
        while (true) {
            val current = state.get()
            if (current.ownerId != ownerId) return
            if (state.compareAndSet(current, TrackedDestination())) return
        }
    }
}

private data class TrackedDestination(
    val ownerId: Long = 0L,
    val destination: ForegroundDestination? = null,
)
