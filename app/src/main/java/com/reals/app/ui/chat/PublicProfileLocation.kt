package com.reals.app.ui.chat

import com.reals.app.core.security.TextSafety
import java.util.Locale

/** Only declared public profile fields belong here; never search location or coordinates. */
internal fun publicProfileLocation(city: String?, countryCode: String?): String? {
    val code = countryCode?.trim()?.uppercase(Locale.ROOT)
    val country = code?.takeIf { it in Locale.getISOCountries() }
        ?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.forLanguageTag("es")) }
    return listOfNotNull(city?.trim()?.takeIf { it.isNotEmpty() }, country)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { TextSafety.safeDisplay(it, maxLength = 100) }
}
