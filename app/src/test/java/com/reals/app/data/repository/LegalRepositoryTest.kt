package com.reals.app.data.repository

import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.successValue
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class LegalRepositoryTest {
    private val api = FakeRealsApi()
    private val tokenProvider = FakeAuthTokenProvider()
    private val repository = LegalRepository(api, tokenProvider, testApiExecutor())

    @Test
    fun `getCurrentDocuments calls public endpoint without authorization`() = runBlocking {
        val documents = repository.getCurrentDocuments().successValue()

        assertEquals("getCurrentLegalDocuments", api.calls.single())
        assertEquals(null, api.lastAuthorization)
        assertEquals(LegalDocumentType.TermsOfUse, documents.single().type)
    }

    @Test
    fun `getStatus uses authenticated endpoint`() = runBlocking {
        api.legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(TestDtos.legalDocumentStatus()),
            )
        )

        val status = repository.getStatus().successValue()

        assertEquals("getMyLegalStatus", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals(false, status.requirementsSatisfied)
    }

    @Test
    fun `recordAction sends raw backend values and maps response`() = runBlocking {
        val record = repository.recordAction(
            documentType = LegalDocumentType.PrivacyNotice,
            documentVersion = "2026-07-01",
            action = LegalDocumentAction.Acknowledged,
        ).successValue()

        assertEquals("recordMyLegalDocumentAction", api.calls.single())
        assertEquals("Bearer test-token", api.lastAuthorization)
        assertEquals("PRIVACY_NOTICE", api.legalActionBody?.documentType)
        assertEquals("2026-07-01", api.legalActionBody?.documentVersion)
        assertEquals("ACKNOWLEDGED", api.legalActionBody?.action)
        assertEquals("action-1", record.id)
    }
}
