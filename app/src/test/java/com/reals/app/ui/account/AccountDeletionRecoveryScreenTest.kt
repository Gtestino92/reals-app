package com.reals.app.ui.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDeletionRecoveryScreenTest {
    @Test
    fun `recovery actions are busy while reactivating or finalizing`() {
        assertEquals(false, accountDeletionRecoveryActionsBusy(reactivating = false, finalizingDeletion = false))
        assertEquals(true, accountDeletionRecoveryActionsBusy(reactivating = true, finalizingDeletion = false))
        assertEquals(true, accountDeletionRecoveryActionsBusy(reactivating = false, finalizingDeletion = true))
    }

    @Test
    fun `permanent deletion button text reflects finalization progress`() {
        assertEquals("Eliminar definitivamente ahora", permanentDeletionButtonText(false))
        assertEquals("Eliminando definitivamente...", permanentDeletionButtonText(true))
    }
}
