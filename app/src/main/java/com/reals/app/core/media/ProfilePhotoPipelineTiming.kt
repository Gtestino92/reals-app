package com.reals.app.core.media

import android.os.SystemClock
import android.util.Log
import com.reals.app.BuildConfig

internal object ProfilePhotoPipelineTiming {
    private const val Tag = "RealsPhotoPipeline"
    private val enabled: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.REALS_ENVIRONMENT != "prod"

    fun nowMillis(): Long = SystemClock.elapsedRealtime()

    fun log(fields: ProfilePhotoTimingFields) {
        if (!enabled) return
        Log.d(Tag, fields.format())
    }
}

internal data class ProfilePhotoTimingFields(
    val phase: String,
    val durationMs: Long? = null,
    val bytes: Long? = null,
    val fastPath: Boolean? = null,
) {
    fun format(): String = buildString {
        append("phase=")
        append(phase)
        durationMs?.let {
            append(" durationMs=")
            append(it.coerceAtLeast(0L))
        }
        bytes?.let {
            append(" bytes=")
            append(it.coerceAtLeast(0L))
        }
        fastPath?.let {
            append(" fastPath=")
            append(it)
        }
    }
}
