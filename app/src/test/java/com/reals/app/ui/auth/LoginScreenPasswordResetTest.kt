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
    fun `google button text keeps continue copy and loading copy`() {
        assertEquals("Continuar con Google", googleSignInButtonText(googleLoading = false))
        assertEquals("Conectando con Google...", googleSignInButtonText(googleLoading = true))
    }

    @Test
    fun `saved credential button text reflects loading state`() {
        assertEquals("Usar credencial guardada", savedCredentialButtonText(passwordCredentialLoading = false))
        assertEquals("Buscando credenciales...", savedCredentialButtonText(passwordCredentialLoading = true))
    }

    @Test
    fun `password reset button is disabled only for auth loading reset loading or cooldown`() {
        assertTrue(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordCredentialLoading = false,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = true,
                googleLoading = false,
                passwordCredentialLoading = false,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordCredentialLoading = true,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordCredentialLoading = false,
                passwordResetLoading = true,
                cooldownRemainingSeconds = 0L,
            )
        )
        assertFalse(
            passwordResetButtonEnabled(
                loginLoading = false,
                googleLoading = false,
                passwordCredentialLoading = false,
                passwordResetLoading = false,
                cooldownRemainingSeconds = 60L,
            )
        )
    }
}
