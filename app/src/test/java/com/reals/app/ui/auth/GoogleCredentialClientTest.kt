package com.reals.app.ui.auth

import android.os.Bundle
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCredentialClientTest {
    @Test
    fun `expected Google credential type returns id token`() {
        val result = extractGoogleIdToken(
            type = GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = Bundle(),
            parser = { "id-token" },
        )

        assertEquals(GoogleCredentialResult.Success("id-token"), result)
    }

    @Test
    fun `unexpected credential type fails`() {
        val result = extractGoogleIdToken(
            type = "unexpected",
            data = Bundle(),
            parser = { "id-token" },
        )

        assertEquals(GoogleCredentialResult.Failure, result)
    }

    @Test
    fun `malformed Google credential fails`() {
        val result = extractGoogleIdToken(
            type = GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = Bundle(),
        )

        assertEquals(GoogleCredentialResult.Failure, result)
    }

    @Test
    fun `missing or Android client id is not usable as server client id`() {
        assertEquals(false, isUsableGoogleServerClientId(""))
        assertEquals(false, isUsableGoogleServerClientId("android-client-id"))
        assertTrue(isUsableGoogleServerClientId("123.apps.googleusercontent.com"))
    }
}
