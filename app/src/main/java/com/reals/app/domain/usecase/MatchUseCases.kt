package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MatchRepository
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.Match

class GetMatchUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): ApiResult<Match> =
        matchRepository.getMatch(matchId)
}

class GetFirstChatForMatchUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): ApiResult<Chat> =
        matchRepository.getFirstChatForMatch(matchId)
}

class SubmitChatDecisionUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String, decision: ChatContinueDecision): ApiResult<Match> =
        matchRepository.submitChatDecision(matchId, decision)
}
