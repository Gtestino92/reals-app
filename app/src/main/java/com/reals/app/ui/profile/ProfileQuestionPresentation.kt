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
    val containsLineBreak = input.any { it in "\n\r\u2028\u2029" }

    val error = when {
        containsLineBreak ->
            "La respuesta debe escribirse en una sola línea."

        input.any { it.code in 0..31 || it.code in 127..159 || it == '<' || it == '>' } ->
            "Usá texto simple, sin caracteres de control ni < o >."

        normalized.isBlank() ->
            "Escribí una respuesta para guardar."

        normalized.length > ProfileQuestionAnswerMaxLength ->
            "La respuesta puede tener hasta $ProfileQuestionAnswerMaxLength caracteres."

        else -> null
    }

    return ProfileQuestionAnswerValidation(
        normalizedAnswer = normalized,
        characterCount = normalized.length,
        valid = error == null,
        error = error,
    )
}

fun ProfileQuestionCatalog.sortedQuestions(): List<ProfileQuestion> =
    questions.sortedWith(compareBy<ProfileQuestion> { it.displayOrder }.thenBy { it.id })

fun ProfileQuestionCatalog.overview(answers: List<ProfileQuestionAnswer>): ProfileQuestionOverviewPresentation {
    val catalogIds = questions.map { it.id }.toSet()
    return ProfileQuestionOverviewPresentation(
        answeredCount = answers.count { it.questionId in catalogIds },
        totalQuestionCount = questions.size,
        selectedCount = rows(answers).count { it.selectable && it.selectedPosition != null },
    )
}

fun ProfileQuestionCatalog.rows(answers: List<ProfileQuestionAnswer>): List<ProfileQuestionRowPresentation> {
    val answersByQuestionId = answers.associateBy { it.questionId }
    return sortedQuestions().map { question ->
        val answer = answersByQuestionId[question.id]
        ProfileQuestionRowPresentation(
            question = question,
            answer = answer,
            selectedPosition = answer?.selectedPosition?.takeIf { answer.isCurrentFor(question) },
            selectable = answer?.isCurrentFor(question) == true && validateProfileQuestionAnswer(answer.answer).valid,
        )
    }
}

fun ProfileQuestionCatalog.selectionRows(
    answers: List<ProfileQuestionAnswer>,
): List<ProfileQuestionSelectionRowPresentation> {
    val questionsById = questions.associateBy { it.id }
    return answers
        .asSequence()
        .filter { it.current && validateProfileQuestionAnswer(it.answer).valid }
        .mapNotNull { answer ->
            questionsById[answer.questionId]?.takeIf { answer.isCurrentFor(it) }?.let { question ->
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

fun ProfileQuestionAnswer.isCurrentFor(question: ProfileQuestion): Boolean =
    current && questionSemanticVersion == question.semanticVersion

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

fun canSaveProfileQuestionAnswer(
    input: String,
    savedAnswer: ProfileQuestionAnswer?,
    mutationActive: Boolean,
): Boolean {
    if (mutationActive) return false

    val validation = validateProfileQuestionAnswer(input)
    if (!validation.valid) return false

    return savedAnswer == null ||
            !savedAnswer.current ||
            validation.normalizedAnswer != savedAnswer.answer.trim()
}
