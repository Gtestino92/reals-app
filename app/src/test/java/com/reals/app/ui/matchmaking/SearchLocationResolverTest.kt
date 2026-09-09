package com.reals.app.ui.matchmaking

import com.reals.app.domain.model.SearchLocationInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SearchLocationResolverTest {
    @Test
    fun `resolveForSearch returns cached recent acceptable location without current request`() = runTest {
        val now = 10_000L
        val cached = resolvedLocation(latitude = -34.6, capturedAtMillis = now - 60_000)
        val cache = SearchLocationMemoryCache().apply { put(cached) }
        val source = FakeSearchLocationSource()
        val resolver = resolver(source = source, cache = cache, nowMillis = now)

        val location = resolver.resolveForSearch()

        assertEquals(cached.location, location)
        assertEquals(0, source.lastKnownCalls)
        assertEquals(0, source.currentCalls)
    }

    @Test
    fun `resolveForSearch ignores stale cached location`() = runTest {
        val now = 20 * 60 * 1000L
        val staleCached = resolvedLocation(latitude = -34.6, capturedAtMillis = now - 16 * 60 * 1000L)
        val lastKnown = resolvedLocation(latitude = -34.7, capturedAtMillis = now - 60_000)
        val cache = SearchLocationMemoryCache().apply { put(staleCached) }
        val source = FakeSearchLocationSource(lastKnown = lastKnown)
        val resolver = resolver(source = source, cache = cache, nowMillis = now)

        val location = resolver.resolveForSearch()

        assertEquals(lastKnown.location, location)
        assertEquals(1, source.lastKnownCalls)
        assertEquals(0, source.currentCalls)
    }

    @Test
    fun `resolveForSearch ignores cached location with poor accuracy`() = runTest {
        val now = 10_000L
        val poorCached = resolvedLocation(latitude = -34.6, accuracyMeters = 1_500, capturedAtMillis = now)
        val current = resolvedLocation(latitude = -34.8, capturedAtMillis = now)
        val cache = SearchLocationMemoryCache().apply { put(poorCached) }
        val source = FakeSearchLocationSource(current = current)
        val resolver = resolver(source = source, cache = cache, nowMillis = now)

        val location = resolver.resolveForSearch()

        assertEquals(current.location, location)
        assertEquals(1, source.lastKnownCalls)
        assertEquals(1, source.currentCalls)
    }

    @Test
    fun `resolveForSearch falls back to current location when cache and last known are unavailable`() = runTest {
        val now = 10_000L
        val current = resolvedLocation(latitude = -34.8, capturedAtMillis = now)
        val source = FakeSearchLocationSource(current = current)
        val resolver = resolver(source = source, nowMillis = now)

        val location = resolver.resolveForSearch()

        assertEquals(current.location, location)
        assertEquals(1, source.lastKnownCalls)
        assertEquals(1, source.currentCalls)
    }

    @Test
    fun `prewarmIfPermitted does not resolve when permission is missing`() = runTest {
        val source = FakeSearchLocationSource(hasPermission = false)
        val resolver = resolver(source = source)

        val location = resolver.prewarmIfPermitted()

        assertNull(location)
        assertEquals(1, source.permissionCalls)
        assertEquals(0, source.lastKnownCalls)
        assertEquals(0, source.currentCalls)
    }

    @Test
    fun `prewarmIfPermitted fails silently`() = runTest {
        val source = FakeSearchLocationSource(currentFailure = IllegalStateException("boom"))
        val resolver = resolver(source = source)

        val location = resolver.prewarmIfPermitted()

        assertNull(location)
        assertEquals(1, source.lastKnownCalls)
        assertEquals(1, source.currentCalls)
    }

    @Test
    fun `resolveForSearch returns clear failure when no location is available`() = runTest {
        val resolver = resolver(source = FakeSearchLocationSource())

        try {
            resolver.resolveForSearch()
            fail("Expected resolveForSearch to fail")
        } catch (exception: IllegalStateException) {
            assertEquals(SEARCH_LOCATION_UNAVAILABLE_MESSAGE, exception.message)
        }
    }

    private fun resolver(
        source: FakeSearchLocationSource,
        cache: SearchLocationMemoryCache = SearchLocationMemoryCache(),
        nowMillis: Long = 10_000L,
    ): SearchLocationResolver = SearchLocationResolver(
        source = source,
        cache = cache,
        nowMillis = { nowMillis },
        currentLocationTimeoutMillis = 100,
    )

    private fun resolvedLocation(
        latitude: Double,
        accuracyMeters: Int = 50,
        capturedAtMillis: Long,
    ): ResolvedSearchLocation = ResolvedSearchLocation(
        location = SearchLocationInput(
            latitude = latitude,
            longitude = -58.4,
            accuracyMeters = accuracyMeters,
        ),
        capturedAtMillis = capturedAtMillis,
    )

    private class FakeSearchLocationSource(
        private val hasPermission: Boolean = true,
        private val lastKnown: ResolvedSearchLocation? = null,
        private val current: ResolvedSearchLocation? = null,
        private val currentFailure: Throwable? = null,
    ) : SearchLocationSource {
        var permissionCalls = 0
            private set
        var lastKnownCalls = 0
            private set
        var currentCalls = 0
            private set

        override fun hasLocationPermission(): Boolean {
            permissionCalls += 1
            return hasPermission
        }

        override fun newestLastKnownLocation(): ResolvedSearchLocation? {
            lastKnownCalls += 1
            return lastKnown
        }

        override suspend fun currentLocation(): ResolvedSearchLocation? {
            currentCalls += 1
            currentFailure?.let { throw it }
            return current
        }
    }
}
