package com.reals.app.core.time

import android.os.SystemClock

fun interface ElapsedRealtimeClock {
    fun elapsedRealtimeMillis(): Long
}

object AndroidElapsedRealtimeClock : ElapsedRealtimeClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

data class ServerClockSnapshot(
    val serverTimeEpochMillis: Long,
    val receivedAtElapsedRealtimeMillis: Long,
) {
    fun estimatedServerTimeEpochMillis(elapsedRealtimeMillis: Long): Long =
        serverTimeEpochMillis + (elapsedRealtimeMillis - receivedAtElapsedRealtimeMillis).coerceAtLeast(0L)
}
