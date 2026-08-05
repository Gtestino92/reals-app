package com.reals.app.ui.profile

import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityAnswerType
import com.reals.app.domain.model.AffinityQuestion
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.domain.model.AffinityQuestionCategory
import com.reals.app.ui.root.AffinityAnswerMutationUiState
import com.reals.app.ui.root.AffinityQuestionSource
import com.reals.app.ui.root.AffinityQuestionnaireDestination

data class AffinityQuestionCategoryPresentation(
    val category: AffinityQuestionCategory,
    val questions: List<AffinityQuestion>,
    val validAnsweredCount: Int,
) {
    val totalQuestionCount: Int get() = questions.size
}

data class AffinityQuestionnaireProgress(
    val answeredCount: Int,
    val totalQuestionCount: Int,
)

data class AffinityQuestionReviewCategoryPresentation(
    val category: AffinityQuestionCategory,
    val rows: List<AffinityQuestionReviewRowPresentation>,
)

data class AffinityQuestionReviewRowPresentation(
    val question: AffinityQuestion,
    val selectedOptionLabel: String,
)

fun AffinityQuestionCatalog.groupQuestionsForPresentation(
    answers: List<AffinityAnswer>,
): List<AffinityQuestionCategoryPresentation> {
    val questionsByCategory = questions.answerable().groupBy { it.categoryId }
    return categories.mapNotNull { category ->
        val categoryQuestions = questionsByCategory[category.id].orEmpty()
        if (categoryQuestions.isEmpty()) {
            null
        } else {
            AffinityQuestionCategoryPresentation(
                category = category,
                questions = categoryQuestions,
                validAnsweredCount = categoryQuestions.count { question ->
                    question.currentValidAnswer(answers) != null
                },
            )
        }
    }
}

fun AffinityQuestionCatalog.reviewRows(
    answers: List<AffinityAnswer>,
): List<AffinityQuestionReviewCategoryPresentation> {
    val rowsByCategory = questions.answerable()
        .mapNotNull { question ->
            question.selectedOptionLabel(answers)?.let { label ->
                question.categoryId to AffinityQuestionReviewRowPresentation(
                    question = question,
                    selectedOptionLabel = label,
                )
            }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    return categories.mapNotNull { category ->
        val rows = rowsByCategory[category.id].orEmpty()
        if (rows.isEmpty()) {
            null
        } else {
            AffinityQuestionReviewCategoryPresentation(category = category, rows = rows)
        }
    }
}

fun AffinityQuestionCatalog.progress(answers: List<AffinityAnswer>): AffinityQuestionnaireProgress {
    val answerableQuestions = questions.answerable()
    return AffinityQuestionnaireProgress(
        answeredCount = answerableQuestions.count { it.currentValidAnswer(answers) != null },
        totalQuestionCount = answerableQuestions.size,
    )
}

fun AffinityQuestionCatalog.firstUnansweredQuestion(
    answers: List<AffinityAnswer>,
): AffinityQuestion? = questions.answerable().firstOrNull { it.currentValidAnswer(answers) == null }

fun AffinityQuestionCatalog.firstCategoryQuestion(
    categoryId: String,
    answers: List<AffinityAnswer>,
    reviewAll: Boolean,
): AffinityQuestion? = categoryAnswerableQuestions(categoryId).firstOrNull { question ->
    reviewAll || question.currentValidAnswer(answers) == null
}

fun AffinityQuestionCatalog.nextContinueQuestion(
    currentQuestionId: String,
    answers: List<AffinityAnswer>,
): AffinityQuestion? = questions.answerable()
    .after(currentQuestionId)
    .firstOrNull { it.currentValidAnswer(answers) == null }

fun AffinityQuestionCatalog.nextCategoryQuestion(
    categoryId: String,
    currentQuestionId: String,
    answers: List<AffinityAnswer>,
    reviewAll: Boolean,
): AffinityQuestion? = categoryAnswerableQuestions(categoryId)
    .after(currentQuestionId)
    .firstOrNull { question -> reviewAll || question.currentValidAnswer(answers) == null }

fun AffinityQuestionCatalog.questionPositionLabel(
    questionId: String,
    source: AffinityQuestionSource,
): String? {
    val sequence = when (source) {
        AffinityQuestionSource.Continue -> questions.answerable()
        is AffinityQuestionSource.Category -> categoryAnswerableQuestions(source.categoryId)
        AffinityQuestionSource.Review -> return null
    }
    val index = sequence.indexOfFirst { it.id == questionId }
    if (index < 0) return null
    return "Pregunta ${index + 1} de ${sequence.size}"
}

fun AffinityQuestionCatalog.categoryFor(question: AffinityQuestion): AffinityQuestionCategory? =
    categories.firstOrNull { it.id == question.categoryId }

fun AffinityQuestionCatalog.findAnswerableQuestion(questionId: String): AffinityQuestion? =
    questions.answerable().firstOrNull { it.id == questionId }

fun AffinityQuestionCatalog.reconciledDestination(
    destination: AffinityQuestionnaireDestination,
    answers: List<AffinityAnswer>,
): AffinityQuestionnaireDestination = when (destination) {
    AffinityQuestionnaireDestination.Overview -> AffinityQuestionnaireDestination.Overview
    AffinityQuestionnaireDestination.Categories -> AffinityQuestionnaireDestination.Categories
    AffinityQuestionnaireDestination.Review -> AffinityQuestionnaireDestination.Review
    is AffinityQuestionnaireDestination.Question -> when (val source = destination.source) {
        AffinityQuestionSource.Continue -> {
            if (findAnswerableQuestion(destination.questionId) == null) {
                AffinityQuestionnaireDestination.Overview
            } else {
                destination
            }
        }

        is AffinityQuestionSource.Category -> {
            val question = findAnswerableQuestion(destination.questionId)
            val categoryExists = categoryAnswerableQuestions(source.categoryId).isNotEmpty()
            if (!categoryExists || question?.categoryId != source.categoryId) {
                AffinityQuestionnaireDestination.Categories
            } else {
                destination
            }
        }

        AffinityQuestionSource.Review -> {
            val question = findAnswerableQuestion(destination.questionId)
            if (question?.currentValidAnswer(answers) == null) {
                AffinityQuestionnaireDestination.Review
            } else {
                destination
            }
        }
    }
}

fun AffinityQuestion.currentValidAnswer(answers: List<AffinityAnswer>): AffinityAnswer? {
    if (!isAnswerable()) return null
    return answers.firstOrNull { answer ->
        answer.questionId == id &&
            answer.questionSemanticVersion == semanticVersion &&
            options.any { option -> option.code == answer.answerCode }
    }
}

fun AffinityQuestion.presentedAnswerCode(
    answers: List<AffinityAnswer>,
    mutation: AffinityAnswerMutationUiState?,
): String? {
    if (mutation?.questionId == id) return mutation.pendingAnswerCode
    return currentValidAnswer(answers)?.answerCode
}

fun AffinityQuestion.selectedOptionLabel(answers: List<AffinityAnswer>): String? {
    val answer = currentValidAnswer(answers) ?: return null
    return options.firstOrNull { it.code == answer.answerCode }?.label
}

fun AffinityQuestion.canSelectAnswerCode(answerCode: String): Boolean =
    isAnswerable() && options.any { it.code == answerCode }

fun AffinityQuestion.isAnswerable(): Boolean =
    answerType.isSupported() && options.size >= 2

fun AffinityAnswerType.isSupported(): Boolean =
    this == AffinityAnswerType.SingleChoice || this == AffinityAnswerType.OrdinalScale

private fun List<AffinityQuestion>.answerable(): List<AffinityQuestion> =
    filter { it.isAnswerable() }

private fun AffinityQuestionCatalog.categoryAnswerableQuestions(categoryId: String): List<AffinityQuestion> =
    questions.answerable().filter { it.categoryId == categoryId }

private fun List<AffinityQuestion>.after(questionId: String): List<AffinityQuestion> {
    val index = indexOfFirst { it.id == questionId }
    return if (index < 0 || index + 1 >= size) emptyList() else drop(index + 1)
}
