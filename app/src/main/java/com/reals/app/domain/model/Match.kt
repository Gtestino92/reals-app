package com.reals.app.domain.model

data class Match(
    val id: String,
    val userAId: String,
    val userBId: String,
    val state: MatchState,
    val connectionId: String?,
    val visualExpiresAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

sealed interface MatchState {
    val rawValue: String

    data object ChatActive : MatchState {
        override val rawValue = "CHAT_ACTIVE"
    }

    data object VisualPhase : MatchState {
        override val rawValue = "VISUAL_PHASE"
    }

    data object VisualApproved : MatchState {
        override val rawValue = "VISUAL_APPROVED"
    }

    data object ChatRejected : MatchState {
        override val rawValue = "CHAT_REJECTED"
    }

    data object VisualRejected : MatchState {
        override val rawValue = "VISUAL_REJECTED"
    }

    data object Expired : MatchState {
        override val rawValue = "EXPIRED"
    }

    data class Unknown(override val rawValue: String) : MatchState

    companion object {
        fun fromBackend(value: String): MatchState = when (value.uppercase()) {
            ChatActive.rawValue -> ChatActive
            VisualPhase.rawValue -> VisualPhase
            VisualApproved.rawValue -> VisualApproved
            ChatRejected.rawValue -> ChatRejected
            VisualRejected.rawValue -> VisualRejected
            Expired.rawValue -> Expired
            else -> Unknown(value)
        }
    }
}

enum class ChatContinueDecision(val backendValue: String) {
    Approved("APPROVED"),
    Rejected("REJECTED"),
}
