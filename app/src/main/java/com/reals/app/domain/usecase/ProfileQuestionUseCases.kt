package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.ProfileQuestionRepository
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog

class GetProfileQuestionCatalogUseCase(
    private val repository: ProfileQuestionRepository,
) {
    suspend operator fun invoke(): ApiResult<ProfileQuestionCatalog> =
        repository.getCatalog()
}

class GetMyProfileQuestionAnswersUseCase(
    private val repository: ProfileQuestionRepository,
) {
    suspend operator fun invoke(): ApiResult<List<ProfileQuestionAnswer>> =
        repository.getMyAnswers()
}

class UpsertMyProfileQuestionAnswerUseCase(
    private val repository: ProfileQuestionRepository,
) {
    suspend operator fun invoke(questionId: String, answer: String): ApiResult<List<ProfileQuestionAnswer>> =
        repository.upsertAnswer(questionId, answer)
}

class DeleteMyProfileQuestionAnswerUseCase(
    private val repository: ProfileQuestionRepository,
) {
    suspend operator fun invoke(questionId: String): ApiResult<List<ProfileQuestionAnswer>> =
        repository.deleteAnswer(questionId)
}

class ReplaceMyProfileQuestionSelectionsUseCase(
    private val repository: ProfileQuestionRepository,
) {
    suspend operator fun invoke(questionIds: List<String>): ApiResult<List<ProfileQuestionAnswer>> =
        repository.replaceSelections(questionIds)
}
