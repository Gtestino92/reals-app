package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase

internal class ProfileEntryCoordinator(
    private val getProfilePhotos: GetProfilePhotosUseCase,
    private val getHome: GetHomeUseCase,
) {
    suspend fun enter(
        session: ProvisionedSession,
        onPending: (ProfileEntryResult.ShowReady) -> Unit,
    ): ProfileEntryResult {
        val snapshot = session.profileSnapshot
        if (snapshot is ProfileSnapshot.Found) {
            if (snapshot.profile.status == ProfileStatus.Active) {
                return ProfileEntryResult.LoadHome(
                    ready = RealsRootUiState.Ready(
                        session = session,
                        home = HomeUiState(homeLoading = true),
                    ),
                    publishLoadingState = false,
                    autoNavigateEngagements = true,
                )
            }

            if (snapshot.profile.status == ProfileStatus.Draft) {
                when (val home = getHome()) {
                    is ApiResult.Success -> {
                        if (home.value.canRemainInHomeForProfileStatus()) {
                            return ProfileEntryResult.LoadHome(
                                ready = RealsRootUiState.Ready(
                                    session = session,
                                    home = HomeUiState(homeLoading = true),
                                ),
                                publishLoadingState = false,
                                autoNavigateEngagements = true,
                                preloadedHome = home.value,
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        if (home.error.isAccountDeleted()) {
                            return ProfileEntryResult.AccountDeletionPendingFromBackend
                        }
                        return ProfileEntryResult.ShowReady(
                            RealsRootUiState.Ready(
                                session = session,
                                home = HomeUiState(
                                    homeLoading = false,
                                    homeError = home.error,
                                    matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                                ),
                            )
                        )
                    }
                }
            }

            return loadProfileCompletion(session, onPending)
        }

        return ProfileEntryResult.ShowReady(RealsRootUiState.Ready(session))
    }

    private suspend fun loadProfileCompletion(
        session: ProvisionedSession,
        onPending: (ProfileEntryResult.ShowReady) -> Unit,
    ): ProfileEntryResult {
        val loadingState = RealsRootUiState.Ready(
            session = session,
            photos = PhotoManagementUiState(loadingPhotos = true),
        )
        onPending(ProfileEntryResult.ShowReady(loadingState))

        return when (val photos = getProfilePhotos()) {
            is ApiResult.Success -> ProfileEntryResult.ShowReady(
                loadingState.copy(
                    photos = PhotoManagementUiState(
                        loadingPhotos = false,
                        profilePhotos = photos.value.sortedBy { it.position },
                    ),
                )
            )

            is ApiResult.Failure -> {
                if (photos.error.isAccountDeleted()) {
                    ProfileEntryResult.AccountDeletionPendingFromBackend
                } else {
                    ProfileEntryResult.ShowReady(
                        loadingState.copy(
                            photos = PhotoManagementUiState(
                                loadingPhotos = false,
                                profilePhotosError = photos.error,
                            ),
                        )
                    )
                }
            }
        }
    }
}

internal sealed interface ProfileEntryResult {
    data class LoadHome(
        val ready: RealsRootUiState.Ready,
        val publishLoadingState: Boolean = false,
        val autoNavigateEngagements: Boolean = true,
        val preloadedHome: HomeState? = null,
    ) : ProfileEntryResult

    data class ShowReady(
        val state: RealsRootUiState.Ready,
    ) : ProfileEntryResult

    data object AccountDeletionPendingFromBackend : ProfileEntryResult
}
