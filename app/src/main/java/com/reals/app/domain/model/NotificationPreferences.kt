package com.reals.app.domain.model

data class NotificationPreferences(
    val activityEnabled: Boolean,
    val remindersEnabled: Boolean,
    val availabilityEnabled: Boolean,
)

enum class NotificationPreferenceGroup {
    Activity,
    Reminders,
    Availability,
}

fun NotificationPreferences.withGroup(
    group: NotificationPreferenceGroup,
    enabled: Boolean,
): NotificationPreferences = when (group) {
    NotificationPreferenceGroup.Activity -> copy(activityEnabled = enabled)
    NotificationPreferenceGroup.Reminders -> copy(remindersEnabled = enabled)
    NotificationPreferenceGroup.Availability -> copy(availabilityEnabled = enabled)
}
