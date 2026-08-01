package com.reals.app.foreground

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundDestinationTrackerTest {
    @Test
    fun `resumed lifecycle publishes current destination`() {
        val tracker = AtomicForegroundDestinationTracker()
        val publisher = ForegroundDestinationLifecyclePublisher(tracker.register())

        publisher.onDestinationChanged(ForegroundDestination.Home)
        assertEquals(null, tracker.current())

        publisher.onResume()

        assertEquals(ForegroundDestination.Home, tracker.current())
    }

    @Test
    fun `root state change while resumed updates destination`() {
        val tracker = AtomicForegroundDestinationTracker()
        val publisher = ForegroundDestinationLifecyclePublisher(tracker.register())

        publisher.onDestinationChanged(ForegroundDestination.Home)
        publisher.onResume()
        publisher.onDestinationChanged(ForegroundDestination.SecondChat("connection-1"))

        assertEquals(ForegroundDestination.SecondChat("connection-1"), tracker.current())
    }

    @Test
    fun `pause and stop clear destination`() {
        val tracker = AtomicForegroundDestinationTracker()
        val publisher = ForegroundDestinationLifecyclePublisher(tracker.register())

        publisher.onDestinationChanged(ForegroundDestination.SecondChat("connection-1"))
        publisher.onResume()
        publisher.onPause()

        assertEquals(null, tracker.current())

        publisher.onResume()
        publisher.onStop()

        assertEquals(null, tracker.current())
    }

    @Test
    fun `changing second chat and returning home replace visible target`() {
        val tracker = AtomicForegroundDestinationTracker()
        val publisher = ForegroundDestinationLifecyclePublisher(tracker.register())

        publisher.onResume()
        publisher.onDestinationChanged(ForegroundDestination.SecondChat("connection-1"))
        publisher.onDestinationChanged(ForegroundDestination.SecondChat("connection-2"))
        assertEquals(ForegroundDestination.SecondChat("connection-2"), tracker.current())

        publisher.onDestinationChanged(ForegroundDestination.Home)

        assertEquals(ForegroundDestination.Home, tracker.current())
    }

    @Test
    fun `disposing older registration does not clear newer destination`() {
        val tracker = AtomicForegroundDestinationTracker()
        val oldPublisher = ForegroundDestinationLifecyclePublisher(tracker.register())
        val newPublisher = ForegroundDestinationLifecyclePublisher(tracker.register())

        oldPublisher.onDestinationChanged(ForegroundDestination.SecondChat("connection-old"))
        oldPublisher.onResume()
        newPublisher.onDestinationChanged(ForegroundDestination.Home)
        newPublisher.onResume()
        oldPublisher.onDispose()

        assertEquals(ForegroundDestination.Home, tracker.current())
    }
}
