package com.reals.app.data.repository

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.ReplaceProfileQuestionSelectionsRequestDto
import com.reals.app.data.dto.UpsertProfileQuestionAnswerRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog

class ProfileQuestionRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getCatalog(): ApiResult<ProfileQuestionCatalog> =
        authorizedCall { authorization -> api.getProfileQuestionCatalog(authorization) }
            .map { it.toDomain() }

    suspend fun getMyAnswers(): ApiResult<List<ProfileQuestionAnswer>> =
        authorizedCall { authorization -> api.getMyProfileQuestionAnswers(authorization) }
            .map { response -> response.answers.map { it.toDomain() } }

    suspend fun upsertAnswer(
        questionId: String,
        answer: String,
    ): ApiResult<List<ProfileQuestionAnswer>> {
        val normalizedQuestionId = questionId.trim()
        val normalizedAnswer = answer.trim()
        if (normalizedQuestionId.isBlank()) {
            return ApiResult.Failure(ApiError.Unexpected("Profile question id is blank."))
        }
        if (normalizedAnswer.isBlank()) {
            return ApiResult.Failure(ApiError.Unexpected("Profile question answer is blank."))
        }
        return authorizedCall { authorization ->
            api.upsertMyProfileQuestionAnswer(
                authorization = authorization,
                questionId = normalizedQuestionId,
                body = UpsertProfileQuestionAnswerRequestDto(answer = normalizedAnswer),
            )
        }.map { response -> response.answers.map { it.toDomain() } }
    }

    suspend fun deleteAnswer(questionId: String): ApiResult<List<ProfileQuestionAnswer>> {
        val normalizedQuestionId = questionId.trim()
        if (normalizedQuestionId.isBlank()) {
            return ApiResult.Failure(ApiError.Unexpected("Profile question id is blank."))
        }
        return authorizedCall { authorization ->
            api.deleteMyProfileQuestionAnswer(
                authorization = authorization,
                questionId = normalizedQuestionId,
            )
        }.map { response -> response.answers.map { it.toDomain() } }
    }

    suspend fun replaceSelections(questionIds: List<String>): ApiResult<List<ProfileQuestionAnswer>> {
        val normalizedQuestionIds = questionIds.map { it.trim() }
        if (normalizedQuestionIds.any { it.isBlank() }) {
            return ApiResult.Failure(ApiError.Unexpected("Profile question selection contains a blank id."))
        }
        return authorizedCall { authorization ->
            api.replaceMyProfileQuestionSelections(
                authorization = authorization,
                body = ReplaceProfileQuestionSelectionsRequestDto(questionIds = normalizedQuestionIds),
            )
        }.map { response -> response.answers.map { it.toDomain() } }
    }
}
