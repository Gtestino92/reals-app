package com.reals.app.domain.usecase

import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.MeRepository
import com.reals.app.domain.model.NotificationPreferences

fun interface GetNotificationPreferences {
    suspend operator fun invoke(): ApiResult<NotificationPreferences>
}

fun interface UpdateNotificationPreferences {
    suspend operator fun invoke(preferences: NotificationPreferences): ApiResult<NotificationPreferences>
}

class GetNotificationPreferencesUseCase(
    private val meRepository: MeRepository,
) : GetNotificationPreferences {
    override suspend operator fun invoke(): ApiResult<NotificationPreferences> =
        meRepository.getNotificationPreferences()
}

class UpdateNotificationPreferencesUseCase(
    private val meRepository: MeRepository,
) : UpdateNotificationPreferences {
    override suspend operator fun invoke(
        preferences: NotificationPreferences,
    ): ApiResult<NotificationPreferences> =
        meRepository.updateNotificationPreferences(preferences)
}
