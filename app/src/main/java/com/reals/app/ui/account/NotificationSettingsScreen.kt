package com.reals.app.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.NotificationPreferenceGroup
import com.reals.app.domain.model.NotificationPreferences
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.RealsPrimaryButton
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType

@Composable
fun NotificationSettingsScreen(
    loading: Boolean,
    preferences: NotificationPreferences?,
    saving: Boolean,
    loadError: ApiError?,
    saveError: ApiError?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPreferenceChange: (NotificationPreferenceGroup, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            NotificationSettingsHeader()
            when {
                preferences == null && loading -> NotificationSettingsLoading()
                preferences == null && loadError != null -> NotificationSettingsLoadError(
                    error = loadError,
                    loading = loading,
                    onRetry = onRetry,
                )

                preferences != null -> NotificationSettingsPreferences(
                    preferences = preferences,
                    saving = saving,
                    saveError = saveError,
                    onPreferenceChange = onPreferenceChange,
                )
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun NotificationSettingsHeader() {
    Column {
        Text(
            text = NotificationSettingsTitle,
            style = RealsType.ScreenTitle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = NotificationSettingsIntro,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RealsBrandDivider(
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun NotificationSettingsLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(NotificationPreferencesLoadingTag))
    }
}

@Composable
private fun NotificationSettingsLoadError(
    error: ApiError,
    loading: Boolean,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ApiErrorFeedbackCard(error, ErrorContext.Account)
        RealsPrimaryButton(
            text = "Reintentar",
            onClick = onRetry,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotificationSettingsPreferences(
    preferences: NotificationPreferences,
    saving: Boolean,
    saveError: ApiError?,
    onPreferenceChange: (NotificationPreferenceGroup, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        saveError?.let { ApiErrorFeedbackCard(it, ErrorContext.Account) }
        notificationPreferenceRows(preferences).forEach { row ->
            NotificationPreferenceRow(
                row = row,
                enabled = !saving,
                onPreferenceChange = onPreferenceChange,
            )
        }
    }
}

@Composable
private fun NotificationPreferenceRow(
    row: NotificationPreferenceRowModel,
    enabled: Boolean,
    onPreferenceChange: (NotificationPreferenceGroup, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .testTag(NotificationPreferenceRowTag)
            .toggleable(
                value = row.checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { checked -> onPreferenceChange(row.group, checked) },
            )
            .semantics { contentDescription = row.accessibleLabel },
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = row.checked,
                onCheckedChange = null,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .testTag(NotificationPreferenceSwitchTag),
            )
        }
    }
}

data class NotificationPreferenceRowModel(
    val group: NotificationPreferenceGroup,
    val title: String,
    val description: String,
    val checked: Boolean,
) {
    val accessibleLabel: String get() = "$title. $description"
}

fun notificationPreferenceRows(
    preferences: NotificationPreferences,
): List<NotificationPreferenceRowModel> = listOf(
    NotificationPreferenceRowModel(
        group = NotificationPreferenceGroup.Activity,
        title = "Actividad",
        description = "Nuevos chats y cambios importantes en tus interacciones.",
        checked = preferences.activityEnabled,
    ),
    NotificationPreferenceRowModel(
        group = NotificationPreferenceGroup.Reminders,
        title = "Recordatorios",
        description = "Avisos sobre revisiones pendientes y próximas segundas charlas.",
        checked = preferences.remindersEnabled,
    ),
    NotificationPreferenceRowModel(
        group = NotificationPreferenceGroup.Availability,
        title = "Disponibilidad",
        description = "Avisame cuando pueda volver a buscar a alguien nuevo.",
        checked = preferences.availabilityEnabled,
    ),
)

const val NotificationSettingsTitle = "Notificaciones"
const val NotificationSettingsIntro = "Elegí qué avisos querés recibir."
const val NotificationPreferenceRowTag = "notification_preference_row"
const val NotificationPreferenceSwitchTag = "notification_preference_switch"
const val NotificationPreferencesLoadingTag = "notification_preferences_loading"
