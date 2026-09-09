package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

internal const val ProfileMinAge = 18
internal const val ProfileMaxAge = 99
internal const val ProfileMinDistanceKm = 1
internal const val ProfileMaxDistanceKm = 100

@Composable
internal fun AgeRangePreferenceControl(
    minAge: Int,
    maxAge: Int,
    enabled: Boolean,
    onAgeRangeChange: (minAge: Int, maxAge: Int) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = "Edad: $minAge - $maxAge años",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error == null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        RangeSlider(
            value = minAge.toFloat()..maxAge.toFloat(),
            onValueChange = { range ->
                val nextMin = range.start.roundToInt().coerceIn(ProfileMinAge, ProfileMaxAge)
                val nextMax = range.endInclusive.roundToInt().coerceIn(nextMin, ProfileMaxAge)
                onAgeRangeChange(nextMin, nextMax)
            },
            valueRange = ProfileMinAge.toFloat()..ProfileMaxAge.toFloat(),
            steps = 0,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun DistancePreferenceControl(
    distanceKm: Int,
    enabled: Boolean,
    onDistanceChange: (distanceKm: Int) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = "Distancia máxima: $distanceKm km",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error == null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Slider(
            value = distanceKm.toFloat(),
            onValueChange = { value ->
                onDistanceChange(value.roundToInt().coerceIn(ProfileMinDistanceKm, ProfileMaxDistanceKm))
            },
            valueRange = ProfileMinDistanceKm.toFloat()..ProfileMaxDistanceKm.toFloat(),
            steps = 0,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
