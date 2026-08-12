package com.reals.app.domain.model

data class BackendUser(
    val id: String,
    val email: String?,
    val status: BackendUserStatus,
    val deletedAt: String?,
    val deletionFinalizesAt: String?,
    val passwordManagementAllowed: Boolean,
    val createdAt: String,
)

sealed interface BackendUserStatus {
    val rawValue: String

    data object Active : BackendUserStatus {
        override val rawValue: String = "ACTIVE"
    }

    data object Deleted : BackendUserStatus {
        override val rawValue: String = "DELETED"
    }

    data class Unknown(override val rawValue: String) : BackendUserStatus

    companion object {
        fun fromBackend(value: String): BackendUserStatus = when (value.uppercase()) {
            Active.rawValue -> Active
            Deleted.rawValue -> Deleted
            else -> Unknown(value)
        }
    }
}
