package com.reals.app.ui.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.NotificationPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadedScreenShowsThreeSwitchesWithBackendValuesAndNoSaveButton() {
        setScreen(
            preferences = NotificationPreferences(
                activityEnabled = false,
                remindersEnabled = true,
                availabilityEnabled = false,
            )
        )

        composeRule.onNodeWithText("Notificaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Elegí qué avisos querés recibir.").assertIsDisplayed()
        composeRule.onNodeWithText("Actividad").assertIsDisplayed()
        composeRule.onNodeWithText("Nuevos chats y cambios importantes en tus interacciones.").assertIsDisplayed()
        composeRule.onNodeWithText("Recordatorios").assertIsDisplayed()
        composeRule.onNodeWithText("Avisos sobre revisiones pendientes y próximas segundas charlas.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Disponibilidad").assertIsDisplayed()
        composeRule.onNodeWithText("Avisame cuando pueda volver a buscar a alguien nuevo.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NotificationPreferenceSwitchTag).assertCountEquals(3)
        composeRule.onAllNodesWithText("Guardar").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Actividad. Nuevos chats y cambios importantes en tus interacciones.")
            .assertIsOff()
        composeRule.onNodeWithContentDescription(
            "Recordatorios. Avisos sobre revisiones pendientes y próximas segundas charlas."
        ).assertIsOn()
        composeRule.onNodeWithContentDescription("Disponibilidad. Avisame cuando pueda volver a buscar a alguien nuevo.")
            .assertIsOff()
            .assertIsEnabled()
    }

    @Test
    fun savingDisablesAllRows() {
        setScreen(
            preferences = NotificationPreferences(
                activityEnabled = true,
                remindersEnabled = true,
                availabilityEnabled = true,
            ),
            saving = true,
        )

        composeRule.onAllNodesWithTag(NotificationPreferenceSwitchTag).assertCountEquals(3)
        composeRule.onNodeWithContentDescription("Actividad. Nuevos chats y cambios importantes en tus interacciones.")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            "Recordatorios. Avisos sobre revisiones pendientes y próximas segundas charlas."
        ).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Disponibilidad. Avisame cuando pueda volver a buscar a alguien nuevo.")
            .assertIsNotEnabled()
    }

    @Test
    fun loadErrorShowsRetryWithoutSwitches() {
        setScreen(
            preferences = null,
            loadError = ApiError.Unexpected("failed"),
        )

        composeRule.onNodeWithText("Reintentar").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NotificationPreferenceSwitchTag).assertCountEquals(0)
    }

    @Test
    fun screenDoesNotExposeAndroidChannelTerminology() {
        setScreen(
            preferences = NotificationPreferences(
                activityEnabled = true,
                remindersEnabled = true,
                availabilityEnabled = true,
            )
        )

        composeRule.onAllNodesWithText("Canal", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Channel", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    private fun setScreen(
        preferences: NotificationPreferences?,
        saving: Boolean = false,
        loadError: ApiError? = null,
    ) {
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsScreen(
                    loading = false,
                    preferences = preferences,
                    saving = saving,
                    loadError = loadError,
                    saveError = null,
                    onRetry = {},
                    onBack = {},
                    onPreferenceChange = { _, _ -> },
                )
            }
        }
    }
}
