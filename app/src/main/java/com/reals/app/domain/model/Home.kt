package com.reals.app.domain.model

data class HomeState(
    val profileStatus: ProfileStatus?,
    val queue: HomeQueueState,
    val activeMatches: List<HomeMatch>,
    val activeConnections: List<HomeConnection>,
)

data class HomeQueueState(
    val inQueue: Boolean,
)

data class HomeMatch(
    val matchId: String,
    val matchState: MatchState,
    val firstChat: HomeChat?,
)

data class HomeConnection(
    val connectionId: String,
    val matchId: String,
    val connectionState: ConnectionState,
    val secondChat: HomeChat?,
)

data class HomeChat(
    val chatId: String,
    val chatType: ChatType,
    val chatStatus: ChatStatus,
)

sealed interface ConnectionState {
    val rawValue: String

    data object SchedulingPhase : ConnectionState {
        override val rawValue = "SCHEDULING_PHASE"
    }

    data object SecondChatScheduled : ConnectionState {
        override val rawValue = "SECOND_CHAT_SCHEDULED"
    }

    data object SecondChatAvailable : ConnectionState {
        override val rawValue = "SECOND_CHAT_AVAILABLE"
    }

    data object SecondChat : ConnectionState {
        override val rawValue = "SECOND_CHAT"
    }

    data object Closed : ConnectionState {
        override val rawValue = "CLOSED"
    }

    data class Unknown(override val rawValue: String) : ConnectionState

    companion object {
        fun fromBackend(value: String): ConnectionState = when (value.uppercase()) {
            SchedulingPhase.rawValue -> SchedulingPhase
            SecondChatScheduled.rawValue -> SecondChatScheduled
            SecondChatAvailable.rawValue -> SecondChatAvailable
            SecondChat.rawValue -> SecondChat
            Closed.rawValue -> Closed
            else -> Unknown(value)
        }
    }
}
