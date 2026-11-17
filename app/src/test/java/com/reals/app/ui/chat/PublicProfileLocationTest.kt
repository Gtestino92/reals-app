package com.reals.app.ui.chat

import com.reals.app.data.dto.VisualProfileResponseDto
import com.reals.app.data.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PublicProfileLocationTest {
    @Test fun formatsDeclaredLocationInSpanish() {
        assertEquals("Buenos Aires, Argentina", publicProfileLocation(" Buenos Aires ", "ar"))
        assertEquals("Argentina", publicProfileLocation(null, "AR"))
        assertEquals("Rosario", publicProfileLocation("Rosario", "XX"))
        assertNull(publicProfileLocation(" ", null))
        assertNull(publicProfileLocation(null, "invalid"))
    }
    @Test fun additiveFieldsAndLegacyResponsesMap() {
        val legacy = """{"profileId":"p","displayName":"Ana","age":25,"photos":[]}"""
        val dto = Json.decodeFromString<VisualProfileResponseDto>(legacy)
        assertNull(dto.toDomain().city)
        assertNull(dto.toDomain().countryCode)
        val mapped = dto.copy(city = "Córdoba", countryCode = "AR").toDomain()
        assertEquals("Córdoba", mapped.city)
        assertEquals("AR", mapped.countryCode)
    }
}
