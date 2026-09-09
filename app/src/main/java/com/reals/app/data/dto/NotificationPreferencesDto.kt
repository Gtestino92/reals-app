package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationPreferencesResponseDto(
    val activityEnabled: Boolean,
    val remindersEnabled: Boolean,
    val availabilityEnabled: Boolean,
)

@Serializable
data class NotificationPreferencesRequestDto(
    val activityEnabled: Boolean,
    val remindersEnabled: Boolean,
    val availabilityEnabled: Boolean,
)
