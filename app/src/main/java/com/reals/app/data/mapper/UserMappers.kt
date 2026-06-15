package com.reals.app.data.mapper

import com.reals.app.data.dto.UserResponseDto
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.BackendUserStatus

fun UserResponseDto.toDomain(): BackendUser = BackendUser(
    id = id,
    email = email,
    status = BackendUserStatus.fromBackend(status),
    deletedAt = deletedAt,
    deletionFinalizesAt = deletionFinalizesAt,
    createdAt = createdAt,
)
