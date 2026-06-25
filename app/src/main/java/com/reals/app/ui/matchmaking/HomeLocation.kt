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

private const val TAG = "RealsLocation"

internal fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

internal suspend fun currentSearchLocation(context: Context): SearchLocationInput {
    if (!hasLocationPermission(context)) {
        Log.d(TAG, "location permission present=false")
        error("Falta permiso de ubicacion.")
    }
    Log.d(TAG, "location permission present=true")

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val provider = preferredProvider(locationManager)
    Log.d(TAG, "selected provider=$provider")
    if (provider == null) {
        error("Activa la ubicacion del dispositivo para buscar chat.")
    }

    val current = requestCurrentLocation(context, locationManager, provider)
    Log.d(TAG, "current provider location found=${current != null}")

    val location = current ?: newestLastKnownLocation(context, locationManager)
    Log.d(TAG, "last known fallback found=${current == null && location != null}")

    return location?.toSearchLocationInput()
        ?: error("No hay ubicacion disponible todavia. Intenta nuevamente en unos segundos.")
}

private fun preferredProvider(locationManager: LocationManager): String? {
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
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
