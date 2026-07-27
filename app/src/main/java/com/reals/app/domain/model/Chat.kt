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
    val inactivityExpiresAt: String?,
    val partner: ChatPartner?,
    val myDecision: ChatDecisionState,
    val partnerDecision: ChatDecisionState,
    val endedReason: SecondChatEndedReason?,
    val endedAt: String?,
    val readOnlyUntil: String?,
    val lastMessageAt: String?,
    val guidance: FirstChatGuidance?,
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

data class SecondChatStatus(
    val connectionId: String,
    val chatId: String?,
    val scheduledAt: String,
    val onTimeUntil: String,
    val entryClosesAt: String,
    val absoluteExpiresAt: String,
    val conversationStartedAt: String?,
    val serverTime: String,
    val myAttendanceStatus: SecondChatAttendanceStatus,
    val myJoinedAt: String?,
    val partnerAttendanceStatus: SecondChatAttendanceStatus,
    val partnerJoinedAt: String?,
    val canJoin: Boolean,
    val canClaimPartnerNoShow: Boolean,
    val activeNoShowClaim: SecondChatResolutionRequest?,
    val activeResolutionRequest: SecondChatResolutionRequest?,
    val chatStatus: ChatStatus?,
    val endedReason: SecondChatEndedReason?,
    val endedAt: String?,
    val readOnlyUntil: String?,
    val mutualCompletionEligibleAt: String?,
    val canRequestMutualCompletion: Boolean,
    val mutualCompletionCooldownUntil: String?,
    val inactivityClaimableAt: String?,
    val inactivityClosesAt: String?,
    val canClaimPartnerInactivity: Boolean,
    val mustRespondToPartner: Boolean,
    val lastMessageAt: String?,
    val lastMessageSenderId: String?,
)

data class SecondChatResolutionRequest(
    val id: String,
    val type: SecondChatResolutionRequestType,
    val requesterUserId: String,
    val responderUserId: String,
    val referenceMessageId: String?,
    val status: SecondChatResolutionRequestStatus,
    val createdAt: String,
    val expiresAt: String,
)

data class FirstChatGuidanceQuestion(
    val id: String,
    val text: String,
)

data class FirstChatGuidance(
    val question: FirstChatGuidanceQuestion,
    val questionOrdinal: Int,
    val maxQuestions: Int,
    val requiredCharacters: Int,
    val canRequestNext: Boolean,
    val myNextRequested: Boolean,
    val completed: Boolean,
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

sealed interface SecondChatAttendanceStatus {
    val rawValue: String

    data object Pending : SecondChatAttendanceStatus { override val rawValue = "PENDING" }
    data object OnTime : SecondChatAttendanceStatus { override val rawValue = "ON_TIME" }
    data object Late : SecondChatAttendanceStatus { override val rawValue = "LATE" }
    data object NoShow : SecondChatAttendanceStatus { override val rawValue = "NO_SHOW" }
    data class Unknown(override val rawValue: String) : SecondChatAttendanceStatus

    companion object {
        fun fromBackend(value: String): SecondChatAttendanceStatus = when (value.uppercase()) {
            Pending.rawValue -> Pending
            OnTime.rawValue -> OnTime
            Late.rawValue -> Late
            NoShow.rawValue -> NoShow
            else -> Unknown(value)
        }
    }
}

sealed interface SecondChatResolutionRequestType {
    val rawValue: String

    data object PartnerNoShow : SecondChatResolutionRequestType { override val rawValue = "PARTNER_NO_SHOW" }
    data object MutualCompletion : SecondChatResolutionRequestType { override val rawValue = "MUTUAL_COMPLETION" }
    data object PartnerInactivity : SecondChatResolutionRequestType { override val rawValue = "PARTNER_INACTIVITY" }
    data class Unknown(override val rawValue: String) : SecondChatResolutionRequestType

    companion object {
        fun fromBackend(value: String): SecondChatResolutionRequestType = when (value.uppercase()) {
            PartnerNoShow.rawValue -> PartnerNoShow
            MutualCompletion.rawValue -> MutualCompletion
            PartnerInactivity.rawValue -> PartnerInactivity
            else -> Unknown(value)
        }
    }
}

sealed interface SecondChatResolutionRequestStatus {
    val rawValue: String

    data object Pending : SecondChatResolutionRequestStatus { override val rawValue = "PENDING" }
    data object Cancelled : SecondChatResolutionRequestStatus { override val rawValue = "CANCELLED" }
    data object Completed : SecondChatResolutionRequestStatus { override val rawValue = "COMPLETED" }
    data object Accepted : SecondChatResolutionRequestStatus { override val rawValue = "ACCEPTED" }
    data object Rejected : SecondChatResolutionRequestStatus { override val rawValue = "REJECTED" }
    data object TimedOut : SecondChatResolutionRequestStatus { override val rawValue = "TIMED_OUT" }
    data class Unknown(override val rawValue: String) : SecondChatResolutionRequestStatus

    companion object {
        fun fromBackend(value: String): SecondChatResolutionRequestStatus = when (value.uppercase()) {
            Pending.rawValue -> Pending
            Cancelled.rawValue -> Cancelled
            Completed.rawValue -> Completed
            Accepted.rawValue -> Accepted
            Rejected.rawValue -> Rejected
            TimedOut.rawValue -> TimedOut
            else -> Unknown(value)
        }
    }
}

sealed interface SecondChatCompletionDecision {
    val rawValue: String

    data object Accepted : SecondChatCompletionDecision { override val rawValue = "ACCEPTED" }
    data object Rejected : SecondChatCompletionDecision { override val rawValue = "REJECTED" }
}

sealed interface SecondChatEndedReason {
    val rawValue: String

    data object NoShow : SecondChatEndedReason { override val rawValue = "SECOND_CHAT_NO_SHOW" }
    data object MutualCompletion : SecondChatEndedReason { override val rawValue = "SECOND_CHAT_MUTUAL_COMPLETION" }
    data object PartnerInactivity : SecondChatEndedReason { override val rawValue = "SECOND_CHAT_PARTNER_INACTIVITY" }
    data object NoConversationStarted : SecondChatEndedReason {
        override val rawValue = "SECOND_CHAT_NO_CONVERSATION_STARTED"
    }
    data object AbsoluteTimeout : SecondChatEndedReason { override val rawValue = "ABSOLUTE_TIMEOUT" }
    data class Unknown(override val rawValue: String) : SecondChatEndedReason

    companion object {
        fun fromBackend(value: String?): SecondChatEndedReason? = when (value?.uppercase()) {
            null, "" -> null
            NoShow.rawValue -> NoShow
            MutualCompletion.rawValue -> MutualCompletion
            PartnerInactivity.rawValue -> PartnerInactivity
            NoConversationStarted.rawValue -> NoConversationStarted
            AbsoluteTimeout.rawValue -> AbsoluteTimeout
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

    data object TimedOut : ChatExitRequestStatus {
        override val rawValue = "TIMED_OUT"
    }

    data class Unknown(override val rawValue: String) : ChatExitRequestStatus

    companion object {
        fun fromBackend(value: String): ChatExitRequestStatus = when (value.uppercase()) {
            Pending.rawValue -> Pending
            Accepted.rawValue -> Accepted
            Rejected.rawValue -> Rejected
            TimedOut.rawValue -> TimedOut
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

    data object ChildSafetyConcern : ChatExitReason {
        override val rawValue = "CHILD_SAFETY_CONCERN"
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
            ChildSafetyConcern.rawValue -> ChildSafetyConcern
            Other.rawValue -> Other
            else -> Unknown(value)
        }
    }
}
