package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository

class FinalizeAccountDeletionUseCase(
    private val meRepository: MeRepository,
) {
    suspend operator fun invoke(): ApiResult<Unit> = meRepository.finalizeMyDeletion()
}
