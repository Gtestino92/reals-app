package com.reals.app.domain.model

sealed interface ProfileSnapshot {
    data object Missing : ProfileSnapshot
    data class Found(val profile: Profile) : ProfileSnapshot
}

data class ProvisionedSession(
    val user: BackendUser,
    val profileSnapshot: ProfileSnapshot,
    val authenticationSessionId: String = java.util.UUID.randomUUID().toString(),
)

data class ProfileOwnerKey(val authenticationSessionId: String, val userId: String, val profileId: String?)

fun ProvisionedSession.profileOwnerKey(): ProfileOwnerKey = ProfileOwnerKey(
    authenticationSessionId, user.id, (profileSnapshot as? ProfileSnapshot.Found)?.profile?.id,
)

fun ProvisionedSession.sameProfileOwner(other: ProvisionedSession?): Boolean =
    other != null && profileOwnerKey() == other.profileOwnerKey()
