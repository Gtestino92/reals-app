package com.reals.app.ui.root

import com.reals.app.BuildConfig
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal object FirstChatSendTiming {
    private const val Tag = "RealsFirstChatSend"
    private val enabled: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.REALS_ENVIRONMENT != "prod"

    fun markNow(): TimeMark = TimeSource.Monotonic.markNow()

    fun logStage(stage: String, duration: Duration) {
        if (!enabled) return
        println("$Tag stage=$stage durationMs=${duration.inWholeMilliseconds.coerceAtLeast(0L)}")
    }
}
