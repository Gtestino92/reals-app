package com.reals.app.domain.model

data class PermanentBanAppealState(
    val status: PermanentBanAppealStatus,
    val banActive: Boolean,
    val appealedAt: String?,
    val reviewedAt: String?,
)

sealed interface PermanentBanAppealStatus {
    val rawValue: String

    data object Available : PermanentBanAppealStatus {
        override val rawValue = "AVAILABLE"
    }

    data object Pending : PermanentBanAppealStatus {
        override val rawValue = "PENDING"
    }

    data object Approved : PermanentBanAppealStatus {
        override val rawValue = "APPROVED"
    }

    data object Rejected : PermanentBanAppealStatus {
        override val rawValue = "REJECTED"
    }

    data class Unknown(override val rawValue: String) : PermanentBanAppealStatus

    companion object {
        fun fromBackend(value: String): PermanentBanAppealStatus = when (value.uppercase()) {
            Available.rawValue -> Available
            Pending.rawValue -> Pending
            Approved.rawValue -> Approved
            Rejected.rawValue -> Rejected
            else -> Unknown(value)
        }
    }
}
