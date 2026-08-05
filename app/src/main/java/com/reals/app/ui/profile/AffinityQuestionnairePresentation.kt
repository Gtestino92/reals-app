package com.reals.app.ui.profile

import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityAnswerType
import com.reals.app.domain.model.AffinityQuestion
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.domain.model.AffinityQuestionCategory
import com.reals.app.ui.root.AffinityAnswerMutationUiState

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

fun AffinityQuestionCatalog.groupQuestionsForPresentation(
    answers: List<AffinityAnswer>,
): List<AffinityQuestionCategoryPresentation> {
    val questionsByCategory = questions.groupBy { it.categoryId }
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

fun AffinityQuestionCatalog.progress(answers: List<AffinityAnswer>): AffinityQuestionnaireProgress =
    AffinityQuestionnaireProgress(
        answeredCount = questions.count { it.currentValidAnswer(answers) != null },
        totalQuestionCount = questions.size,
    )

fun AffinityQuestion.currentValidAnswer(answers: List<AffinityAnswer>): AffinityAnswer? {
    if (!answerType.isSupported()) return null
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

fun AffinityQuestion.canSelectAnswerCode(answerCode: String): Boolean =
    answerType.isSupported() && options.any { it.code == answerCode }

fun AffinityAnswerType.isSupported(): Boolean =
    this == AffinityAnswerType.SingleChoice || this == AffinityAnswerType.OrdinalScale
