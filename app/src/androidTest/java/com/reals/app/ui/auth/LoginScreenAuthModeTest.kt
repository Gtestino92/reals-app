package com.reals.app.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenAuthModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialModeIsSignIn() {
        setLoginScreen()

        composeRule.onNodeWithText("Iniciar sesión").assertIsDisplayed()
        composeRule.onNodeWithText("Ingresar").assertIsDisplayed()
        composeRule.onNodeWithText("¿No tenés cuenta? Crear cuenta").assertIsDisplayed()
        composeRule.onNodeWithText("Olvidé mi contraseña").assertIsDisplayed()
        composeRule.onNodeWithText("Continuar con Google").assertIsDisplayed()
        composeRule.onAllNodesWithText("Crear cuenta").assertCountEquals(0)
    }

    @Test
    fun signUpModeShowsAccountCreationAndReturnToSignIn() {
        setLoginScreen()

        composeRule.onNodeWithText("¿No tenés cuenta? Crear cuenta").performClick()

        composeRule.onNodeWithText("Creá tu cuenta").assertIsDisplayed()
        composeRule.onNodeWithText("Crear cuenta").assertIsDisplayed()
        composeRule.onNodeWithText("¿Ya tenés cuenta? Ingresar").assertIsDisplayed()
        composeRule.onAllNodesWithText("Olvidé mi contraseña").assertCountEquals(0)
        composeRule.onNodeWithText("Continuar con Google").assertIsDisplayed()
    }

    @Test
    fun switchingModesDoesNotSubmit() {
        var signInCount = 0
        var signUpCount = 0
        setLoginScreen(
            onSignIn = { _, _, _ -> signInCount++ },
            onSignUp = { _, _, _ -> signUpCount++ },
        )

        composeRule.onNodeWithText("¿No tenés cuenta? Crear cuenta").performClick()
        composeRule.onNodeWithText("¿Ya tenés cuenta? Ingresar").performClick()

        assertEquals(0, signInCount)
        assertEquals(0, signUpCount)
    }

    @Test
    fun signInSubmitInvokesOnlySignIn() {
        var signInCount = 0
        var signUpCount = 0
        setLoginScreen(
            onSignIn = { email, password, rememberCredentials ->
                assertEquals("persona@example.com", email)
                assertEquals("password", password)
                assertEquals(false, rememberCredentials)
                signInCount++
            },
            onSignUp = { _, _, _ -> signUpCount++ },
        )

        composeRule.onNodeWithTag(LoginEmailFieldTag).performTextInput("persona@example.com")
        composeRule.onNodeWithTag(LoginPasswordFieldTag).performTextInput("password")
        composeRule.onNodeWithText("Ingresar").performClick()

        assertEquals(1, signInCount)
        assertEquals(0, signUpCount)
    }

    @Test
    fun signUpSubmitInvokesOnlySignUp() {
        var signInCount = 0
        var signUpCount = 0
        setLoginScreen(
            onSignIn = { _, _, _ -> signInCount++ },
            onSignUp = { email, password, rememberCredentials ->
                assertEquals("persona@example.com", email)
                assertEquals("password", password)
                assertEquals(false, rememberCredentials)
                signUpCount++
            },
        )

        composeRule.onNodeWithText("¿No tenés cuenta? Crear cuenta").performClick()
        composeRule.onNodeWithTag(LoginEmailFieldTag).performTextInput("persona@example.com")
        composeRule.onNodeWithTag(LoginPasswordFieldTag).performTextInput("password")
        composeRule.onNodeWithText("Crear cuenta").performClick()

        assertEquals(0, signInCount)
        assertEquals(1, signUpCount)
    }

    @Test
    fun emailIsPreservedAcrossModeChanges() {
        setLoginScreen()

        composeRule.onNodeWithTag(LoginEmailFieldTag).performTextInput("persona@example.com")
        composeRule.onNodeWithText("¿No tenés cuenta? Crear cuenta").performClick()

        composeRule.onNodeWithTag(LoginEmailFieldTag).assertTextContains("persona@example.com")
    }

    private fun setLoginScreen(
        loading: Boolean = false,
        googleLoading: Boolean = false,
        passwordResetLoading: Boolean = false,
        onSignIn: (email: String, password: String, rememberCredentials: Boolean) -> Unit = { _, _, _ -> },
        onSignUp: (email: String, password: String, rememberCredentials: Boolean) -> Unit = { _, _, _ -> },
    ) {
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    loading = loading,
                    googleLoading = googleLoading,
                    error = null,
                    passwordResetLoading = passwordResetLoading,
                    passwordResetMessage = null,
                    passwordResetAvailableAtMillis = null,
                    onSignIn = onSignIn,
                    onSignUp = onSignUp,
                    onPasswordReset = {},
                    onGoogleSignIn = {},
                )
            }
        }
    }
}
