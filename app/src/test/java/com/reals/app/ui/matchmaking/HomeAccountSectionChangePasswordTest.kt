package com.reals.app.ui.matchmaking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAccountSectionChangePasswordTest {
    @Test
    fun `expanded account actions include change password when capability is available`() {
        assertEquals(
            listOf("Cerrar sesión", "Notificaciones", "Cambiar contraseña de Reals", "Eliminar cuenta"),
            expandedAccountActionLabels(canChangePassword = true),
        )
    }

    @Test
    fun `expanded account actions hide change password when capability is unavailable`() {
        assertEquals(
            listOf("Cerrar sesión", "Notificaciones", "Eliminar cuenta"),
            expandedAccountActionLabels(canChangePassword = false),
        )
    }

    @Test
    fun `change password validation rejects blank current password`() {
        assertEquals(
            "Ingresá tu contraseña actual.",
            changePasswordValidationError("", "new-password", "new-password"),
        )
    }

    @Test
    fun `change password validation rejects blank new password`() {
        assertEquals(
            "Ingresá una nueva contraseña.",
            changePasswordValidationError("current-password", "", "new-password"),
        )
    }

    @Test
    fun `change password validation rejects blank confirmation`() {
        assertEquals(
            "Repetí la nueva contraseña.",
            changePasswordValidationError("current-password", "new-password", ""),
        )
    }

    @Test
    fun `change password validation rejects mismatched confirmation`() {
        assertEquals(
            "Las contraseñas nuevas no coinciden.",
            changePasswordValidationError("current-password", "new-password", "other-password"),
        )
    }

    @Test
    fun `change password validation rejects short new password`() {
        assertEquals(
            "La nueva contraseña debe tener al menos 6 caracteres.",
            changePasswordValidationError("current-password", "short", "short"),
        )
    }

    @Test
    fun `change password validation rejects same current and new password`() {
        assertEquals(
            "La nueva contraseña debe ser distinta de la actual.",
            changePasswordValidationError("same-password", "same-password", "same-password"),
        )
    }

    @Test
    fun `change password validation accepts valid input`() {
        assertNull(
            changePasswordValidationError("current-password", "new-password", "new-password")
        )
    }
}
