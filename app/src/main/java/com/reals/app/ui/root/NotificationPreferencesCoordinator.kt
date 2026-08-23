package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.domain.model.NotificationPreferenceGroup
import com.reals.app.domain.model.NotificationPreferences
import com.reals.app.domain.model.withGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class NotificationPreferencesCoordinator(
    private val uiState: MutableStateFlow<RealsRootUiState>,
    private val dependencies: AccountFeatureDependencies,
    private val scope: CoroutineScope,
) {
    private var nextRequestId = 0L
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun open() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (current.notificationPreferences.open) return
        val requestId = nextRequestId()
        uiState.value = current.copy(
            editingActiveProfile = false,
            profileManagementDestination = null,
            affinityQuestionnaire = current.affinityQuestionnaire.copy(open = false),
            profileQuestions = current.profileQuestions.copy(open = false),
            notificationPreferences = NotificationPreferencesUiState(
                open = true,
                loading = true,
                requestId = requestId,
            ),
        )
        load(requestId)
    }

    fun retryLoad() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.notificationPreferences
        if (!state.open || state.loading || state.saving) return
        val requestId = nextRequestId()
        uiState.value = current.copy(
            notificationPreferences = state.copy(
                loading = true,
                loadError = null,
                saveError = null,
                preferences = null,
                confirmedPreferences = null,
                requestId = requestId,
            ),
        )
        load(requestId)
    }

    fun close() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.notificationPreferences.open) return
        uiState.value = current.copy(notificationPreferences = NotificationPreferencesUiState())
    }

    fun update(group: NotificationPreferenceGroup, enabled: Boolean) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.notificationPreferences
        val confirmed = state.confirmedPreferences ?: return
        val displayed = state.preferences ?: return
        if (!state.open || state.loading || state.saving) return
        val requested = displayed.withGroup(group, enabled)
        if (requested == displayed) return

        val requestId = nextRequestId()
        uiState.value = current.copy(
            notificationPreferences = state.copy(
                preferences = requested,
                saving = true,
                saveError = null,
                requestId = requestId,
            ),
        )
        save(requestId, confirmed, requested)
    }

    private fun load(requestId: Long) {
        loadJob?.cancel()
        loadJob = scope.launch {
            when (val result = dependencies.getNotificationPreferences()) {
                is ApiResult.Success -> publishLoadSuccess(requestId, result.value)
                is ApiResult.Failure -> publishLoadFailure(requestId, result.error)
            }
        }
    }

    private fun save(
        requestId: Long,
        confirmed: NotificationPreferences,
        requested: NotificationPreferences,
    ) {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            when (val result = dependencies.updateNotificationPreferences(requested)) {
                is ApiResult.Success -> publishSaveSuccess(requestId, result.value)
                is ApiResult.Failure -> publishSaveFailure(requestId, confirmed, result.error)
            }
        }
    }

    private fun publishLoadSuccess(requestId: Long, preferences: NotificationPreferences) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.notificationPreferences
        if (!state.open || state.requestId != requestId) return
        uiState.value = current.copy(
            notificationPreferences = state.copy(
                loading = false,
                preferences = preferences,
                confirmedPreferences = preferences,
                loadError = null,
                saveError = null,
            ),
        )
    }

    private fun publishLoadFailure(requestId: Long, error: com.reals.app.core.network.ApiError) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.notificationPreferences
        if (!state.open || state.requestId != requestId) return
        uiState.value = current.copy(
            notificationPreferences = state.copy(
                loading = false,
                preferences = null,
                confirmedPreferences = null,
                loadError = error,
            ),
        )
    }

    private fun publishSaveSuccess(requestId: Long, preferences: NotificationPreferences) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.notificationPreferences
        if (!state.open || state.requestId != requestId) return
        uiState.value = current.copy(
            notificationPreferences = state.copy(
                preferences = preferences,
                confirmedPreferences = preferences,
                saving = false,
                saveError = null,
            ),
        )
    }

    private fun publishSaveFailure(
        requestId: Long,
        confirmed: NotificationPreferences,
        error: com.reals.app.core.network.ApiError,
    ) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.notificationPreferences
        if (!state.open || state.requestId != requestId) return
        uiState.value = current.copy(
            notificationPreferences = state.copy(
                preferences = confirmed,
                confirmedPreferences = confirmed,
                saving = false,
                saveError = error,
            ),
        )
    }

    private fun nextRequestId(): Long {
        nextRequestId += 1
        return nextRequestId
    }
}
