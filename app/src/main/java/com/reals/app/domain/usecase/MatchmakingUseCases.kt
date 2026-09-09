package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.domain.model.QueueStatus
import com.reals.app.domain.model.SearchLocationInput

class EnqueueMatchmakingUseCase(
    private val matchmakingRepository: MatchmakingRepository,
) {
    suspend operator fun invoke(location: SearchLocationInput): ApiResult<QueueStatus> =
        matchmakingRepository.enqueue(location)
}

class GetQueueStatusUseCase(
    private val matchmakingRepository: MatchmakingRepository,
) {
    suspend operator fun invoke(): ApiResult<QueueStatus> =
        matchmakingRepository.getQueueStatus()
}

class LeaveQueueUseCase(
    private val matchmakingRepository: MatchmakingRepository,
) {
    suspend operator fun invoke(): ApiResult<QueueStatus> =
        matchmakingRepository.leaveQueue()
}
