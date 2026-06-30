package com.reals.app.domain.model

data class VisualProfile(
    val profileId: String,
    val displayName: String,
    val age: Int,
    val bio: String?,
    val photos: List<ProfilePhoto>,
    val visualExpiresAt: String?,
    val myPersonalMessageSubmitted: Boolean,
    val partnerPersonalMessageSubmitted: Boolean,
    val partnerPersonalMessageRead: Boolean,
    val decisionRequiresPartnerPersonalMessageRead: Boolean,
)

enum class VisualDecision(val backendValue: String) {
    Approved("APPROVED"),
    Rejected("REJECTED"),
}
