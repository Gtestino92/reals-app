package com.reals.app.ui.auth

import com.reals.app.ui.root.LoginErrorOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginScreenPasswordResetTest {
    @Test
    fun `auth mode starts in sign in`() {
        assertEquals(AuthMode.SignIn, initialAuthMode())
    }

    @Test
    fun `sign in mode exposes sign in submit and account creation switch`() {
        assertEquals("Iniciar sesión", authModeHeading(AuthMode.SignIn))
        assertEquals("Ingresar", authPrimaryButtonText(AuthMode.SignIn, loading = false))
        assertEquals("¿No tenés cuenta? Crear cuenta", authModeSwitchActionText(AuthMode.SignIn))
    }

    @Test
    fun `sign up mode exposes account creation submit and sign in switch`() {
        assertEquals("Creá tu cuenta", authModeHeading(AuthMode.SignUp))
        assertEquals("Crear cuenta", authPrimaryButtonText(AuthMode.SignUp, loading = false))
        assertEquals("¿Ya tenés cuenta? Ingresar", authModeSwitchActionText(AuthMode.SignUp))
    }

    @Test
    fun `password reset is available only in sign in mode`() {
        assertTrue(authModeShowsPasswordReset(AuthMode.SignIn))
        assertFalse(authModeShowsPasswordReset(AuthMode.SignUp))
    }

    @Test
    fun `auth mode switch is blocked while auth is busy`() {
        assertEquals(AuthMode.SignUp, switchedAuthMode(AuthMode.SignIn, authBusy = false))
        assertEquals(AuthMode.SignIn, switchedAuthMode(AuthMode.SignUp, authBusy = false))
        assertEquals(AuthMode.SignIn, switchedAuthMode(AuthMode.SignIn, authBusy = true))
        assertEquals(AuthMode.SignUp, switchedAuthMode(AuthMode.SignUp, authBusy = true))
        assertFalse(authModeSwitchEnabled(authBusy = true))
    }

    @Test
    fun `back from sign up returns to sign in only when not busy`() {
        assertTrue(authModeBackHandlerEnabled(AuthMode.SignUp, authBusy = false))
        assertFalse(authModeBackHandlerEnabled(AuthMode.SignIn, authBusy = false))
        assertFalse(authModeBackHandlerEnabled(AuthMode.SignUp, authBusy = true))
        assertEquals(AuthMode.SignIn, authModeAfterBack(AuthMode.SignUp, authBusy = false))
        assertEquals(AuthMode.SignUp, authModeAfterBack(AuthMode.SignUp, authBusy = true))
    }

    @Test
    fun `auth error visibility follows owner mode`() {
        assertEquals(
            "Credenciales inválidas.",
            visibleAuthError(
                error = "Credenciales inválidas.",
                authMode = AuthMode.SignIn,
                errorOwner = LoginErrorOwner.SignIn,
            ),
        )
        assertEquals(
            null,
            visibleAuthError(
                error = "Credenciales inválidas.",
                authMode = AuthMode.SignUp,
                errorOwner = LoginErrorOwner.SignIn,
            ),
        )
        assertEquals(
            "No pudimos iniciar sesión con Google.",
            visibleAuthError(
                error = "No pudimos iniciar sesión con Google.",
                authMode = AuthMode.SignUp,
                errorOwner = LoginErrorOwner.Shared,
            ),
        )
        assertEquals(
            null,
            visibleAuthError(
                error = "Tu sesión terminó.",
                authMode = AuthMode.SignUp,
                errorOwner = null,
            ),
        )
    }

    @Test
    fun `submitting sign in mode invokes only sign in callback`() {
        var signInCount = 0
        var signUpCount = 0

        submitAuthMode(
            authMode = AuthMode.SignIn,
            email = "persona@example.com",
            password = "password",
            rememberCredentials = true,
            onSignIn = { email, password, rememberCredentials ->
                assertEquals("persona@example.com", email)
                assertEquals("password", password)
                assertTrue(rememberCredentials)
                signInCount++
            },
            onSignUp = { _, _, _ -> signUpCount++ },
        )

        assertEquals(1, signInCount)
        assertEquals(0, signUpCount)
    }

    @Test
    fun `submitting sign up mode invokes only sign up callback`() {
        var signInCount = 0
        var signUpCount = 0

        submitAuthMode(
            authMode = AuthMode.SignUp,
            email = "persona@example.com",
            password = "password",
            rememberCredentials = true,
            onSignIn = { _, _, _ -> signInCount++ },
            onSignUp = { email, password, rememberCredentials ->
                assertEquals("persona@example.com", email)
                assertEquals("password", password)
                assertTrue(rememberCredentials)
                signUpCount++
            },
        )

        assertEquals(0, signInCount)
        assertEquals(1, signUpCount)
    }

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
    fun `remember credentials starts unchecked and uses login label`() {
        assertFalse(defaultRememberCredentials())
        assertEquals("Recordar credenciales", rememberCredentialsLabel)
    }

    @Test
    fun `login header is hidden in compact keyboard layout`() {
        assertTrue(loginHeaderVisible(compactForIme = false))
        assertFalse(loginHeaderVisible(compactForIme = true))
    }

    @Test
    fun `login autofill content types are configured`() {
        assertEquals(loginEmailContentType(), loginEmailContentType())
        assertEquals(loginPasswordContentType(), loginPasswordContentType())
    }

    @Test
    fun `password reset button is disabled only for login google reset loading or cooldown`() {
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
                googleLoading = true,
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
