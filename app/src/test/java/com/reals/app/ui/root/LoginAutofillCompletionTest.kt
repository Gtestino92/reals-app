package com.reals.app.ui.root

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginAutofillCompletionTest {
    @Test
    fun `remember credentials true maps to autofill commit`() {
        assertEquals(
            LoginAutofillCompletionAction.Commit,
            loginAutofillCompletionAction(rememberCredentials = true),
        )
    }

    @Test
    fun `remember credentials false maps to autofill cancel`() {
        assertEquals(
            LoginAutofillCompletionAction.Cancel,
            loginAutofillCompletionAction(rememberCredentials = false),
        )
    }
}
