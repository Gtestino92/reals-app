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
import kotlinx.coroutines.Job
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
    private var searchAttemptId = 0
    private var enqueueJob: Job? = null
    private var silentHomePollJob: Job? = null
    private val locallyHiddenPendingChatMatchIds = mutableSetOf<String>()
    private val locallyHiddenVisualMatchIds = mutableSetOf<String>()

    // -- Public API for RealsRootViewModel -------------------------------------

    fun beginMatchmakingLocationResolution() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        searchAttemptId += 1
        uiState.value = current.copy(
            home = current.home.copy(
                matchmakingSearchPhase = MatchmakingSearchUiPhase.ResolvingLocation,
                homeError = null,
                homeMessage = null,
            ),
        )
    }

    fun failMatchmakingSearchPreparation() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        uiState.value = current.copy(
            home = current.home.copy(
                matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                homeLoading = false,
            ),
        )
    }

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
        if (uiState.value !is RealsRootUiState.Ready) return
        if (silentHomePollJob?.isActive == true) return

        silentHomePollJob = scope.launch {
            when (val statusResult = dependencies.getHomeStatus()) {
                is ApiResult.Success -> {
                    val latest = uiState.value as? RealsRootUiState.Ready ?: return@launch
                    val status = statusResult.value
                    val knownVersion = latest.home.homeStatusVersion
                    val hasHome = latest.home.homeState != null
                    if (hasHome && knownVersion == status.version) return@launch
                    if (hasHome && knownVersion == null && !status.dirty) {
                        uiState.value = latest.copy(
                            home = latest.home.copy(homeStatusVersion = status.version),
                        )
                        return@launch
                    }

                    when (val homeResult = dependencies.getHome()) {
                        is ApiResult.Success -> publishHomeSuccess(
                            ready = latest,
                            home = homeResult.value,
                            autoNavigateEngagements = latest.home.screenModel?.matchmaking?.inQueue == true,
                            homeStatusVersion = status.version,
                        )

                        is ApiResult.Failure -> {
                            // Silent polling should never surface transient refresh errors.
                        }
                    }
                }

                is ApiResult.Failure -> {
                    // Silent polling should never surface transient status errors.
                }
            }
        }
    }

    fun enqueueMatchmaking(location: SearchLocationInput) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        searchAttemptId += 1
        enqueueMatchmakingForAttempt(current, location, searchAttemptId)
    }

    fun enqueueMatchmakingFromResolvedDeviceLocation(location: SearchLocationInput) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (current.home.matchmakingSearchPhase != MatchmakingSearchUiPhase.ResolvingLocation) return
        enqueueMatchmakingForAttempt(current, location, searchAttemptId)
    }

    private fun enqueueMatchmakingForAttempt(
        current: RealsRootUiState.Ready,
        location: SearchLocationInput,
        attemptId: Int,
    ) {
        enqueueJob?.cancel()
        enqueueJob = scope.launch {
            lastSearchLocation = location
            val pending = current.copy(
                home = current.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                    matchmakingBlockedReason = null,
                    matchmakingSearchPhase = MatchmakingSearchUiPhase.JoiningQueue,
                ),
            )
            uiState.value = pending
            when (val result = dependencies.enqueueMatchmaking(location)) {
                is ApiResult.Success -> {
                    if (attemptId != searchAttemptId) return@launch
                    loadHomeForReady(
                        ready = pending.copy(
                            home = pending.home.copy(
                                homeLoading = true,
                                homeMessage = null,
                                matchmakingSearchPhase = MatchmakingSearchUiPhase.Searching,
                            ),
                        ),
                        autoNavigateEngagements = true,
                    )
                }

                is ApiResult.Failure -> {
                    if (attemptId != searchAttemptId) return@launch
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
                            matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                        ),
                    )
                }
            }
        }
    }

    fun cancelMatchmakingSearch() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val inQueue = current.home.screenModel?.matchmaking?.inQueue == true ||
            current.home.homeState?.matchmaking?.inQueue == true

        if (inQueue || current.home.matchmakingSearchPhase == MatchmakingSearchUiPhase.Searching) {
            searchAttemptId += 1
            leaveMatchmakingQueue()
            return
        }

        if (
            current.home.matchmakingSearchPhase == MatchmakingSearchUiPhase.ResolvingLocation ||
            current.home.matchmakingSearchPhase == MatchmakingSearchUiPhase.JoiningQueue
        ) {
            searchAttemptId += 1
            enqueueJob?.cancel()
            uiState.value = current.copy(
                home = current.home.copy(
                    homeLoading = false,
                    homeError = null,
                    homeMessage = null,
                    matchmakingSearchPhase = MatchmakingSearchUiPhase.Idle,
                ),
            )
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
                publishHomeSuccess(
                    ready = ready,
                    home = homeResult.value,
                    autoNavigateEngagements = autoNavigateEngagements,
                    homeStatusVersion = ready.home.homeStatusVersion,
                )
            }

            is ApiResult.Failure -> {
                uiState.value = ready.copy(
                    home = ready.home.copy(
                        homeLoading = false,
                        homeError = homeResult.error,
                        homeMessage = null,
                        matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
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

    private suspend fun publishHomeSuccess(
        ready: RealsRootUiState.Ready,
        home: HomeState,
        autoNavigateEngagements: Boolean,
        homeStatusVersion: Long?,
    ) {
        pruneLocalHiddenInteractions(home)
        val screenModel = buildHomeScreenModel(
            home = home,
            localMatchmakingBlockedReason = ready.home.matchmakingBlockedReason,
        )

        routeFromHomeScreenModel(
            ready = ready.copy(
                home = ready.home.copy(
                    homeState = home,
                    homeStatusVersion = homeStatusVersion,
                    screenModel = screenModel,
                    homeLoading = false,
                    homeError = null,
                    matchmakingSearchPhase = searchPhaseAfterHomeLoad(
                        ready = ready,
                        screenModel = screenModel,
                    ),
                ),
            ),
            autoNavigateEngagements = autoNavigateEngagements,
        )
    }

    private fun searchPhaseAfterHomeLoad(
        ready: RealsRootUiState.Ready,
        screenModel: com.reals.app.ui.matchmaking.HomeScreenModel,
    ): MatchmakingSearchUiPhase {
        if (screenModel.matchmaking.inQueue) return MatchmakingSearchUiPhase.Searching
        return when (ready.home.matchmakingSearchPhase) {
            MatchmakingSearchUiPhase.ResolvingLocation,
            MatchmakingSearchUiPhase.JoiningQueue,
            MatchmakingSearchUiPhase.Searching,
            MatchmakingSearchUiPhase.Failed -> MatchmakingSearchUiPhase.Idle
            MatchmakingSearchUiPhase.Idle -> MatchmakingSearchUiPhase.Idle
        }
    }

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
