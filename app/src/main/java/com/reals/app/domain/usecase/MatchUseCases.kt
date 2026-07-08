package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MatchRepository
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.VisualDecision
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.model.UserBlock

class BlockMatchParticipantUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): ApiResult<UserBlock> =
        matchRepository.blockMatchParticipant(matchId)
}

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

class GetVisualProfileUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): ApiResult<VisualProfile> =
        matchRepository.getVisualProfile(matchId)
}

class SubmitVisualDecisionUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String, decision: VisualDecision): ApiResult<Match> =
        matchRepository.submitVisualDecision(matchId, decision)
}

class PutMyPersonalMessageUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String, message: String): ApiResult<Unit> =
        matchRepository.putMyPersonalMessage(matchId, message)
}

class GetPartnerPersonalMessageUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): ApiResult<String?> =
        matchRepository.getPartnerPersonalMessage(matchId)
}
