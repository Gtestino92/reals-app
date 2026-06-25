package com.reals.app.ui.matchmaking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.reals.app.domain.model.SearchLocationInput
import kotlinx.coroutines.tasks.await

private const val RECENT_LOCATION_MAX_AGE_MILLIS = 5 * 60 * 1000L

internal fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
internal suspend fun currentSearchLocation(context: Context): SearchLocationInput? {
    if (!hasLocationPermission(context)) {
        return null
    }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
        return null
    }

    val client = LocationServices.getFusedLocationProviderClient(context)
    val cached = runCatching { client.lastLocation.await() }.getOrNull()
    if (cached != null && cached.isFreshEnough()) {
        return cached.toSearchLocationInput()
    }

    val providerCached = newestFreshLastKnownLocation(locationManager)
    if (providerCached != null) {
        return providerCached.toSearchLocationInput()
    }

    val cancellation = CancellationTokenSource()
    val fresh = try {
        runCatching {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            ).await()
        }.getOrNull()
    } finally {
        cancellation.cancel()
    }

    return fresh?.toSearchLocationInput()
}

@SuppressLint("MissingPermission")
private fun newestFreshLastKnownLocation(locationManager: LocationManager): Location? =
    listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .filter { it.isFreshEnough() }
        .maxByOrNull { it.locationTimeMillis() }

private fun Location.isFreshEnough(): Boolean {
    val ageMillis = ageMillis()
    return ageMillis in 0..RECENT_LOCATION_MAX_AGE_MILLIS
}

private fun Location.ageMillis(): Long =
    if (elapsedRealtimeNanos > 0L) {
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L
    } else {
        System.currentTimeMillis() - time
    }

private fun Location.locationTimeMillis(): Long =
    if (elapsedRealtimeNanos > 0L) {
        SystemClock.elapsedRealtime() - ageMillis()
    } else {
        time
    }

private fun Location.toSearchLocationInput(): SearchLocationInput = SearchLocationInput(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy.takeIf { hasAccuracy() }?.toInt()?.coerceIn(0, 100000),
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
