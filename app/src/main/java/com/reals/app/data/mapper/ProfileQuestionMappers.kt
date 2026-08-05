package com.reals.app.data.mapper

import com.reals.app.data.dto.ProfileQuestionAnswerResponseDto
import com.reals.app.data.dto.ProfileQuestionCatalogResponseDto
import com.reals.app.data.dto.ProfileQuestionResponseDto
import com.reals.app.data.dto.PublicProfileQuestionResponseDto
import com.reals.app.domain.model.ProfileQuestion
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog
import com.reals.app.domain.model.PublicProfileQuestion

fun ProfileQuestionCatalogResponseDto.toDomain(): ProfileQuestionCatalog = ProfileQuestionCatalog(
    catalogVersion = catalogVersion,
    questions = questions.map { it.toDomain() },
)

fun ProfileQuestionResponseDto.toDomain(): ProfileQuestion = ProfileQuestion(
    id = id,
    semanticVersion = semanticVersion,
    contentVersion = contentVersion,
    prompt = prompt,
    displayOrder = displayOrder,
)

fun ProfileQuestionAnswerResponseDto.toDomain(): ProfileQuestionAnswer = ProfileQuestionAnswer(
    questionId = questionId,
    questionSemanticVersion = questionSemanticVersion,
    answer = answer,
    selectedPosition = selectedPosition,
    current = current,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PublicProfileQuestionResponseDto.toDomain(): PublicProfileQuestion = PublicProfileQuestion(
    questionId = questionId,
    prompt = prompt,
    answer = answer,
    position = position,
)
