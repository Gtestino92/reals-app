package com.reals.app.ui.account

import com.reals.app.ui.root.AccountSuspension
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSuspendedScreenTest {
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")
    private val locale = Locale.forLanguageTag("es-AR")

    @Test
    fun `temporary suspension formats backend expiry in local timezone`() {
        assertEquals(
            "1 de septiembre a las 22:30",
            formatAccountSuspensionExpiresAt("2026-09-02T01:30:00Z", buenosAires, locale),
        )
    }

    @Test
    fun `temporary suspension body uses formatted local expiry`() {
        val body = accountSuspensionBody(
            AccountSuspension.Temporary("2026-09-02T01:30:00Z"),
            buenosAires,
            locale,
        )

        assertTrue(body.contains("hasta el 1 de septiembre a las 22:30"))
        assertTrue(body.contains("Podrás volver a usar Reals"))
    }

    @Test
    fun `temporary suspension body falls back when expiry is missing or malformed`() {
        assertNull(formatAccountSuspensionExpiresAt(null, buenosAires, locale))
        assertNull(formatAccountSuspensionExpiresAt("not-a-date", buenosAires, locale))
        assertEquals(
            "Tu cuenta está suspendida temporalmente. Podrás volver a entrar cuando termine la suspensión.",
            accountSuspensionBody(AccountSuspension.Temporary("not-a-date"), buenosAires, locale),
        )
    }

    @Test
    fun `permanent suspension copy does not expose expiry`() {
        assertEquals("Cuenta suspendida", accountSuspensionTitle(AccountSuspension.Permanent))
        assertEquals(
            "Tu cuenta fue suspendida permanentemente.",
            accountSuspensionBody(AccountSuspension.Permanent, buenosAires, locale),
        )
    }
}
