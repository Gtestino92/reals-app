package com.reals.app.ui.account

import com.reals.app.domain.model.NotificationPreferenceGroup
import com.reals.app.domain.model.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationSettingsPresentationTest {
    @Test
    fun `notification settings copy matches product text`() {
        val rows = notificationPreferenceRows(
            NotificationPreferences(
                activityEnabled = false,
                remindersEnabled = true,
                availabilityEnabled = false,
            )
        )

        assertEquals("Notificaciones", NotificationSettingsTitle)
        assertEquals("Elegí qué avisos querés recibir.", NotificationSettingsIntro)
        assertEquals(3, rows.size)
        assertEquals(NotificationPreferenceGroup.Activity, rows[0].group)
        assertEquals("Actividad", rows[0].title)
        assertEquals("Nuevos chats y cambios importantes en tus interacciones.", rows[0].description)
        assertEquals(false, rows[0].checked)
        assertEquals(NotificationPreferenceGroup.Reminders, rows[1].group)
        assertEquals("Recordatorios", rows[1].title)
        assertEquals("Avisos sobre revisiones pendientes y próximas segundas charlas.", rows[1].description)
        assertEquals(true, rows[1].checked)
        assertEquals(NotificationPreferenceGroup.Availability, rows[2].group)
        assertEquals("Disponibilidad", rows[2].title)
        assertEquals("Avisame cuando pueda volver a buscar a alguien nuevo.", rows[2].description)
        assertEquals(false, rows[2].checked)
    }

    @Test
    fun `notification settings do not expose android channel terminology`() {
        val visibleCopy = buildList {
            add(NotificationSettingsTitle)
            add(NotificationSettingsIntro)
            notificationPreferenceRows(
                NotificationPreferences(
                    activityEnabled = true,
                    remindersEnabled = true,
                    availabilityEnabled = true,
                )
            ).forEach { row ->
                add(row.title)
                add(row.description)
            }
        }.joinToString(" ")

        assertFalse(visibleCopy.contains("canal", ignoreCase = true))
        assertFalse(visibleCopy.contains("channel", ignoreCase = true))
    }
}
