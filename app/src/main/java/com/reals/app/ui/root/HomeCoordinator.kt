package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.HomePendingAction
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.HomeStatus
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.ui.matchmaking.HomeRoute
import com.reals.app.ui.matchmaking.HomeRouter
import com.reals.app.ui.matchmaking.HomeUiMapper
import com.reals.app.ui.matchmaking.LocalHiddenInteractions
import com.reals.app.ui.matchmaking.toHomeMatchmakingBlockedReasonUiState
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
    private val onOpenSecondChat: (
        session: ProvisionedSession,
        connectionId: String,
        matchId: String,
        partnerName: String?,
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

    fun showHomeSurface(surface: HomeSurface) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (current.home.surface == surface) return
        uiState.value = current.copy(
            home = current.home.copy(surface = surface),
        )
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
                    val wakeUpDue = status.isHomeWakeUpDue()
                    val requiresFullRefresh = !hasHome ||
                        status.dirty ||
                        knownVersion != status.version ||
                        wakeUpDue
                    if (!requiresFullRefresh) return@launch
                    if (hasHome && knownVersion == null && !status.dirty && !wakeUpDue) {
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
                    if (result.error.isNormalMatchmakingAvailabilityError()) {
                        val availabilityBlocked = pending.copy(
                            home = pending.home.copy(
                                screenModel = buildHomeScreenModel(
                                    home = pending.home.homeState,
                                    localMatchmakingBlockedReason = result.error,
                                ),
                                homeLoading = false,
                                homeError = null,
                                matchmakingBlockedReason = result.error,
                                matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                            ),
                        )
                        uiState.value = availabilityBlocked
                        loadHomeForReady(
                            ready = availabilityBlocked,
                            publishLoadingState = false,
                            autoNavigateEngagements = false,
                        )
                        return@launch
                    }

                    uiState.value = pending.copy(
                        home = pending.home.copy(
                            screenModel = buildHomeScreenModel(
                                home = pending.home.homeState,
                                localMatchmakingBlockedReason = null,
                            ),
                            homeLoading = false,
                            homeError = result.error,
                            matchmakingBlockedReason = null,
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
        preloadedHome: HomeState? = null,
        allowDraftHomeWithoutInteractions: Boolean = false,
    ) {
        val allowDraftHome = allowDraftHomeWithoutInteractions || ready.home.allowDraftHomeWithoutInteractions
        val readyForHome = ready.copy(
            home = ready.home.copy(
                allowDraftHomeWithoutInteractions = allowDraftHome,
            ),
        )
        if (preloadedHome != null) {
            publishHomeSuccess(
                ready = readyForHome,
                home = preloadedHome,
                autoNavigateEngagements = autoNavigateEngagements,
                homeStatusVersion = readyForHome.home.homeStatusVersion,
            )
            return
        }

        if (publishLoadingState) {
            uiState.value = readyForHome.copy(
                home = readyForHome.home.copy(
                    homeLoading = true,
                    homeError = null,
                ),
            )
        }

        when (val homeResult = dependencies.getHome()) {
            is ApiResult.Success -> {
                publishHomeSuccess(
                    ready = readyForHome,
                    home = homeResult.value,
                    autoNavigateEngagements = autoNavigateEngagements,
                    homeStatusVersion = readyForHome.home.homeStatusVersion,
                )
            }

            is ApiResult.Failure -> {
                uiState.value = readyForHome.copy(
                    home = readyForHome.home.copy(
                        homeLoading = false,
                        homeError = homeResult.error,
                        homeMessage = null,
                        matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                    ),
                )
            }
        }
    }

    fun closeProfileManagementWithHomeReload(current: RealsRootUiState.Ready) {
        if (current.session.profileSnapshot !is com.reals.app.domain.model.ProfileSnapshot.Found) return

        scope.launch {
            val safeCurrent = current.withoutStaleHomeForKnownDraftProfile()
            val loading = safeCurrent.copy(
                home = safeCurrent.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                ),
            )
            uiState.value = loading

            when (val homeResult = dependencies.getHome()) {
                is ApiResult.Success -> {
                    val refreshed = readyWithHomeSuccess(
                        ready = loading.copy(
                            editingActiveProfile = false,
                            profileManagementDestination = null,
                        ),
                        home = homeResult.value,
                        homeStatusVersion = loading.home.homeStatusVersion,
                    )
                    if (homeResult.value.canRemainInHomeForProfileStatus()) {
                        routeFromHomeScreenModel(
                            ready = refreshed,
                            autoNavigateEngagements = false,
                        )
                    } else {
                        uiState.value = refreshed
                    }
                }

                is ApiResult.Failure -> {
                    uiState.value = loading.copy(
                        home = loading.home.copy(
                            homeLoading = false,
                            homeError = homeResult.error,
                            homeMessage = null,
                            matchmakingSearchPhase = MatchmakingSearchUiPhase.Failed,
                        ),
                    )
                }
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
                val normalAvailability = enqueueResult.error.isNormalMatchmakingAvailabilityError()
                val readyForRefresh = ready.copy(
                    home = ready.home.copy(
                        screenModel = if (normalAvailability) {
                            buildHomeScreenModel(
                                home = ready.home.homeState,
                                localMatchmakingBlockedReason = enqueueResult.error,
                            )
                        } else {
                            ready.home.screenModel
                        },
                        homeLoading = false,
                        homeError = null,
                        homeMessage = if (normalAvailability) {
                            null
                        } else {
                            "Aprobaste el chat. No pudimos volver a iniciar la b\u00fasqueda autom\u00e1ticamente."
                        },
                        matchmakingBlockedReason = enqueueResult.error,
                        matchmakingSearchPhase = if (normalAvailability) {
                            MatchmakingSearchUiPhase.Failed
                        } else {
                            ready.home.matchmakingSearchPhase
                        },
                    ),
                )
                if (normalAvailability) {
                    uiState.value = readyForRefresh
                }

                loadHomeForReady(
                    ready = readyForRefresh,
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
    suspend fun returnHome(
        session: ProvisionedSession,
        message: String? = null,
        surface: HomeSurface = HomeSurface.Overview,
    ) {
        loadHomeForReady(
            ready = RealsRootUiState.Ready(
                session = session,
                home = HomeUiState(
                    surface = surface,
                    homeLoading = true,
                    homeMessage = message,
                ),
            ),
            publishLoadingState = true,
            autoNavigateEngagements = false,
            allowDraftHomeWithoutInteractions = true,
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
        localMatchmakingBlockedReason = localMatchmakingBlockedReason?.toHomeMatchmakingBlockedReasonUiState(),
    )

    private suspend fun publishHomeSuccess(
        ready: RealsRootUiState.Ready,
        home: HomeState,
        autoNavigateEngagements: Boolean,
        homeStatusVersion: Long?,
    ) {
        routeFromHomeScreenModel(
            ready = readyWithHomeSuccess(
                ready = ready,
                home = home,
                homeStatusVersion = homeStatusVersion,
            ),
            autoNavigateEngagements = autoNavigateEngagements,
        )
    }

    private fun readyWithHomeSuccess(
        ready: RealsRootUiState.Ready,
        home: HomeState,
        homeStatusVersion: Long?,
    ): RealsRootUiState.Ready {
        pruneLocalHiddenInteractions(home)
        val retainedLocalBlocker = ready.home.matchmakingBlockedReason
            .takeIf { home.matchmaking.blockedReason == null && !home.matchmaking.canSearch }
        val screenModel = buildHomeScreenModel(
            home = home,
            localMatchmakingBlockedReason = retainedLocalBlocker,
        )
        val session = ready.session.withProfileStatusFrom(home)

        return ready.copy(
            session = session,
            home = ready.home.copy(
                homeState = home,
                homeStatusVersion = homeStatusVersion,
                screenModel = screenModel,
                homeLoading = false,
                homeError = null,
                matchmakingBlockedReason = retainedLocalBlocker,
                matchmakingSearchPhase = searchPhaseAfterHomeLoad(
                    ready = ready,
                    screenModel = screenModel,
                ),
            ),
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

        if (!home.canRemainInHomeForProfileStatus() && !ready.home.allowDraftHomeWithoutInteractions) {
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
            is HomeRoute.OpenSecondChat -> onOpenSecondChat(
                ready.session,
                route.connectionId,
                route.matchId,
                route.partnerName,
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

    private fun ApiError.isVisualAdvancementLimitError(): Boolean {
        if (this !is ApiError.Backend) return false
        return backendErrorCode == BackendErrorCode.VisualAdvancementLimitReached
    }

    private fun ApiError.isNormalMatchmakingAvailabilityError(): Boolean =
        isVisualAdvancementLimitError() || isActiveInteractionLimitError()
}

private fun ProvisionedSession.withProfileStatusFrom(home: HomeState): ProvisionedSession {
    val status = home.profileStatus ?: return this
    val snapshot = profileSnapshot as? com.reals.app.domain.model.ProfileSnapshot.Found ?: return this
    if (snapshot.profile.status == status) return this
    return copy(
        profileSnapshot = com.reals.app.domain.model.ProfileSnapshot.Found(
            snapshot.profile.copy(status = status),
        ),
    )
}

private fun RealsRootUiState.Ready.withoutStaleHomeForKnownDraftProfile(): RealsRootUiState.Ready {
    val snapshot = session.profileSnapshot as? com.reals.app.domain.model.ProfileSnapshot.Found ?: return this
    if (snapshot.profile.status != ProfileStatus.Draft) return this
    if (home.homeState?.profileStatus != ProfileStatus.Active && home.screenModel?.matchmaking?.canSearch != true) {
        return this
    }

    return copy(
        home = home.copy(
            homeState = null,
            screenModel = null,
            matchmakingBlockedReason = null,
            matchmakingSearchPhase = MatchmakingSearchUiPhase.Idle,
        ),
    )
}

internal fun HomeStatus.isHomeWakeUpDue(): Boolean {
    val nextRefreshAtInstant = backendInstantOrNull(nextRefreshAt) ?: return false
    val serverTimeInstant = backendInstantOrNull(serverTime) ?: return false
    return !serverTimeInstant.isBefore(nextRefreshAtInstant)
}
