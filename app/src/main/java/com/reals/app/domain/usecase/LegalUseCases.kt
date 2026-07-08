package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.LegalRepository
import com.reals.app.domain.model.CurrentLegalDocument
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentActionRecord
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.model.LegalStatus

class GetCurrentLegalDocumentsUseCase(
    private val legalRepository: LegalRepository,
) {
    suspend operator fun invoke(): ApiResult<List<CurrentLegalDocument>> =
        legalRepository.getCurrentDocuments()
}

class GetLegalStatusUseCase(
    private val legalRepository: LegalRepository,
) {
    suspend operator fun invoke(): ApiResult<LegalStatus> =
        legalRepository.getStatus()
}

class RecordLegalDocumentActionUseCase(
    private val legalRepository: LegalRepository,
) {
    suspend operator fun invoke(
        documentType: LegalDocumentType,
        documentVersion: String,
        action: LegalDocumentAction,
    ): ApiResult<LegalDocumentActionRecord> =
        legalRepository.recordAction(documentType, documentVersion, action)
}
