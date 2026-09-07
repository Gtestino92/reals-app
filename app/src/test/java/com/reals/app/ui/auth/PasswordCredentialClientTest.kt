package com.reals.app.ui.auth

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCredentialClientTest {
    @Test
    fun `password credential returns success with trimmed email and password`() {
        val result = extractPasswordCredential(
            PasswordCredential(
                id = " alex@example.com ",
                password = "secret-password",
            )
        ) as PasswordCredentialResult.Success

        assertEquals("alex@example.com", result.email)
        assertEquals("secret-password", result.password)
        assertFalse(result.toString().contains("secret-password"))
    }

    @Test
    fun `non password credential returns unsupported`() {
        val result = extractPasswordCredential(CustomCredential("custom.type", Bundle()))

        assertEquals(PasswordCredentialResult.Unsupported, result)
    }

    @Test
    fun `empty password credential fields return unsupported`() {
        assertEquals(PasswordCredentialResult.Unsupported, passwordCredentialResult("", "secret-password"))
        assertEquals(PasswordCredentialResult.Unsupported, passwordCredentialResult("alex@example.com", ""))
    }

    @Test
    fun `only manual email password origin offers password save`() {
        assertTrue(shouldOfferPasswordCredentialSave(LoginCredentialOrigin.ManualEmailPassword))
        assertFalse(shouldOfferPasswordCredentialSave(LoginCredentialOrigin.SavedPasswordCredential))
        assertFalse(shouldOfferPasswordCredentialSave(LoginCredentialOrigin.Google))
    }
}
