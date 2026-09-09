package com.reals.app.data.mapper

import com.reals.app.data.dto.BanAppealResponseDto
import com.reals.app.domain.model.PermanentBanAppealStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BanAppealMappersTest {
    @Test
    fun `maps all known appeal statuses and ban activity`() {
        mapOf(
            "AVAILABLE" to PermanentBanAppealStatus.Available,
            "PENDING" to PermanentBanAppealStatus.Pending,
            "APPROVED" to PermanentBanAppealStatus.Approved,
            "REJECTED" to PermanentBanAppealStatus.Rejected,
        ).forEach { (raw, expected) ->
            val appeal = BanAppealResponseDto(
                status = raw,
                banActive = raw != "APPROVED",
                appealedAt = "2026-09-01T10:00:00Z",
                reviewedAt = "2026-09-02T10:00:00Z",
            ).toDomain()

            assertEquals(expected, appeal.status)
            assertEquals(raw != "APPROVED", appeal.banActive)
            assertEquals("2026-09-01T10:00:00Z", appeal.appealedAt)
            assertEquals("2026-09-02T10:00:00Z", appeal.reviewedAt)
        }
    }

    @Test
    fun `maps unknown status and null timestamps without crashing`() {
        val appeal = BanAppealResponseDto(
            status = "FUTURE_STATUS",
            banActive = true,
        ).toDomain()

        assertEquals(PermanentBanAppealStatus.Unknown("FUTURE_STATUS"), appeal.status)
        assertEquals(true, appeal.banActive)
        assertNull(appeal.appealedAt)
        assertNull(appeal.reviewedAt)
    }
}
