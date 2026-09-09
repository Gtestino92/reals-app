package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.PatchAffinityAnswerRequestDto
import com.reals.app.data.dto.PatchAffinityAnswersRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityQuestionCatalog

class AffinityQuestionRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getCatalog(): ApiResult<AffinityQuestionCatalog> =
        authorizedCall { authorization -> api.getAffinityQuestionCatalog(authorization) }
            .map { it.toDomain() }

    suspend fun getMyAnswers(): ApiResult<List<AffinityAnswer>> =
        authorizedCall { authorization -> api.getMyAffinityAnswers(authorization) }
            .map { response -> response.answers.map { it.toDomain() } }

    suspend fun patchAnswer(
        questionId: String,
        answerCode: String,
    ): ApiResult<List<AffinityAnswer>> {
        val normalizedQuestionId = questionId.trim()
        val normalizedAnswerCode = answerCode.trim()
        if (normalizedQuestionId.isBlank()) {
            return ApiResult.Failure(ApiError.Unexpected("Affinity question id is blank."))
        }
        if (normalizedAnswerCode.isBlank()) {
            return ApiResult.Failure(ApiError.Unexpected("Affinity answer code is blank."))
        }
        return authorizedCall { authorization ->
            api.patchMyAffinityAnswers(
                authorization = authorization,
                body = PatchAffinityAnswersRequestDto(
                    answers = listOf(
                        PatchAffinityAnswerRequestDto(
                            questionId = normalizedQuestionId,
                            answerCode = normalizedAnswerCode,
                        )
                    ),
                ),
            )
        }.map { response -> response.answers.map { it.toDomain() } }
    }

    suspend fun deleteAnswer(questionId: String): ApiResult<List<AffinityAnswer>> {
        val normalizedQuestionId = questionId.trim()
        if (normalizedQuestionId.isBlank()) {
            return ApiResult.Failure(ApiError.Unexpected("Affinity question id is blank."))
        }
        return authorizedCall { authorization ->
            api.deleteMyAffinityAnswer(
                authorization = authorization,
                questionId = normalizedQuestionId,
            )
        }.map { response -> response.answers.map { it.toDomain() } }
    }
}
