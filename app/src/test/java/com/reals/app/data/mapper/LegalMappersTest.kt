package com.reals.app.data.mapper

import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.testutil.TestDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalMappersTest {
    @Test
    fun `current document response maps known values`() {
        val document = TestDtos.currentLegalDocument().toDomain()

        assertEquals(LegalDocumentType.TermsOfUse, document.type)
        assertEquals("2026-07-01", document.version)
        assertEquals("https://example.test/terms", document.url)
        assertEquals(LegalDocumentAction.Accepted, document.requiredAction)
    }

    @Test
    fun `legal status response preserves nullable action fields`() {
        val status = TestDtos.legalStatus(
            requirementsSatisfied = false,
            documents = listOf(TestDtos.legalDocumentStatus(recordedAction = null, actedAt = null)),
        ).toDomain()

        assertEquals(false, status.requirementsSatisfied)
        assertEquals(null, status.documents.single().recordedAction)
        assertEquals(null, status.documents.single().actedAt)
    }

    @Test
    fun `action response maps known values`() {
        val action = TestDtos.legalAction(action = "ACKNOWLEDGED").toDomain()

        assertEquals("action-1", action.id)
        assertEquals(LegalDocumentType.TermsOfUse, action.documentType)
        assertEquals(LegalDocumentAction.Acknowledged, action.action)
        assertEquals(TestDtos.now, action.actedAt)
    }

    @Test
    fun `unknown document type and action are preserved`() {
        val document = TestDtos.currentLegalDocument(
            type = "FUTURE_DOCUMENT",
            requiredAction = "SIGNED",
        ).toDomain()

        assertTrue(document.type is LegalDocumentType.Unknown)
        assertEquals("FUTURE_DOCUMENT", document.type.rawValue)
        assertTrue(document.requiredAction is LegalDocumentAction.Unknown)
        assertEquals("SIGNED", document.requiredAction.rawValue)
    }
}
