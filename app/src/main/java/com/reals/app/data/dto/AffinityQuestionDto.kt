package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AffinityQuestionCatalogResponseDto(
    val catalogVersion: String,
    val categories: List<AffinityQuestionCategoryResponseDto>,
    val questions: List<AffinityQuestionResponseDto>,
)

@Serializable
data class AffinityQuestionCategoryResponseDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val displayOrder: Int,
)

@Serializable
data class AffinityQuestionResponseDto(
    val id: String,
    val semanticVersion: Int,
    val contentVersion: Int,
    val categoryId: String,
    val primaryTopic: String,
    val topicTags: List<String> = emptyList(),
    val answerType: String,
    val prompt: String,
    val options: List<AffinityAnswerOptionResponseDto> = emptyList(),
)

@Serializable
data class AffinityAnswerOptionResponseDto(
    val code: String,
    val label: String,
    val displayOrder: Int,
)

@Serializable
data class AffinityAnswersResponseDto(
    val answers: List<AffinityAnswerResponseDto> = emptyList(),
)

@Serializable
data class AffinityAnswerResponseDto(
    val questionId: String,
    val questionSemanticVersion: Int,
    val answerCode: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class PatchAffinityAnswersRequestDto(
    val answers: List<PatchAffinityAnswerRequestDto>,
)

@Serializable
data class PatchAffinityAnswerRequestDto(
    val questionId: String,
    val answerCode: String,
)
