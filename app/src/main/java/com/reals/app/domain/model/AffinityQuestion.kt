package com.reals.app.domain.model

data class AffinityQuestionCatalog(
    val catalogVersion: String,
    val categories: List<AffinityQuestionCategory>,
    val questions: List<AffinityQuestion>,
)

data class AffinityQuestionCategory(
    val id: String,
    val title: String,
    val description: String?,
    val displayOrder: Int,
)

data class AffinityQuestion(
    val id: String,
    val semanticVersion: Int,
    val contentVersion: Int,
    val categoryId: String,
    val primaryTopic: String,
    val topicTags: List<String>,
    val answerType: AffinityAnswerType,
    val prompt: String,
    val options: List<AffinityAnswerOption>,
)

data class AffinityAnswerOption(
    val code: String,
    val label: String,
    val displayOrder: Int,
)

data class AffinityAnswer(
    val questionId: String,
    val questionSemanticVersion: Int,
    val answerCode: String,
    val createdAt: String,
    val updatedAt: String,
)

enum class AffinityAnswerType {
    SingleChoice,
    OrdinalScale,
    Unknown,
}

