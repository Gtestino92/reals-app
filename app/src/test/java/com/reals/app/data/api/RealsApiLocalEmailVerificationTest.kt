package com.reals.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.POST

class RealsApiLocalEmailVerificationTest {
    @Test
    fun `local firebase email verification endpoint uses post path and no request body`() {
        val method = RealsApi::class.java.declaredMethods.single {
            it.name == "markCurrentFirebaseEmailVerifiedForLocalDevelopment"
        }

        val post = method.getAnnotation(POST::class.java)

        assertEquals("api/me/local-dev/email-verification", post!!.value)
        assertFalse(method.parameterAnnotations.flatten().any { it is Body })
    }
}
