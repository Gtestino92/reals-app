package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.isAccountBanned
import com.reals.app.core.network.isTerminalAuthFailure

internal fun ApiError.disallowsProfileSnapshot(): Boolean = isTerminalAuthFailure() || isAccountBanned() ||
    (this is ApiError.Backend && statusCode in listOf(401, 403))

internal fun RealsRootUiState.Ready.hasOpenProfileSurface(): Boolean =
    editingActiveProfile || ownerPreview.open || profileQuestions.open

internal fun com.reals.app.domain.model.ProvisionedSession.supportsOwnerProfileContent(): Boolean =
    user.status == com.reals.app.domain.model.BackendUserStatus.Active &&
        (profileSnapshot as? com.reals.app.domain.model.ProfileSnapshot.Found)?.profile?.status in
        listOf(com.reals.app.domain.model.ProfileStatus.Active, com.reals.app.domain.model.ProfileStatus.Draft)
