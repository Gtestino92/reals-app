package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.ui.matchmaking.HomeRoute
import com.reals.app.ui.matchmaking.HomeRouter
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Encapsulates the Home / matchmaking concerns of [RealsRootViewModel].
 *
 * Owns the matchmaking queue state, locally-hidden pending interactions, and the
 * shared [RealsRootUiState.Ready] home-loading pipeline. Other features can ask
 * the coordinator to reload home or return to home without knowing how the
 * routing or local bookkeeping works.
 */
internal class HomeCoordinator(
    private val uiState: MutableStateFlow<RealsRootUiState>,
    private val dependencies: HomeFeatureDependencies,
    private val scope: CoroutineScope,
    private val onOpenFirstChat: suspend (
        session: ProvisionedSession,
        matchId: String,
        chatId: String?,
    ) -> Unit,
    private val onReloadActiveSession: suspend (user: BackendUser) -> Unit,
) {
    private val homeUiMapper = HomeUiMapper()
    private val homeRouter = HomeRouter()
    private var lastSearchLocation: SearchLocationInput? = null
    private val locallyHiddenPendingChatMatchIds = mutableSetOf<String>()
    private val locallyHiddenVisualMatchIds = mutableSetOf<String>()

    // -- Public API for RealsRootViewModel -------------------------------------

    fun refreshHomeState() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return

        scope.launch {
            loadHomeForReady(
                ready = current.copy(
                    home = current.home.copy(
                        homeLoading = true,
                        homeError = null,
                        homeMessage = null,
                    ),
                ),
                autoNavigateEngagements = current.home.screenModel?.matchmaking?.inQueue == true,
            )
        }
    }

    fun pollHomeStateSilently() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return

        scope.launch {
            when (val homeResult = dependencies.getHome()) {
                is ApiResult.Success -> {
                    val latest = uiState.value as? RealsRootUiState.Ready ?: return@launch
                    pruneLocalHiddenInteractions(homeResult.value)
                    val screenModel = buildHomeScreenModel(
                        home = homeResult.value,
                        localMatchmakingBlockedReason = latest.home.matchmakingBlockedReason,
                    )

                    routeFromHomeScreenModel(
                        ready = latest.copy(
                            home = latest.home.copy(
                                homeState = homeResult.value,
                                screenModel = screenModel,
                                homeLoading = false,
                            ),
                        ),
                        autoNavigateEngagements = latest.home.screenModel?.matchmaking?.inQueue == true,
                    )
                }

                is ApiResult.Failure -> {
                    // polling silencioso: no pisar UI
                }
            }
        }
    }

    fun enqueueMatchmaking(location: SearchLocationInput) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return

        scope.launch {
            lastSearchLocation = location
            val pending = current.copy(
                home = current.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                    matchmakingBlockedReason = null,
                ),
            )
            uiState.value = pending
            when (val result = dependencies.enqueueMatchmaking(location)) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        home = pending.home.copy(
                            homeLoading = true,
                            homeMessage = null,
                        ),
                    ),
                    autoNavigateEngagements = true,
                )

                is ApiResult.Failure -> {
                    val blockedReason = result.error.takeIf { it.isActiveInteractionLimitError() }
                    uiState.value = pending.copy(
                        home = pending.home.copy(
                            screenModel = buildHomeScreenModel(
                                home = pending.home.homeState,
                                localMatchmakingBlockedReason = blockedReason,
                            ),
                            homeLoading = false,
                            homeError = result.error,
                            matchmakingBlockedReason = blockedReason,
                        ),
                    )
                }
            }
        }
    }

    fun leaveMatchmakingQueue() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return

        scope.launch {
            val pending = current.copy(
                home = current.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                ),
            )
            uiState.value = pending
            when (val result = dependencies.leaveQueue()) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        home = pending.home.copy(
                            homeLoading = true,
                            homeMessage = null,
                        ),
                    ),
                    autoNavigateEngagements = false,
                )

                is ApiResult.Failure -> uiState.value = pending.copy(
                    home = pending.home.copy(
                        homeLoading = false,
                        homeError = result.error,
                    ),
                )
            }
        }
    }

    fun dismissSecondChatFromHome(connectionId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val cleanConnectionId = connectionId.trim()
        if (cleanConnectionId.isBlank()) return

        scope.launch {
            val pending = current.copy(
                home = current.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                ),
            )
            uiState.value = pending

            when (val result = dependencies.dismissSecondChat(cleanConnectionId)) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        home = pending.home.copy(
                            homeLoading = true,
                            homeMessage = "Eliminamos este segundo chat de tu Home.",
                        ),
                    ),
                    autoNavigateEngagements = false,
                )

                is ApiResult.Failure -> uiState.value = pending.copy(
                    home = pending.home.copy(
                        homeLoading = false,
                        homeError = result.error,
                    ),
                )
            }
        }
    }

    /**
     * Loads the home for an arbitrary [RealsRootUiState.Ready] state, optionally
     * publishing a loading indicator and auto-navigating to a pending engagement.
     */
    suspend fun loadHomeForReady(
        ready: RealsRootUiState.Ready,
        publishLoadingState: Boolean = true,
        autoNavigateEngagements: Boolean = false,
    ) {
        if (publishLoadingState) {
            uiState.value = ready.copy(
                home = ready.home.copy(
                    homeLoading = true,
                    homeError = null,
                ),
            )
        }

        when (val homeResult = dependencies.getHome()) {
            is ApiResult.Success -> {
                pruneLocalHiddenInteractions(homeResult.value)
                val screenModel = buildHomeScreenModel(
                    home = homeResult.value,
                    localMatchmakingBlockedReason = ready.home.matchmakingBlockedReason,
                )

                routeFromHomeScreenModel(
                    ready = ready.copy(
                        home = ready.home.copy(
                            homeState = homeResult.value,
                            screenModel = screenModel,
                            homeLoading = false,
                            homeError = null,
                        ),
                    ),
                    autoNavigateEngagements = autoNavigateEngagements,
                )
            }

            is ApiResult.Failure -> {
                uiState.value = ready.copy(
                    home = ready.home.copy(
                        homeLoading = false,
                        homeError = homeResult.error,
                        homeMessage = null,
                    ),
                )
            }
        }
    }

    /**
     * Reloads home after a session is provisioned (e.g. after account reactivation
     * or coming back from a non-home feature). Reuses [lastSearchLocation] when
     * available so the user resumes the matchmaking queue without leaving home.
     */
    suspend fun reenterMatchmakingOrLoadHome(session: ProvisionedSession) {
        val location = lastSearchLocation

        if (location == null) {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = session,
                    home = HomeUiState(homeLoading = true),
                ),
                autoNavigateEngagements = false,
            )
            return
        }

        val ready = RealsRootUiState.Ready(
            session = session,
            home = HomeUiState(homeLoading = false),
        )

        when (val enqueueResult = dependencies.enqueueMatchmaking(location)) {
            is ApiResult.Success -> loadHomeForReady(
                ready = ready.copy(
                    home = ready.home.copy(
                        matchmakingBlockedReason = null,
                        homeMessage = "Aprobaste el chat. Te avisaremos si la otra persona tambi\u00e9n aprueba.",
                    ),
                ),
                publishLoadingState = false,
                autoNavigateEngagements = true,
            )

            is ApiResult.Failure -> {
                val reachedLimit = enqueueResult.error.isActiveInteractionLimitError()

                loadHomeForReady(
                    ready = ready.copy(
                        home = ready.home.copy(
                            homeMessage = if (reachedLimit) {
                                "Aprobaste el chat. Ya ten\u00e9s el m\u00e1ximo de interacciones activas."
                            } else {
                                "Aprobaste el chat. No pudimos volver a iniciar la b\u00fasqueda autom\u00e1ticamente."
                            },
                            matchmakingBlockedReason = enqueueResult.error,
                        ),
                    ),
                    publishLoadingState = false,
                    autoNavigateEngagements = false,
                )
            }
        }
    }

    /**
     * Convenience for non-home features (chat, scheduling, visual review...) to
     * come back to home after they finish. Loads the home with a status message.
     */
    suspend fun returnHome(session: ProvisionedSession, message: String? = null) {
        loadHomeForReady(
            ready = RealsRootUiState.Ready(
                session = session,
                home = HomeUiState(
                    homeLoading = true,
                    homeMessage = message,
                ),
            ),
            publishLoadingState = true,
            autoNavigateEngagements = false,
        )
    }

    /** Snapshot of the locally-hidden interactions, used by the mapper and routing. */
    fun localHiddenSnapshot(): LocalHiddenInteractions = LocalHiddenInteractions(
        hiddenFirstChatMatchIds = locallyHiddenPendingChatMatchIds.toSet(),
        hiddenVisualMatchIds = locallyHiddenVisualMatchIds.toSet(),
    )

    /** Records a locally-hidden first chat match so it stops appearing in pending actions. */
    fun hideFirstChatLocally(matchId: String) {
        locallyHiddenPendingChatMatchIds += matchId
    }

    /** Records a locally-hidden visual review match so it stops appearing in pending actions. */
    fun hideVisualReviewLocally(matchId: String) {
        locallyHiddenVisualMatchIds += matchId
    }

    // -- Private helpers -------------------------------------------------------

    private fun buildHomeScreenModel(
        home: HomeState?,
        localMatchmakingBlockedReason: ApiError?,
    ) = homeUiMapper.toScreenModel(
        home = home,
        localHidden = localHiddenSnapshot(),
        localMatchmakingBlockedReason = localMatchmakingBlockedReason,
    )

    private suspend fun routeFromHomeScreenModel(
        ready: RealsRootUiState.Ready,
        autoNavigateEngagements: Boolean,
    ) {
        val home = ready.homeState ?: run {
            uiState.value = ready
            return
        }

        if (home.profileStatus != ProfileStatus.Active) {
            onReloadActiveSession(ready.session.user)
            return
        }

        when (
            val route = homeRouter.resolve(
                screenModel = ready.home.screenModel ?: buildHomeScreenModel(
                    home = home,
                    localMatchmakingBlockedReason = ready.home.matchmakingBlockedReason,
                ),
                autoNavigate = autoNavigateEngagements,
            )
        ) {
            HomeRoute.StayHome -> uiState.value = ready
            is HomeRoute.OpenFirstChat -> onOpenFirstChat(
                ready.session,
                route.matchId,
                route.chatId,
            )
        }
    }

    private fun pruneLocalHiddenInteractions(home: HomeState) {
        val actionableChatActiveIds = home.pendingActions
            .filterIsInstance<HomePendingAction.FirstChat>()
            .map { it.matchId }
            .toSet()

        val stillVisualPhaseIds = home.pendingActions
            .filterIsInstance<HomePendingAction.VisualReview>()
            .map { it.matchId }
            .toSet()

        locallyHiddenPendingChatMatchIds.retainAll(actionableChatActiveIds)
        locallyHiddenVisualMatchIds.retainAll(stillVisualPhaseIds)
    }

    private fun ApiError.isActiveInteractionLimitError(): Boolean {
        if (this !is ApiError.Backend) return false
        return when (backendErrorCode) {
            BackendErrorCode.ActiveMatchLimitReached,
            BackendErrorCode.ActiveConnectionLimitReached -> true
            else -> false
        }
    }
}
