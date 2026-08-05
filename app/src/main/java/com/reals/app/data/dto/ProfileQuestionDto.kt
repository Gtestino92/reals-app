package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfileQuestionCatalogResponseDto(
    val catalogVersion: String,
    val questions: List<ProfileQuestionResponseDto> = emptyList(),
)

@Serializable
data class ProfileQuestionResponseDto(
    val id: String,
    val semanticVersion: Int,
    val contentVersion: Int,
    val prompt: String,
    val displayOrder: Int,
)

@Serializable
data class ProfileQuestionAnswersResponseDto(
    val answers: List<ProfileQuestionAnswerResponseDto> = emptyList(),
)

@Serializable
data class ProfileQuestionAnswerResponseDto(
    val questionId: String,
    val questionSemanticVersion: Int,
    val answer: String,
    val selectedPosition: Int? = null,
    val current: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class UpsertProfileQuestionAnswerRequestDto(
    val answer: String,
)

@Serializable
data class ReplaceProfileQuestionSelectionsRequestDto(
    val questionIds: List<String>,
)

@Serializable
data class PublicProfileQuestionResponseDto(
    val questionId: String,
    val prompt: String,
    val answer: String,
    val position: Int,
)
