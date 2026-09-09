package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CurrentLegalDocumentsResponseDto(
    val documents: List<CurrentLegalDocumentResponseDto>,
)

@Serializable
data class CurrentLegalDocumentResponseDto(
    val type: String,
    val version: String,
    val url: String,
    val requiredAction: String,
)

@Serializable
data class LegalStatusResponseDto(
    val requirementsSatisfied: Boolean,
    val documents: List<LegalDocumentStatusResponseDto>,
)

@Serializable
data class LegalDocumentStatusResponseDto(
    val type: String,
    val version: String,
    val requiredAction: String,
    val recordedAction: String?,
    val actedAt: String?,
    val satisfied: Boolean,
)

@Serializable
data class RecordLegalDocumentActionRequestDto(
    val documentType: String,
    val documentVersion: String,
    val action: String,
)

@Serializable
data class LegalDocumentActionResponseDto(
    val id: String,
    val documentType: String,
    val documentVersion: String,
    val action: String,
    val actedAt: String,
)
