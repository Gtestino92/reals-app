package com.reals.app.data.mapper

import com.reals.app.data.dto.AffinityAnswerOptionResponseDto
import com.reals.app.data.dto.AffinityAnswerResponseDto
import com.reals.app.data.dto.AffinityQuestionCatalogResponseDto
import com.reals.app.data.dto.AffinityQuestionCategoryResponseDto
import com.reals.app.data.dto.AffinityQuestionResponseDto
import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityAnswerOption
import com.reals.app.domain.model.AffinityAnswerType
import com.reals.app.domain.model.AffinityQuestion
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.domain.model.AffinityQuestionCategory

fun AffinityQuestionCatalogResponseDto.toDomain(): AffinityQuestionCatalog = AffinityQuestionCatalog(
    catalogVersion = catalogVersion,
    categories = categories.map { it.toDomain() },
    questions = questions.map { it.toDomain() },
)

fun AffinityQuestionCategoryResponseDto.toDomain(): AffinityQuestionCategory = AffinityQuestionCategory(
    id = id,
    title = title,
    description = description,
    displayOrder = displayOrder,
)

fun AffinityQuestionResponseDto.toDomain(): AffinityQuestion = AffinityQuestion(
    id = id,
    semanticVersion = semanticVersion,
    contentVersion = contentVersion,
    categoryId = categoryId,
    primaryTopic = primaryTopic,
    topicTags = topicTags,
    answerType = answerType.toAffinityAnswerType(),
    prompt = prompt,
    options = options.map { it.toDomain() },
)

fun AffinityAnswerOptionResponseDto.toDomain(): AffinityAnswerOption = AffinityAnswerOption(
    code = code,
    label = label,
    displayOrder = displayOrder,
)

fun AffinityAnswerResponseDto.toDomain(): AffinityAnswer = AffinityAnswer(
    questionId = questionId,
    questionSemanticVersion = questionSemanticVersion,
    answerCode = answerCode,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun String.toAffinityAnswerType(): AffinityAnswerType = when (this) {
    "SINGLE_CHOICE" -> AffinityAnswerType.SingleChoice
    "ORDINAL_SCALE" -> AffinityAnswerType.OrdinalScale
    else -> AffinityAnswerType.Unknown
}
