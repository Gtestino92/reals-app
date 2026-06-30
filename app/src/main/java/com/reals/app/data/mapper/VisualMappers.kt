package com.reals.app.data.mapper

import com.reals.app.data.dto.VisualProfileResponseDto
import com.reals.app.domain.model.VisualProfile

fun VisualProfileResponseDto.toDomain(): VisualProfile = VisualProfile(
    profileId = profileId,
    displayName = displayName,
    age = age,
    bio = bio,
    photos = photos.map { it.toDomain() }.sortedBy { it.position },
    visualExpiresAt = visualExpiresAt,
    myPersonalMessageSubmitted = myPersonalMessageSubmitted,
    partnerPersonalMessageSubmitted = partnerPersonalMessageSubmitted,
    partnerPersonalMessageRead = partnerPersonalMessageRead,
    decisionRequiresPartnerPersonalMessageRead = decisionRequiresPartnerPersonalMessageRead
        ?: approvalRequiresPartnerPersonalMessageRead
        ?: false,
)
