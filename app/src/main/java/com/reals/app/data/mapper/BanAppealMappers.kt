package com.reals.app.data.mapper

import com.reals.app.data.dto.BanAppealResponseDto
import com.reals.app.domain.model.PermanentBanAppealState
import com.reals.app.domain.model.PermanentBanAppealStatus

fun BanAppealResponseDto.toDomain(): PermanentBanAppealState = PermanentBanAppealState(
    status = PermanentBanAppealStatus.fromBackend(status),
    banActive = banActive,
    appealedAt = appealedAt,
    reviewedAt = reviewedAt,
)
