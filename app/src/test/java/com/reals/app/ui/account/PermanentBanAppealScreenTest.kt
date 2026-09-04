package com.reals.app.ui.account

import com.reals.app.domain.model.PermanentBanAppealState
import com.reals.app.domain.model.PermanentBanAppealStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentBanAppealScreenTest {
    @Test
    fun `available state enables nonblank submission only`() {
        val appeal = appeal(PermanentBanAppealStatus.Available, banActive = true)

        assertFalse(canSubmitPermanentBanAppeal(appeal, "   ", loading = false, submitting = false))
        assertTrue(canSubmitPermanentBanAppeal(appeal, "Solicito revisión", loading = false, submitting = false))
        assertFalse(canSubmitPermanentBanAppeal(appeal, "Solicito revisión", loading = true, submitting = false))
        assertFalse(canSubmitPermanentBanAppeal(appeal, "Solicito revisión", loading = false, submitting = true))
    }

    @Test
    fun `available state enforces maximum length`() {
        val appeal = appeal(PermanentBanAppealStatus.Available, banActive = true)

        assertTrue(canSubmitPermanentBanAppeal(appeal, "a".repeat(1000), loading = false, submitting = false))
        assertFalse(canSubmitPermanentBanAppeal(appeal, "a".repeat(1001), loading = false, submitting = false))
    }

    @Test
    fun `pending rejected approved and unknown states do not enable submission`() {
        listOf(
            appeal(PermanentBanAppealStatus.Pending, banActive = true),
            appeal(PermanentBanAppealStatus.Rejected, banActive = true),
            appeal(PermanentBanAppealStatus.Approved, banActive = false),
            appeal(PermanentBanAppealStatus.Unknown("FUTURE"), banActive = true),
        ).forEach {
            assertFalse(canSubmitPermanentBanAppeal(it, "Solicito revisión", loading = false, submitting = false))
        }
    }

    @Test
    fun `copy matches appeal status without exposing internal data`() {
        assertEquals(
            "Cuenta suspendida",
            permanentBanAppealTitle(appeal(PermanentBanAppealStatus.Available, true), false, null),
        )
        assertEquals(
            "Revisión solicitada",
            permanentBanAppealTitle(appeal(PermanentBanAppealStatus.Pending, true), false, null),
        )
        assertEquals(
            "Revisión finalizada",
            permanentBanAppealTitle(appeal(PermanentBanAppealStatus.Rejected, true), false, null),
        )
        assertTrue(
            permanentBanAppealBody(appeal(PermanentBanAppealStatus.Available, true), false, null)
                .contains("solicitar una revisión")
        )
    }

    private fun appeal(
        status: PermanentBanAppealStatus,
        banActive: Boolean,
    ) = PermanentBanAppealState(
        status = status,
        banActive = banActive,
        appealedAt = null,
        reviewedAt = null,
    )
}
