package com.reals.app.domain.model

sealed interface ProfileSnapshot {
    data object Missing : ProfileSnapshot
    data class Found(val profile: Profile) : ProfileSnapshot
}

data class ProvisionedSession(
    val user: BackendUser,
    val profileSnapshot: ProfileSnapshot,
)
