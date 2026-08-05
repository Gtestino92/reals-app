package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.AffinityQuestionRepository
import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityQuestionCatalog

class GetAffinityQuestionCatalogUseCase(
    private val repository: AffinityQuestionRepository,
) {
    suspend operator fun invoke(): ApiResult<AffinityQuestionCatalog> =
        repository.getCatalog()
}

class GetMyAffinityAnswersUseCase(
    private val repository: AffinityQuestionRepository,
) {
    suspend operator fun invoke(): ApiResult<List<AffinityAnswer>> =
        repository.getMyAnswers()
}

class PatchMyAffinityAnswerUseCase(
    private val repository: AffinityQuestionRepository,
) {
    suspend operator fun invoke(questionId: String, answerCode: String): ApiResult<List<AffinityAnswer>> =
        repository.patchAnswer(questionId, answerCode)
}

class DeleteMyAffinityAnswerUseCase(
    private val repository: AffinityQuestionRepository,
) {
    suspend operator fun invoke(questionId: String): ApiResult<List<AffinityAnswer>> =
        repository.deleteAnswer(questionId)
}
