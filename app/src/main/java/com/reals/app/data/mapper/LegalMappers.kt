package com.reals.app.data.mapper

import com.reals.app.data.dto.CurrentLegalDocumentResponseDto
import com.reals.app.data.dto.LegalDocumentActionResponseDto
import com.reals.app.data.dto.LegalDocumentStatusResponseDto
import com.reals.app.data.dto.LegalStatusResponseDto
import com.reals.app.domain.model.CurrentLegalDocument
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentActionRecord
import com.reals.app.domain.model.LegalDocumentStatus
import com.reals.app.domain.model.LegalDocumentType
import com.reals.app.domain.model.LegalStatus

fun CurrentLegalDocumentResponseDto.toDomain(): CurrentLegalDocument = CurrentLegalDocument(
    type = LegalDocumentType.fromBackend(type),
    version = version,
    url = url,
    requiredAction = LegalDocumentAction.fromBackend(requiredAction),
)

fun LegalDocumentStatusResponseDto.toDomain(): LegalDocumentStatus = LegalDocumentStatus(
    type = LegalDocumentType.fromBackend(type),
    version = version,
    requiredAction = LegalDocumentAction.fromBackend(requiredAction),
    recordedAction = recordedAction?.let(LegalDocumentAction::fromBackend),
    actedAt = actedAt,
    satisfied = satisfied,
)

fun LegalStatusResponseDto.toDomain(): LegalStatus = LegalStatus(
    requirementsSatisfied = requirementsSatisfied,
    documents = documents.map { it.toDomain() },
)

fun LegalDocumentActionResponseDto.toDomain(): LegalDocumentActionRecord = LegalDocumentActionRecord(
    id = id,
    documentType = LegalDocumentType.fromBackend(documentType),
    documentVersion = documentVersion,
    action = LegalDocumentAction.fromBackend(action),
    actedAt = actedAt,
)
