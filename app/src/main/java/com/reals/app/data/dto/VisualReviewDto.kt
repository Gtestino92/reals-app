package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class VisualProfileResponseDto(
    val profileId: String,
    val displayName: String,
    val age: Int,
    val bio: String? = null,
    val photos: List<PhotoResponseDto>,
    val visualExpiresAt: String? = null,
    val myPersonalMessageSubmitted: Boolean = false,
    val partnerPersonalMessageSubmitted: Boolean = false,
    val partnerPersonalMessageRead: Boolean = true,
    val decisionRequiresPartnerPersonalMessageRead: Boolean? = null,
    val approvalRequiresPartnerPersonalMessageRead: Boolean? = null,
    val affinityIndicators: List<VisualAffinityIndicatorResponseDto> = emptyList(),
    val profileQuestions: List<PublicProfileQuestionResponseDto> = emptyList(),
)

@Serializable
data class VisualAffinityIndicatorResponseDto(
    val categoryId: String,
    val title: String,
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
