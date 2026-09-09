package com.reals.app.notifications

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class HomeRefreshSignal {
    private val channel = Channel<Unit>(capacity = Channel.CONFLATED)

    val requests: Flow<Unit> = channel.receiveAsFlow()

    fun request() {
        channel.trySend(Unit)
    }
}
