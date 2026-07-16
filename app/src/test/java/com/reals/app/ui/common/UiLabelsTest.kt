package com.reals.app.ui.common

import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatExitRequestType
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.ProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLabelsTest {
    @Test
    fun `labels cover known and unknown profile status`() {
        assertEquals("Activo", ProfileStatus.Active.userLabel())
        assertEquals("Borrador", ProfileStatus.Draft.userLabel())
        assertEquals("Pausado", ProfileStatus.Inactive.userLabel())
        assertEquals("Estado no disponible", ProfileStatus.Unknown("NEW").userLabel())
    }

    @Test
    fun `labels cover match and chat states`() {
        assertEquals("Chat en curso", MatchState.ChatActive.userLabel())
        assertEquals("Revisión visual", MatchState.VisualPhase.userLabel())
        assertEquals("Revisión aprobada", MatchState.VisualApproved.userLabel())
        assertEquals("Chat cerrado", MatchState.ChatRejected.userLabel())
        assertEquals("Revisión cerrada", MatchState.VisualRejected.userLabel())
        assertEquals("Expirado", MatchState.Expired.userLabel())
        assertEquals("Estado no disponible", MatchState.Unknown("NEW").userLabel())

        assertEquals("En curso", ChatStatus.Active.userLabel())
        assertEquals("Cancelado", ChatStatus.Cancelled.userLabel())
        assertEquals("Vencida", ChatExitRequestStatus.TimedOut.userLabel())
    }

    @Test
    fun `labels cover decisions exit types and reasons`() {
        assertEquals("Pendiente", ChatDecisionState.Pending.userLabel())
        assertEquals("Aprobado", ChatDecisionState.Approved.userLabel())
        assertEquals("Rechazado", ChatDecisionState.Rejected.userLabel())
        assertEquals("Cancelacion propuesta", ChatExitRequestType.MutualCancel.userLabel())
        assertEquals("Reporte de seguridad", ChatExitRequestType.SafetyReport.userLabel())
        assertEquals("Ya no hay interés", ChatExitReason.NoLongerInterested.userLabel())
        assertEquals("Comportamiento inapropiado", ChatExitReason.InappropriateBehavior.userLabel())
        assertEquals("Acoso", ChatExitReason.Harassment.userLabel())
        assertEquals("Seguridad de menores", ChatExitReason.ChildSafetyConcern.userLabel())
        assertEquals("Motivo no disponible", ChatExitReason.Unknown("NEW").userLabel())
    }

    @Test
    fun `photoValidationLabel covers backend variations`() {
        assertEquals("Aprobada", photoValidationLabel("VALIDATED"))
        assertEquals("Aprobada", photoValidationLabel("APPROVED"))
        assertEquals("En revisión", photoValidationLabel("PENDING_VALIDATION"))
        assertEquals("Necesita cambios", photoValidationLabel("REJECTED"))
        assertEquals("En revisión", photoValidationLabel("SOMETHING_NEW"))
    }
}
