package com.reals.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginScreenPasswordResetTest {
    @Test
    fun `password reset cooldown rounds remaining seconds up`() {
        assertEquals(60L, passwordResetCooldownRemainingSeconds(60_000L, 1L))
        assertEquals(59L, passwordResetCooldownRemainingSeconds(60_000L, 1_001L))
        assertEquals(0L, passwordResetCooldownRemainingSeconds(60_000L, 60_000L))
        assertEquals(0L, passwordResetCooldownRemainingSeconds(null, 1_000L))
    }

    @Test
    fun `password reset button text reflects loading cooldown and normal states`() {
        assertEquals("Enviando...", passwordResetButtonText(loading = true, cooldownRemainingSeconds = 60L))
        assertEquals("Reenviar en 60s", passwordResetButtonText(loading = false, cooldownRemainingSeconds = 60L))
        assertEquals("Olvidé mi contraseña", passwordResetButtonText(loading = false, cooldownRemainingSeconds = 0L))
    }

    @Test
    fun `password reset button is disabled only for login loading reset loading or cooldown`() {
        assertTrue(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = true,
                googleLoading = false,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordResetLoading = true,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 60L,
            )
        )
    }
}
