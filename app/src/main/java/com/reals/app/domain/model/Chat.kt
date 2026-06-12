package com.reals.app.domain.model

data class Chat(
    val id: String,
    val matchId: String,
    val connectionId: String?,
    val chatType: ChatType,
    val status: ChatStatus,
    val startedAt: String,
    val availableAt: String?,
    val activatedAt: String?,
    val timeoutAt: String,
    val expiresAt: String,
    val partner: ChatPartner?,
    val myDecision: ChatDecisionState,
    val partnerDecision: ChatDecisionState,
    val endedAt: String?,
    val lastMessageAt: String?,
)

data class ChatPartner(
    val userId: String,
    val profileId: String,
    val displayName: String,
)

data class ChatMessage(
    val id: String,
    val chatSessionId: String,
    val senderId: String,
    val content: String,
    val sentAt: String,
)

data class ChatExitRequest(
    val id: String,
    val chatId: String,
    val requesterUserId: String,
    val responderUserId: String,
    val type: ChatExitRequestType,
    val status: ChatExitRequestStatus,
    val reason: ChatExitReason?,
    val details: String?,
    val createdAt: String,
    val resolvedAt: String?,
)

data class ChatExitOutcome(
    val chat: Chat,
    val exitRequest: ChatExitRequest,
    val penaltyApplied: Boolean,
    val penalizedUserId: String?,
)

sealed interface ChatType {
    val rawValue: String

    data object FirstChat : ChatType {
        override val rawValue = "FIRST_CHAT"
    }

    data object SecondChat : ChatType {
        override val rawValue = "SECOND_CHAT"
    }

    data class Unknown(override val rawValue: String) : ChatType

    companion object {
        fun fromBackend(value: String): ChatType = when (value.uppercase()) {
            FirstChat.rawValue -> FirstChat
            SecondChat.rawValue -> SecondChat
            else -> Unknown(value)
        }
    }
}

sealed interface ChatDecisionState {
    val rawValue: String

    data object Pending : ChatDecisionState {
        override val rawValue = "PENDING"
    }

    data object Approved : ChatDecisionState {
        override val rawValue = "APPROVED"
    }

    data object Rejected : ChatDecisionState {
        override val rawValue = "REJECTED"
    }

    data object Abandoned : ChatDecisionState {
        override val rawValue = "ABANDONED"
    }

    data class Unknown(override val rawValue: String) : ChatDecisionState

    companion object {
        fun fromBackend(value: String?): ChatDecisionState = when (value?.uppercase()) {
            null,
            "",
            Pending.rawValue -> Pending
            Approved.rawValue -> Approved
            Rejected.rawValue -> Rejected
            Abandoned.rawValue -> Abandoned
            else -> Unknown(value)
        }
    }
}

sealed interface ChatStatus {
    val rawValue: String

    data object Available : ChatStatus {
        override val rawValue = "AVAILABLE"
    }

    data object Active : ChatStatus {
        override val rawValue = "ACTIVE"
    }

    data object Cancelled : ChatStatus {
        override val rawValue = "CANCELLED"
    }

    data object Expired : ChatStatus {
        override val rawValue = "EXPIRED"
    }

    data object Abandoned : ChatStatus {
        override val rawValue = "ABANDONED"
    }

    data object Closed : ChatStatus {
        override val rawValue = "CLOSED"
    }

    data object Finished : ChatStatus {
        override val rawValue = "FINISHED"
    }

    data class Unknown(override val rawValue: String) : ChatStatus

    companion object {
        fun fromBackend(value: String): ChatStatus = when (value.uppercase()) {
            Available.rawValue -> Available
            Active.rawValue -> Active
            Cancelled.rawValue -> Cancelled
            Expired.rawValue -> Expired
            Abandoned.rawValue -> Abandoned
            Closed.rawValue -> Closed
            Finished.rawValue -> Finished
            else -> Unknown(value)
        }
    }
}

sealed interface ChatExitRequestType {
    val rawValue: String

    data object MutualCancel : ChatExitRequestType {
        override val rawValue = "MUTUAL_CANCEL"
    }

    data object UnilateralCancel : ChatExitRequestType {
        override val rawValue = "UNILATERAL_CANCEL"
    }

    data object SafetyReport : ChatExitRequestType {
        override val rawValue = "SAFETY_REPORT"
    }

    data class Unknown(override val rawValue: String) : ChatExitRequestType

    companion object {
        fun fromBackend(value: String): ChatExitRequestType = when (value.uppercase()) {
            MutualCancel.rawValue -> MutualCancel
            UnilateralCancel.rawValue -> UnilateralCancel
            SafetyReport.rawValue -> SafetyReport
            else -> Unknown(value)
        }
    }
}

sealed interface ChatExitRequestStatus {
    val rawValue: String

    data object Pending : ChatExitRequestStatus {
        override val rawValue = "PENDING"
    }

    data object Accepted : ChatExitRequestStatus {
        override val rawValue = "ACCEPTED"
    }

    data object Rejected : ChatExitRequestStatus {
        override val rawValue = "REJECTED"
    }

    data class Unknown(override val rawValue: String) : ChatExitRequestStatus

    companion object {
        fun fromBackend(value: String): ChatExitRequestStatus = when (value.uppercase()) {
            Pending.rawValue -> Pending
            Accepted.rawValue -> Accepted
            Rejected.rawValue -> Rejected
            else -> Unknown(value)
        }
    }
}

sealed interface ChatExitReason {
    val rawValue: String

    data object NoLongerInterested : ChatExitReason {
        override val rawValue = "NO_LONGER_INTERESTED"
    }

    data object InappropriateBehavior : ChatExitReason {
        override val rawValue = "INAPPROPRIATE_BEHAVIOR"
    }

    data object Harassment : ChatExitReason {
        override val rawValue = "HARASSMENT"
    }

    data object Other : ChatExitReason {
        override val rawValue = "OTHER"
    }

    data class Unknown(override val rawValue: String) : ChatExitReason

    companion object {
        fun fromBackend(value: String): ChatExitReason = when (value.uppercase()) {
            NoLongerInterested.rawValue -> NoLongerInterested
            InappropriateBehavior.rawValue -> InappropriateBehavior
            Harassment.rawValue -> Harassment
            Other.rawValue -> Other
            else -> Unknown(value)
        }
    }
}
