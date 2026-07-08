package com.reals.app.data.repository

import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.map
import com.reals.app.data.api.AuthTokenProvider
import com.reals.app.data.api.RealsApi
import com.reals.app.data.dto.ChatDecisionRequestDto
import com.reals.app.data.dto.PersonalMessageRequestDto
import com.reals.app.data.dto.VisualDecisionRequestDto
import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.VisualDecision
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.model.UserBlock

class MatchRepository(
    private val api: RealsApi,
    tokenProvider: AuthTokenProvider,
    apiExecutor: ApiExecutor,
) : AuthenticatedRepository(tokenProvider, apiExecutor) {
    suspend fun blockMatchParticipant(matchId: String): ApiResult<UserBlock> =
        authorizedCall { authorization -> api.blockMatchParticipant(authorization, matchId) }
            .map { it.toDomain() }

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

    suspend fun getVisualProfile(matchId: String): ApiResult<VisualProfile> =
        authorizedCall { authorization -> api.getVisualProfile(authorization, matchId) }
            .map { it.toDomain() }

    suspend fun submitVisualDecision(
        matchId: String,
        decision: VisualDecision,
    ): ApiResult<Match> =
        authorizedCall { authorization ->
            api.submitVisualDecision(
                authorization = authorization,
                matchId = matchId,
                body = VisualDecisionRequestDto(decision.backendValue),
            )
        }.map { it.toDomain() }

    suspend fun putMyPersonalMessage(matchId: String, message: String): ApiResult<Unit> =
        authorizedUnitCall { authorization ->
            api.putMyPersonalMessage(
                authorization = authorization,
                matchId = matchId,
                body = PersonalMessageRequestDto(message),
            )
        }

    suspend fun getPartnerPersonalMessage(matchId: String): ApiResult<String?> =
        authorizedCall { authorization -> api.getPartnerPersonalMessage(authorization, matchId) }
            .map { it.message }
}
