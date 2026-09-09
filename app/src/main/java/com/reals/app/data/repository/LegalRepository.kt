package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.RecordLegalDocumentActionRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.CurrentLegalDocument
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentActionRecord
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.model.LegalStatus

class LegalRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    private val apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getCurrentDocuments(): ApiResult<List<CurrentLegalDocument>> =
        apiExecutor.execute { api.getCurrentLegalDocuments() }
            .map { response -> response.documents.map { it.toDomain() } }

    suspend fun getStatus(): ApiResult<LegalStatus> =
        authorizedCall { authorization -> api.getMyLegalStatus(authorization) }
            .map { it.toDomain() }

    suspend fun recordAction(
        documentType: LegalDocumentType,
        documentVersion: String,
        action: LegalDocumentAction,
    ): ApiResult<LegalDocumentActionRecord> =
        authorizedCall { authorization ->
            api.recordMyLegalDocumentAction(
                authorization = authorization,
                body = RecordLegalDocumentActionRequestDto(
                    documentType = documentType.rawValue,
                    documentVersion = documentVersion,
                    action = action.rawValue,
                ),
            )
        }.map { it.toDomain() }
}
