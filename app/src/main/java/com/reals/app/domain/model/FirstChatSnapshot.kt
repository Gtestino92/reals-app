package com.reals.app.domain.model

import com.reals.app.core.time.ServerClockSnapshot

data class FirstChatSnapshot(
    val chat: Chat,
    val serverTime: String,
    val serverClockSnapshot: ServerClockSnapshot,
)
