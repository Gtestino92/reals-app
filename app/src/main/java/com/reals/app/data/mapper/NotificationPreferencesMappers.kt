package com.reals.app.data.mapper

import com.reals.app.data.dto.NotificationPreferencesRequestDto
import com.reals.app.data.dto.NotificationPreferencesResponseDto
import com.reals.app.domain.model.NotificationPreferences

fun NotificationPreferencesResponseDto.toDomain(): NotificationPreferences = NotificationPreferences(
    activityEnabled = activityEnabled,
    remindersEnabled = remindersEnabled,
    availabilityEnabled = availabilityEnabled,
)

fun NotificationPreferences.toRequestDto(): NotificationPreferencesRequestDto =
    NotificationPreferencesRequestDto(
        activityEnabled = activityEnabled,
        remindersEnabled = remindersEnabled,
        availabilityEnabled = availabilityEnabled,
    )
