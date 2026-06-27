package com.reals.app.ui.matchmaking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.reals.app.domain.model.SearchLocationInput
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "RealsLocation"
private const val SEARCH_LOCATION_CACHE_MAX_AGE_MILLIS = 15 * 60 * 1000L
private const val SEARCH_LOCATION_MAX_ACCURACY_METERS = 1000
private const val CURRENT_LOCATION_TIMEOUT_MILLIS = 7_000L

internal const val SEARCH_LOCATION_UNAVAILABLE_MESSAGE =
    "No pudimos obtener tu ubicacion. Verifica que la ubicacion del telefono este activada e intenta nuevamente."

private val sharedSearchLocationCache = SearchLocationMemoryCache()

internal fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

internal object DeviceSearchLocationResolver {
    suspend fun prewarmIfPermitted(context: Context): SearchLocationInput? =
        SearchLocationResolver(
            source = AndroidSearchLocationSource(context),
            cache = sharedSearchLocationCache,
        ).prewarmIfPermitted()

    suspend fun resolveForSearch(context: Context): SearchLocationInput =
        SearchLocationResolver(
            source = AndroidSearchLocationSource(context),
            cache = sharedSearchLocationCache,
        ).resolveForSearch()
}

internal suspend fun currentSearchLocation(context: Context): SearchLocationInput {
    return DeviceSearchLocationResolver.resolveForSearch(context)
}

internal class SearchLocationResolver(
    private val source: SearchLocationSource,
    private val cache: SearchLocationMemoryCache = SearchLocationMemoryCache(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val cacheMaxAgeMillis: Long = SEARCH_LOCATION_CACHE_MAX_AGE_MILLIS,
    private val maxAccuracyMeters: Int = SEARCH_LOCATION_MAX_ACCURACY_METERS,
    private val currentLocationTimeoutMillis: Long = CURRENT_LOCATION_TIMEOUT_MILLIS,
) {
    suspend fun prewarmIfPermitted(): SearchLocationInput? {
        if (!source.hasLocationPermission()) return null
        return runCatching { bestAvailableLocation(allowCurrentRequest = true) }.getOrNull()
    }

    suspend fun resolveForSearch(): SearchLocationInput {
        if (!source.hasLocationPermission()) {
            error("Falta permiso de ubicacion.")
        }

        return bestAvailableLocation(allowCurrentRequest = true)
            ?: error(SEARCH_LOCATION_UNAVAILABLE_MESSAGE)
    }

    private suspend fun bestAvailableLocation(allowCurrentRequest: Boolean): SearchLocationInput? {
        cache.getValid(
            nowMillis = nowMillis(),
            maxAgeMillis = cacheMaxAgeMillis,
            maxAccuracyMeters = maxAccuracyMeters,
        )?.let {
            return it.location
        }

        source.newestLastKnownLocation()?.takeIf(::isAcceptable)?.let { location ->
            cache.put(location)
            return location.location
        }

        if (!allowCurrentRequest) return null

        val current = withTimeoutOrNull(currentLocationTimeoutMillis) {
            source.currentLocation()
        }?.takeIf(::isAcceptable)
        if (current != null) {
            cache.put(current)
        }
        return current?.location
    }

    private fun isAcceptable(location: ResolvedSearchLocation): Boolean =
        cache.isValid(
            location = location,
            nowMillis = nowMillis(),
            maxAgeMillis = cacheMaxAgeMillis,
            maxAccuracyMeters = maxAccuracyMeters,
        )
}

internal interface SearchLocationSource {
    fun hasLocationPermission(): Boolean
    fun newestLastKnownLocation(): ResolvedSearchLocation?
    suspend fun currentLocation(): ResolvedSearchLocation?
}

internal data class ResolvedSearchLocation(
    val location: SearchLocationInput,
    val capturedAtMillis: Long,
)

internal class SearchLocationMemoryCache {
    private var cachedLocation: ResolvedSearchLocation? = null

    fun put(location: ResolvedSearchLocation) {
        cachedLocation = location
    }

    fun getValid(
        nowMillis: Long,
        maxAgeMillis: Long = SEARCH_LOCATION_CACHE_MAX_AGE_MILLIS,
        maxAccuracyMeters: Int = SEARCH_LOCATION_MAX_ACCURACY_METERS,
    ): ResolvedSearchLocation? = cachedLocation?.takeIf { location ->
        isValid(
            location = location,
            nowMillis = nowMillis,
            maxAgeMillis = maxAgeMillis,
            maxAccuracyMeters = maxAccuracyMeters,
        )
    }

    fun isValid(
        location: ResolvedSearchLocation,
        nowMillis: Long,
        maxAgeMillis: Long = SEARCH_LOCATION_CACHE_MAX_AGE_MILLIS,
        maxAccuracyMeters: Int = SEARCH_LOCATION_MAX_ACCURACY_METERS,
    ): Boolean {
        val ageMillis = nowMillis - location.capturedAtMillis
        val accuracy = location.location.accuracyMeters
        return ageMillis <= maxAgeMillis && (accuracy == null || accuracy <= maxAccuracyMeters)
    }
}

private class AndroidSearchLocationSource(
    private val context: Context,
) : SearchLocationSource {
    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasLocationPermission(): Boolean = hasLocationPermission(context)

    override fun newestLastKnownLocation(): ResolvedSearchLocation? =
        newestLastKnownLocation(context, locationManager)?.toResolvedSearchLocation()

    override suspend fun currentLocation(): ResolvedSearchLocation? {
        val provider = preferredProvider(locationManager)
        Log.d(TAG, "selected provider=$provider")
        if (provider == null) return null

        return requestCurrentLocation(context, locationManager, provider)?.toResolvedSearchLocation()
    }
}

private fun preferredProvider(locationManager: LocationManager): String? {
    return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
}

private suspend fun requestCurrentLocation(
    context: Context,
    locationManager: LocationManager,
    provider: String,
): Location? = suspendCancellableCoroutine { continuation ->
    val cancellationSignal = CancellationSignal()
    continuation.invokeOnCancellation { cancellationSignal.cancel() }
    try {
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    } catch (exception: SecurityException) {
        if (continuation.isActive) continuation.resume(null)
    } catch (exception: IllegalArgumentException) {
        if (continuation.isActive) continuation.resume(null)
    }
}

private fun newestLastKnownLocation(
    context: Context,
    locationManager: LocationManager,
): Location? {
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val hasCoarseLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasFineLocation && !hasCoarseLocation) {
        return null
    }

    return listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )
        .mapNotNull { provider ->
            runCatching {
                locationManager.getLastKnownLocation(provider)
            }.getOrNull()
        }
        .maxByOrNull { it.time }
}

private fun Location.toSearchLocationInput(): SearchLocationInput = SearchLocationInput(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy.takeIf { hasAccuracy() }?.toInt()?.coerceIn(0, 100000),
)

private fun Location.toResolvedSearchLocation(): ResolvedSearchLocation = ResolvedSearchLocation(
    location = toSearchLocationInput(),
    capturedAtMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
)

internal fun signedDecimalInput(value: String): String =
    value.filterIndexed { index, char ->
        char.isDigit() || char == '.' || (char == '-' && index == 0)
    }

internal fun validateLocation(
    latitude: String,
    longitude: String,
    accuracy: String,
): SearchLocationInput? {
    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val parsedAccuracy = accuracy.toIntOrNull()
    if (parsedLatitude == null || parsedLongitude == null) return null
    if (parsedLatitude !in -90.0..90.0 || parsedLongitude !in -180.0..180.0) return null
    if (parsedAccuracy != null && parsedAccuracy !in 0..100000) return null
    return SearchLocationInput(
        latitude = parsedLatitude,
        longitude = parsedLongitude,
        accuracyMeters = parsedAccuracy,
    )
}
