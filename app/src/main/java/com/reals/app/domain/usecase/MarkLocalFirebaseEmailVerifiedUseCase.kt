package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository

fun interface MarkLocalFirebaseEmailVerified {
    suspend operator fun invoke(): ApiResult<Unit>
}

class MarkLocalFirebaseEmailVerifiedUseCase(
    private val meRepository: MeRepository,
) : MarkLocalFirebaseEmailVerified {
    override suspend operator fun invoke(): ApiResult<Unit> =
        meRepository.markCurrentFirebaseEmailVerifiedForLocalDevelopment()
}
