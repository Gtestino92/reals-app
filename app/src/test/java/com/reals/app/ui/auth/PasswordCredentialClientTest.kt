package com.reals.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordCredentialClientTest {
    @Test
    fun `password credential save trims email before creating request`() {
        assertEquals(
            "alex@example.com",
            passwordCredentialSaveEmailOrNull(" alex@example.com ", "secret-password"),
        )
    }

    @Test
    fun `password credential save rejects blank fields before Credential Manager`() {
        assertNull(passwordCredentialSaveEmailOrNull("", "secret-password"))
        assertNull(passwordCredentialSaveEmailOrNull("alex@example.com", ""))
    }
}
