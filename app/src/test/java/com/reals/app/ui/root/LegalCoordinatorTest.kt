package com.reals.app.ui.root

import com.reals.app.data.repository.LegalRepository
import com.reals.app.di.LegalFeatureDependencies
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.usecase.GetCurrentLegalDocumentsUseCase
import com.reals.app.domain.usecase.GetLegalStatusUseCase
import com.reals.app.domain.usecase.RecordLegalDocumentActionUseCase
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.TestDtos
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class LegalCoordinatorTest {
    private val api = FakeRealsApi()
    private val coordinator = LegalCoordinator(legalDependencies(api))

    @Test
    fun `already satisfied does not fetch current document metadata`() = runBlocking {
        api.legalStatusResponse = Response.success(TestDtos.legalStatus(requirementsSatisfied = true))

        val result = coordinator.load(TestDomain.session(), LegalResumeContext.PostSession)

        assertTrue(result is LegalCoordinatorResult.Satisfied)
        assertEquals(listOf("getMyLegalStatus"), api.calls)
    }

    @Test
    fun `unsatisfied status joins current documents in backend order`() = runBlocking {
        api.legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(
                    TestDtos.legalDocumentStatus(type = "PRIVACY_NOTICE", requiredAction = "ACKNOWLEDGED"),
                    TestDtos.legalDocumentStatus(type = "TERMS_OF_USE", requiredAction = "ACCEPTED"),
                ),
            )
        )
        api.currentLegalDocumentsResponse = Response.success(
            TestDtos.currentLegalDocuments(
                listOf(
                    TestDtos.currentLegalDocument(
                        type = "PRIVACY_NOTICE",
                        url = "https://example.test/privacy",
                        requiredAction = "ACKNOWLEDGED",
                    ),
                    TestDtos.currentLegalDocument(type = "TERMS_OF_USE"),
                )
            )
        )

        val state = (coordinator.load(
            TestDomain.session(),
            LegalResumeContext.PostSession,
        ) as LegalCoordinatorResult.Show).state

        assertEquals(listOf("getMyLegalStatus", "getCurrentLegalDocuments"), api.calls)
        assertEquals("PRIVACY_NOTICE", state.documents.first().type.rawValue)
        assertEquals("https://example.test/privacy", state.documents.first().url)
        assertEquals(LegalDocumentAction.Acknowledged, state.documents.first().requiredAction)
    }

    @Test
    fun `record partial completion remains in legal requirements`() = runBlocking {
        api.legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(TestDtos.legalDocumentStatus()),
            )
        )
        val state = (coordinator.load(
            TestDomain.session(),
            LegalResumeContext.PostSession,
        ) as LegalCoordinatorResult.Show).state

        val result = coordinator.recordRequiredAction(state, state.documents.single())

        assertTrue(result is LegalCoordinatorResult.Show)
        assertEquals("recordMyLegalDocumentAction", api.calls[2])
    }

    @Test
    fun `record final completion returns satisfied`() = runBlocking {
        api.legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(TestDtos.legalDocumentStatus()),
            )
        )
        val state = (coordinator.load(
            TestDomain.session(),
            LegalResumeContext.PostSession,
        ) as LegalCoordinatorResult.Show).state
        api.legalStatusResponse = Response.success(TestDtos.legalStatus(requirementsSatisfied = true))

        val result = coordinator.recordRequiredAction(state, state.documents.single())

        assertTrue(result is LegalCoordinatorResult.Satisfied)
    }

    @Test
    fun `stale document record error reloads requirements`() = runBlocking {
        api.legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(TestDtos.legalDocumentStatus()),
            )
        )
        val state = (coordinator.load(
            TestDomain.session(),
            LegalResumeContext.PostSession,
        ) as LegalCoordinatorResult.Show).state
        api.legalActionResponse = backendErrorResponse(409, "LEGAL_DOCUMENT_VERSION_NOT_CURRENT")

        val result = coordinator.recordRequiredAction(state, state.documents.single())

        assertTrue(result is LegalCoordinatorResult.Show)
        assertEquals("getMyLegalStatus", api.calls[3])
    }

    @Test
    fun `unknown required action is preserved`() = runBlocking {
        api.legalStatusResponse = Response.success(
            TestDtos.legalStatus(
                requirementsSatisfied = false,
                documents = listOf(TestDtos.legalDocumentStatus(requiredAction = "SIGNED")),
            )
        )
        api.currentLegalDocumentsResponse = Response.success(
            TestDtos.currentLegalDocuments(
                listOf(TestDtos.currentLegalDocument(requiredAction = "SIGNED"))
            )
        )

        val state = (coordinator.load(
            TestDomain.session(),
            LegalResumeContext.PostSession,
        ) as LegalCoordinatorResult.Show).state

        assertTrue(state.documents.single().requiredAction is LegalDocumentAction.Unknown)
        assertEquals("SIGNED", state.documents.single().requiredAction.rawValue)
    }
}

private fun legalDependencies(api: FakeRealsApi): LegalFeatureDependencies {
    val repository = LegalRepository(api, FakeAuthTokenProvider(), testApiExecutor())
    return LegalFeatureDependencies(
        getCurrentDocuments = GetCurrentLegalDocumentsUseCase(repository),
        getStatus = GetLegalStatusUseCase(repository),
        recordAction = RecordLegalDocumentActionUseCase(repository),
    )
}
