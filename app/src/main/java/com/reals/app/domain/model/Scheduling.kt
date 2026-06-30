package com.reals.app.domain.model

data class SchedulingConnection(
    val id: String,
    val matchId: String,
    val userAId: String,
    val userBId: String,
    val state: ConnectionState,
    val schedulingExpiresAt: String,
    val createdAt: String,
    val updatedAt: String,
)

data class SchedulingNegotiation(
    val id: String,
    val connectionId: String,
    val roundNumber: Int,
    val status: NegotiationStatus,
    val confirmedDateTime: String?,
    val chatId: String?,
    val schedulingExpiresAt: String,
    val createdAt: String,
    val updatedAt: String,
)

data class SchedulingProposal(
    val id: String,
    val connectionId: String,
    val userId: String,
    val roundNumber: Int,
    val preferenceOrder: Int,
    val proposedDateTime: String,
    val status: ProposalStatus,
    val chatId: String?,
    val createdAt: String,
)

sealed interface ConnectionState {
    val rawValue: String

    data object SchedulingPending : ConnectionState {
        override val rawValue = "SCHEDULING_PENDING"
    }

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
            SchedulingPending.rawValue -> SchedulingPending
            SchedulingPhase.rawValue -> SchedulingPhase
            SecondChatScheduled.rawValue -> SecondChatScheduled
            SecondChatAvailable.rawValue -> SecondChatAvailable
            SecondChat.rawValue -> SecondChat
            Closed.rawValue -> Closed
            else -> Unknown(value)
        }
    }
}

sealed interface NegotiationStatus {
    val rawValue: String

    data object Pending : NegotiationStatus {
        override val rawValue = "PENDING"
    }

    data object Confirmed : NegotiationStatus {
        override val rawValue = "CONFIRMED"
    }

    data object Failed : NegotiationStatus {
        override val rawValue = "FAILED"
    }

    data class Unknown(override val rawValue: String) : NegotiationStatus

    companion object {
        fun fromBackend(value: String): NegotiationStatus = when (value.uppercase()) {
            Pending.rawValue -> Pending
            Confirmed.rawValue -> Confirmed
            Failed.rawValue -> Failed
            else -> Unknown(value)
        }
    }
}

sealed interface ProposalStatus {
    val rawValue: String

    data object Pending : ProposalStatus {
        override val rawValue = "PENDING"
    }

    data object Accepted : ProposalStatus {
        override val rawValue = "ACCEPTED"
    }

    data object Rejected : ProposalStatus {
        override val rawValue = "REJECTED"
    }

    data class Unknown(override val rawValue: String) : ProposalStatus

    companion object {
        fun fromBackend(value: String): ProposalStatus = when (value.uppercase()) {
            Pending.rawValue -> Pending
            Accepted.rawValue -> Accepted
            Rejected.rawValue -> Rejected
            else -> Unknown(value)
        }
    }
}
