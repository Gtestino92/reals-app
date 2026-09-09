package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.BanAppealRepository
import com.reals.app.domain.model.PermanentBanAppealState

fun interface GetPermanentBanAppeal {
    suspend operator fun invoke(): ApiResult<PermanentBanAppealState>
}

fun interface SubmitPermanentBanAppeal {
    suspend operator fun invoke(statement: String): ApiResult<Unit>
}

class GetPermanentBanAppealUseCase(
    private val repository: BanAppealRepository,
) : GetPermanentBanAppeal {
    override suspend operator fun invoke(): ApiResult<PermanentBanAppealState> =
        repository.getMyBanAppeal()
}

class SubmitPermanentBanAppealUseCase(
    private val repository: BanAppealRepository,
) : SubmitPermanentBanAppeal {
    override suspend operator fun invoke(statement: String): ApiResult<Unit> =
        repository.submitMyBanAppeal(statement)
}
