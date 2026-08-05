package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfileQuestion
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog

const val ProfileQuestionAnswerMaxLength = 160
const val ProfileQuestionMaxPublicSelections = 3

data class ProfileQuestionOverviewPresentation(
    val answeredCount: Int,
    val totalQuestionCount: Int,
    val selectedCount: Int,
)

data class ProfileQuestionRowPresentation(
    val question: ProfileQuestion,
    val answer: ProfileQuestionAnswer?,
    val selectedPosition: Int?,
    val selectable: Boolean,
)

data class ProfileQuestionSelectionRowPresentation(
    val question: ProfileQuestion,
    val answer: ProfileQuestionAnswer,
    val selectedPosition: Int?,
)

data class ProfileQuestionAnswerValidation(
    val normalizedAnswer: String,
    val characterCount: Int,
    val valid: Boolean,
    val error: String?,
)

fun validateProfileQuestionAnswer(input: String): ProfileQuestionAnswerValidation {
    val normalized = input.trim()
    return ProfileQuestionAnswerValidation(
        normalizedAnswer = normalized,
        characterCount = normalized.length,
        valid = normalized.isNotBlank() && normalized.length <= ProfileQuestionAnswerMaxLength,
        error = when {
            normalized.isBlank() -> "Escribí una respuesta para guardar."
            normalized.length > ProfileQuestionAnswerMaxLength ->
                "La respuesta puede tener hasta $ProfileQuestionAnswerMaxLength caracteres."
            else -> null
        },
    )
}

fun ProfileQuestionCatalog.sortedQuestions(): List<ProfileQuestion> =
    questions.sortedWith(compareBy<ProfileQuestion> { it.displayOrder }.thenBy { it.id })

fun ProfileQuestionCatalog.overview(answers: List<ProfileQuestionAnswer>): ProfileQuestionOverviewPresentation {
    val catalogIds = questions.map { it.id }.toSet()
    return ProfileQuestionOverviewPresentation(
        answeredCount = answers.count { it.questionId in catalogIds },
        totalQuestionCount = questions.size,
        selectedCount = answers.count { it.current && it.selectedPosition != null },
    )
}

fun ProfileQuestionCatalog.rows(answers: List<ProfileQuestionAnswer>): List<ProfileQuestionRowPresentation> {
    val answersByQuestionId = answers.associateBy { it.questionId }
    return sortedQuestions().map { question ->
        val answer = answersByQuestionId[question.id]
        ProfileQuestionRowPresentation(
            question = question,
            answer = answer,
            selectedPosition = answer?.selectedPosition?.takeIf { answer.current },
            selectable = answer?.current == true && answer.answer.isNotBlank(),
        )
    }
}

fun ProfileQuestionCatalog.selectionRows(
    answers: List<ProfileQuestionAnswer>,
): List<ProfileQuestionSelectionRowPresentation> {
    val questionsById = questions.associateBy { it.id }
    return answers
        .asSequence()
        .filter { it.current && it.answer.isNotBlank() }
        .mapNotNull { answer ->
            questionsById[answer.questionId]?.let { question ->
                ProfileQuestionSelectionRowPresentation(
                    question = question,
                    answer = answer,
                    selectedPosition = answer.selectedPosition,
                )
            }
        }
        .sortedWith(compareBy<ProfileQuestionSelectionRowPresentation> { it.question.displayOrder }.thenBy { it.question.id })
        .toList()
}

fun selectedProfileQuestionIds(answers: List<ProfileQuestionAnswer>): List<String> =
    answers
        .filter { it.current && it.selectedPosition != null }
        .sortedBy { it.selectedPosition }
        .map { it.questionId }

fun profileQuestionSelectionDraftIsValid(
    draftQuestionIds: List<String>,
    rows: List<ProfileQuestionSelectionRowPresentation>,
): Boolean {
    val selectableIds = rows.map { it.question.id }.toSet()
    return draftQuestionIds.size <= ProfileQuestionMaxPublicSelections &&
        draftQuestionIds.distinct().size == draftQuestionIds.size &&
        draftQuestionIds.all { it in selectableIds }
}
