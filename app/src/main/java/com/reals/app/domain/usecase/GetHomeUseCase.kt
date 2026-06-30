package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository
import com.reals.app.domain.model.HomePendingState
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.HomeStatus

class GetHomeUseCase(
    private val meRepository: MeRepository,
) {
    suspend operator fun invoke(): ApiResult<HomeState> =
        meRepository.getHome()
}

class GetHomeStatusUseCase(
    private val meRepository: MeRepository,
) {
    suspend operator fun invoke(): ApiResult<HomeStatus> =
        meRepository.getHomeStatus()
}

class GetHomePendingUseCase(
    private val meRepository: MeRepository,
) {
    suspend operator fun invoke(): ApiResult<HomePendingState> =
        meRepository.getHomePending()
}
