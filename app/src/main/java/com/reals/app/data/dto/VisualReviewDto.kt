package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class VisualProfileResponseDto(
    val profileId: String,
    val displayName: String,
    val age: Int,
    val bio: String? = null,
    val photos: List<PhotoResponseDto>,
    val myPersonalMessageSubmitted: Boolean = false,
    val partnerPersonalMessageSubmitted: Boolean = false,
    val partnerPersonalMessageRead: Boolean = true,
    val decisionRequiresPartnerPersonalMessageRead: Boolean? = null,
    val approvalRequiresPartnerPersonalMessageRead: Boolean? = null,
)

@Serializable
data class VisualDecisionRequestDto(
    val decision: String,
)

@Serializable
data class PersonalMessageRequestDto(
    val message: String,
)

@Serializable
data class PartnerPersonalMessageResponseDto(
    val message: String? = null,
)
