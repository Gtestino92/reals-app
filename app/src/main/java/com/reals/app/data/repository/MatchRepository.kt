package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.ChatDecisionRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.Match

class MatchRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun getMatch(matchId: String): ApiResult<Match> =
        authorizedCall { authorization -> api.getMatch(authorization, matchId) }
            .map { it.toDomain() }

    suspend fun getFirstChatForMatch(matchId: String): ApiResult<Chat> =
        authorizedCall { authorization -> api.getFirstChatForMatch(authorization, matchId) }
            .map { it.toDomain() }

    suspend fun submitChatDecision(
        matchId: String,
        decision: ChatContinueDecision,
    ): ApiResult<Match> =
        authorizedCall { authorization ->
            api.submitChatDecision(
                authorization = authorization,
                matchId = matchId,
                body = ChatDecisionRequestDto(decision.backendValue),
            )
        }.map { it.toDomain() }
}
