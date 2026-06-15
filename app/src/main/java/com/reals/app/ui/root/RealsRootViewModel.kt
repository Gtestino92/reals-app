package com.reals.app.ui.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.core.security.TextSafety
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.di.AppContainer
import com.reals.app.di.RealsRootDependencies
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.BackendUserStatus
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.VisualDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RealsRootViewModel(
    dependencies: RealsRootDependencies,
) : ViewModel() {
    private val authRepository = dependencies.session.authRepository
    private val provisionAndLoadProfile = dependencies.session.provisionAndLoadProfile
    private val getMeUseCase = dependencies.session.getMe
    private val reactivateAccountUseCase = dependencies.account.reactivateAccount
    private val deleteAccountUseCase = dependencies.account.deleteAccount
    private val createProfileUseCase = dependencies.profile.createProfile
    private val updateProfileUseCase = dependencies.profile.updateProfile
    private val updateMatchFiltersUseCase = dependencies.profile.updateMatchFilters
    private val getProfilePhotosUseCase = dependencies.profile.getProfilePhotos
    private val addMockProfilePhotoUseCase = dependencies.profile.addMockProfilePhoto
    private val addProfilePhotoFileUseCase = dependencies.profile.addProfilePhotoFile
    private val replaceMockProfilePhotoUseCase = dependencies.profile.replaceMockProfilePhoto
    private val replaceProfilePhotoFileUseCase = dependencies.profile.replaceProfilePhotoFile
    private val deleteProfilePhotoUseCase = dependencies.profile.deleteProfilePhoto
    private val activateProfileUseCase = dependencies.profile.activateProfile
    private val enqueueMatchmakingUseCase = dependencies.home.enqueueMatchmaking
    private val getHomeUseCase = dependencies.home.getHome
    private val leaveQueueUseCase = dependencies.home.leaveQueue
    private val getFirstChatForMatchUseCase = dependencies.firstChat.getFirstChatForMatch
    private val submitChatDecisionUseCase = dependencies.firstChat.submitChatDecision
    private val getChatExitRequestsUseCase = dependencies.firstChat.getChatExitRequests
    private val requestMutualChatExitUseCase = dependencies.firstChat.requestMutualChatExit
    private val acceptChatExitRequestUseCase = dependencies.firstChat.acceptChatExitRequest
    private val rejectChatExitRequestUseCase = dependencies.firstChat.rejectChatExitRequest
    private val cancelChatUseCase = dependencies.firstChat.cancelChat
    private val safetyCancelChatUseCase = dependencies.firstChat.safetyCancelChat
    private val getVisualProfileUseCase = dependencies.visualApproval.getVisualProfile
    private val firstChatCoordinator = FirstChatCoordinator(dependencies.firstChat)
    private val visualApprovalCoordinator = VisualApprovalCoordinator(dependencies.visualApproval)
    private val _uiState = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Checking)
    val uiState: StateFlow<RealsRootUiState> = _uiState.asStateFlow()
    private var lastSearchLocation: SearchLocationInput? = null
    private val locallyHiddenPendingChatMatchIds = mutableSetOf<String>()
    private val locallyHiddenVisualMatchIds = mutableSetOf<String>()

    init {
        refreshSession()
    }

    fun refreshSession() {
        if (!authRepository.isConfigured()) {
            _uiState.value = RealsRootUiState.MissingFirebase(FirebaseAuthRepository.firebaseMissingMessage)
            return
        }
        if (!authRepository.hasSignedInUser()) {
            _uiState.value = RealsRootUiState.Login()
            return
        }
        loadBackendSession()
    }

    fun signIn(email: String, password: String) {
        authenticate(email, password) { cleanEmail, cleanPassword ->
            authRepository.signIn(cleanEmail, cleanPassword)
        }
    }

    fun signUp(email: String, password: String) {
        authenticate(email, password) { cleanEmail, cleanPassword ->
            authRepository.signUp(cleanEmail, cleanPassword)
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = RealsRootUiState.Login()
    }

    fun deleteAccount() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(
                account = current.account.copy(
                    deletingAccount = true,
                    accountDeleteError = null,
                ),
            )

            when (val result = deleteAccountUseCase()) {
                is ApiResult.Success -> {
                    _uiState.value = RealsRootUiState.AccountDeletionScheduled(
                        deletionFinalizesAt = result.value.deletionFinalizesAt,
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        account = current.account.copy(
                            deletingAccount = false,
                            accountDeleteError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun reactivateAccount() {
        val current = _uiState.value as? RealsRootUiState.AccountDeletionPending ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(reactivating = true, error = null)
            when (val result = reactivateAccountUseCase()) {
                is ApiResult.Success -> loadBackendSessionForActiveUser(result.value)
                is ApiResult.Failure -> _uiState.value = current.copy(
                    reactivating = false,
                    error = result.error,
                )
            }
        }
    }

    fun refreshHomeState() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            loadHomeForReady(
                ready = current.copy(
                    home = current.home.copy(
                        homeLoading = true,
                        homeError = null,
                        homeMessage = null,
                    ),
                ),
                autoNavigateEngagements = current.homeState?.queue?.inQueue == true,
            )
        }
    }

    fun pollHomeStateSilently() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            when (val homeResult = getHomeUseCase()) {
                is ApiResult.Success -> {
                    val latest = _uiState.value as? RealsRootUiState.Ready ?: return@launch

                    routeFromHomeState(
                        ready = latest.copy(
                            home = latest.home.copy(
                                homeState = homeResult.value.withLocallyHiddenMatchesAndPrunedState(),
                                homeLoading = false,
                            ),
                        ),
                        autoNavigateEngagements = latest.homeState?.queue?.inQueue == true,
                    )
                }

                is ApiResult.Failure -> {
                    // polling silencioso: no pisar UI
                }
            }
        }
    }

    fun enqueueMatchmaking(location: SearchLocationInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            lastSearchLocation = location
            val pending = current.copy(
                home = current.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                    matchmakingBlockedReason = null,
                ),
            )
            _uiState.value = pending
            when (val result = enqueueMatchmakingUseCase(location)) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        home = pending.home.copy(
                            homeLoading = true,
                            homeMessage = null,
                        ),
                    ),
                    autoNavigateEngagements = true,
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    home = pending.home.copy(
                        homeLoading = false,
                        homeError = result.error,
                        matchmakingBlockedReason = result.error.takeIf {
                            it is ApiError.Backend && it.code == "ACTIVE_MATCH_LIMIT_REACHED"
                        },
                    ),
                )
            }
        }
    }

    fun leaveMatchmakingQueue() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            val pending = current.copy(
                home = current.home.copy(
                    homeLoading = true,
                    homeError = null,
                    homeMessage = null,
                ),
            )
            _uiState.value = pending
            when (val result = leaveQueueUseCase()) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        home = pending.home.copy(
                            homeLoading = true,
                            homeMessage = null,
                        ),
                    ),
                    autoNavigateEngagements = false,
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    home = pending.home.copy(
                        homeLoading = false,
                        homeError = result.error,
                    ),
                )
            }
        }
    }

    fun openProfileManagement() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        _uiState.value = current.copy(editingActiveProfile = true)
        loadProfilePhotos()
    }

    fun closeProfileManagement() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile
        if (profile?.status == ProfileStatus.Active) {
            viewModelScope.launch {
                loadHomeForReady(
                    current.copy(
                        editingActiveProfile = false,
                        home = current.home.copy(
                            homeLoading = true,
                            homeError = null,
                            homeMessage = null,
                        ),
                    )
                )
            }
        } else {
            _uiState.value = current.copy(editingActiveProfile = false)
        }
    }

    fun openFirstChat(matchId: String, chatId: String? = null) {
        val session = when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> current.session
            is RealsRootUiState.FirstChat -> current.session
            else -> return
        }

        openFirstChat(
            session = session,
            matchId = matchId,
            chatId = chatId,
        )
    }

    private fun openFirstChat(
        session: ProvisionedSession,
        matchId: String,
        chatId: String? = null,
    ) {
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = RealsRootUiState.FirstChat(
                session = session,
                matchId = cleanMatchId,
                chatId = chatId,
                loading = true,
            )

            when (val result = firstChatCoordinator.load(session, cleanMatchId, chatId)) {
                is FirstChatLoadResult.Show -> _uiState.value = result.state
                is FirstChatLoadResult.RouteHome -> loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = session,
                        home = HomeUiState(
                            homeLoading = true,
                            homeMessage = result.message,
                        ),
                    ),
                    autoNavigateEngagements = false,
                )
            }
        }
    }

    fun closeFirstChat() {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        _uiState.value = RealsRootUiState.Ready(
            session = current.session,
            home = HomeUiState(homeLoading = true),
        )
        refreshHomeState()
    }

    fun openVisualApproval(matchId: String) {
        val session = when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> current.session
            is RealsRootUiState.FirstChat -> current.session
            is RealsRootUiState.VisualApproval -> current.session
            else -> return
        }
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return
        if (cleanMatchId in locallyHiddenVisualMatchIds) {
            viewModelScope.launch {
                loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = session,
                        home = HomeUiState(homeLoading = true),
                    ),
                    autoNavigateEngagements = false,
                )
            }
            return
        }

        viewModelScope.launch {
            loadVisualApprovalState(
                session = session,
                matchId = cleanMatchId,
                initialMatch = null,
            )
        }
    }

    fun closeVisualApproval() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = current.session,
                    home = HomeUiState(homeLoading = true),
                ),
                autoNavigateEngagements = false,
            )
        }
    }

    fun refreshVisualApproval() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            loadVisualApprovalState(
                session = current.session,
                matchId = current.matchId,
                initialMatch = current.match,
                previous = current.copy(refreshing = true, error = null, message = null),
            )
        }
    }

    fun openConnectionPartnerProfile(matchId: String) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return

        viewModelScope.launch {
            loadPartnerProfile(
                session = current.session,
                matchId = cleanMatchId,
            )
        }
    }

    fun refreshPartnerProfile() {
        val current = _uiState.value as? RealsRootUiState.PartnerProfile ?: return
        viewModelScope.launch {
            loadPartnerProfile(
                session = current.session,
                matchId = current.matchId,
                previous = current.copy(refreshing = true, error = null),
            )
        }
    }

    fun closePartnerProfile() {
        val current = _uiState.value as? RealsRootUiState.PartnerProfile ?: return
        viewModelScope.launch {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = current.session,
                    home = HomeUiState(homeLoading = true),
                ),
                autoNavigateEngagements = false,
            )
        }
    }

    fun refreshFirstChat(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (current.refreshing || current.sending || current.actionLoading) return
        if (current.chat == null) return openFirstChat(current.matchId, current.chatId)

        viewModelScope.launch {
            _uiState.value = current.copy(
                refreshing = true,
                error = if (silent) current.error else null,
                message = if (silent) current.message else null,
            )
            when (val result = firstChatCoordinator.refresh(current, silent)) {
                is FirstChatRefreshResult.Show -> _uiState.value = result.state
                is FirstChatRefreshResult.Reopen -> openFirstChat(result.matchId, result.chatId)
                is FirstChatRefreshResult.Closed -> {
                    locallyHiddenPendingChatMatchIds += current.matchId
                    routeAfterFirstChatClosed(current.session, result.matchState)
                }
                FirstChatRefreshResult.ExitResolved -> {
                    locallyHiddenPendingChatMatchIds += current.matchId
                    reenterMatchmakingOrLoadHome(current.session)
                }
            }
        }
    }

    fun returnToHomeFromPendingEngagement() {
        val current = _uiState.value as? RealsRootUiState.PendingEngagement ?: return
        _uiState.value = RealsRootUiState.Ready(
            session = current.session,
            home = HomeUiState(homeLoading = true),
        )
        refreshHomeState()
    }

    fun sendFirstChatMessage(content: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (current.chat == null) return
        val cleanContent = TextSafety.normalizeMultiline(content, maxLength = 1_000)
        if (cleanContent.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanContent)) {
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El mensaje no es valido."),
                message = null,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(sending = true, error = null, message = null)
            _uiState.value = firstChatCoordinator.sendMessage(current, cleanContent)
        }
    }

    fun submitFirstChatDecision(decision: ChatContinueDecision) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        val chat = current.chat
        if (chat != null && chat.myDecision != ChatDecisionState.Pending) {
            _uiState.value = current.copy(
                message = "Ya registramos tu decision para este chat.",
                error = null,
            )
            return
        }
        viewModelScope.launch {
            val pending = current.copy(actionLoading = true, error = null, message = null)
            _uiState.value = pending
            when (val result = submitChatDecisionUseCase(current.matchId, decision)) {
                is ApiResult.Success -> {
                    when (val state = result.value.state) {
                        MatchState.ChatActive -> {
                            if (decision == ChatContinueDecision.Approved) {
                                locallyHiddenPendingChatMatchIds += current.matchId
                                reenterMatchmakingOrLoadHome(current.session)
                            } else {
                                _uiState.value = pending.copy(
                                    match = result.value,
                                    actionLoading = false,
                                    message = firstChatDecisionMessage(state),
                                )
                            }
                        }

                        MatchState.VisualPhase,
                        MatchState.ChatRejected,
                        MatchState.Expired,
                        MatchState.VisualApproved,
                        MatchState.VisualRejected -> {
                            locallyHiddenPendingChatMatchIds += current.matchId
                            routeAfterFirstChatClosed(current.session, state)
                        }

                        is MatchState.Unknown -> _uiState.value = pending.copy(
                            match = result.value,
                            actionLoading = false,
                            message = firstChatDecisionMessage(state),
                        )
                    }
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    actionLoading = false,
                    error = result.error,
                )
            }
        }
    }

    fun requestMutualChatExit() {
        runChatExitAction { chatId ->
            requestMutualChatExitUseCase(chatId, ChatExitReason.NoLongerInterested, null)
        }
    }

    fun cancelChatUnilaterally() {
        runChatExitAction { chatId ->
            cancelChatUseCase(chatId, ChatExitReason.NoLongerInterested, null)
        }
    }

    fun safetyCancelChat(details: String) {
        val cleanDetails = TextSafety.normalizeMultiline(details, maxLength = 1_000)
        if (cleanDetails.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanDetails)) {
            val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El detalle del reporte no es valido."),
                message = null,
            )
            return
        }
        runChatExitAction { chatId ->
            safetyCancelChatUseCase(chatId, ChatExitReason.InappropriateBehavior, cleanDetails)
        }
    }

    fun acceptChatExitRequest(exitRequestId: String) {
        runChatExitAction { chatId ->
            acceptChatExitRequestUseCase(chatId, exitRequestId)
        }
    }

    fun rejectChatExitRequest(exitRequestId: String) {
        runChatExitAction(closeOnSuccess = true) { chatId ->
            rejectChatExitRequestUseCase(chatId, exitRequestId)
        }
    }

    fun timeoutChatExitRequest(exitRequestId: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        val request = current.exitRequests.firstOrNull {
            it.id == exitRequestId && it.status == ChatExitRequestStatus.Pending
        } ?: return
        runChatExitAction(closeOnSuccess = true) { chatId ->
            cancelChatUseCase(
                chatId = chatId,
                reason = request.reason ?: ChatExitReason.NoLongerInterested,
                details = "Solicitud de salida consensuada sin respuesta.",
            )
        }
    }

    fun saveMyVisualPersonalMessage(message: String) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        val cleanMessage = TextSafety.normalizeMultiline(message, maxLength = 280)
        if (cleanMessage.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanMessage)) {
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El mensaje personal no es valido."),
                message = null,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(writingMessage = true, error = null, message = null)
            _uiState.value = visualApprovalCoordinator.savePersonalMessage(current, cleanMessage)
        }
    }

    fun submitVisualDecision(decision: VisualDecision) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            val pending = current.copy(deciding = true, error = null, message = null)
            _uiState.value = pending
            when (val result = visualApprovalCoordinator.submitDecision(current.matchId, decision)) {
                is ApiResult.Success -> {
                    locallyHiddenVisualMatchIds += current.matchId
                    when (result.value.state) {
                        MatchState.VisualPhase -> loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                home = HomeUiState(homeLoading = true),
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.VisualApproved -> loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                home = HomeUiState(homeLoading = true),
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.VisualRejected,
                        MatchState.ChatRejected,
                        MatchState.Expired -> loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                home = HomeUiState(homeLoading = true),
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.ChatActive,
                        is MatchState.Unknown -> _uiState.value = pending.copy(
                            match = result.value,
                            deciding = false,
                            message = "Guardamos tu decision.",
                        )
                    }
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    deciding = false,
                    error = result.error,
                )
            }
        }
    }

    fun createProfile(input: CreateProfileInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(
                profile = current.profile.copy(
                    creatingProfile = true,
                    profileCreateError = null,
                ),
            )
            when (val result = createProfileUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = current.copy(
                        session = current.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profile = current.profile.copy(
                            creatingProfile = false,
                            profileCreateError = null,
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        profile = current.profile.copy(
                            creatingProfile = false,
                            profileCreateError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun updateProfile(input: UpdateProfileInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                profile = cleared.profile.copy(updatingProfile = true),
            )
            _uiState.value = pending
            when (val result = updateProfileUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profile = pending.profile.copy(
                            updatingProfile = false,
                            profileUpdateMessage = "Perfil actualizado.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        profile = pending.profile.copy(
                            updatingProfile = false,
                            profileUpdateError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun updateMatchFilters(input: UpdateMatchFiltersInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                profile = cleared.profile.copy(updatingMatchFilters = true),
            )
            _uiState.value = pending
            when (val result = updateMatchFiltersUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        profile = pending.profile.copy(
                            updatingMatchFilters = false,
                            matchFiltersMessage = "Filtros actualizados.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        profile = pending.profile.copy(
                            updatingMatchFilters = false,
                            matchFiltersError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun loadProfilePhotos() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        if (current.session.profileSnapshot !is ProfileSnapshot.Found) return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                photos = cleared.photos.copy(loadingPhotos = true),
            )
            _uiState.value = pending
            when (val result = getProfilePhotosUseCase.invoke()) {
                is ApiResult.Success -> _uiState.value = pending.copy(
                    photos = pending.photos.copy(
                        loadingPhotos = false,
                        profilePhotos = result.value.sortedBy { it.position },
                    ),
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    photos = pending.photos.copy(
                        loadingPhotos = false,
                        profilePhotosError = result.error,
                    ),
                )
            }
        }
    }

    fun addMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                photos = cleared.photos.copy(addingPhoto = true),
            )
            _uiState.value = pending
            when (
                val result = addMockProfilePhotoUseCase.invoke(
                    profile = profile,
                    position = position,
                    isPersonPhoto = isPersonPhoto,
                    isFullBody = isFullBody,
                )
            ) {
                is ApiResult.Success -> {
                    when (val refreshedSession = provisionAndLoadProfile()) {
                        is ApiResult.Success -> {
                            val refreshedPhotos = getProfilePhotosUseCase.invoke()
                            val updatedPhotos = (refreshedPhotos as? ApiResult.Success)?.value
                                ?.sortedBy { it.position }
                                ?: pending.profilePhotos
                            _uiState.value = pending.copy(
                                session = refreshedSession.value,
                                photos = pending.photos.copy(
                                    profilePhotos = updatedPhotos,
                                    profilePhotosError = null,
                                    addingPhoto = false,
                                    photoActionMessage = "Foto agregada correctamente.",
                                ),
                            )
                        }

                        is ApiResult.Failure -> _uiState.value = pending.copy(
                            photos = pending.photos.copy(
                                addingPhoto = false,
                                photoActionError = refreshedSession.error,
                            ),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            addingPhoto = false,
                            photoActionError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun addProfilePhotoFile(position: Int, fileUri: Uri) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                photos = cleared.photos.copy(addingPhoto = true),
            )
            _uiState.value = pending
            when (val result = addProfilePhotoFileUseCase.invoke(fileUri = fileUri, position = position)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
                    successMessage = "Foto subida correctamente.",
                )

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            addingPhoto = false,
                            photoActionError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun replaceMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                photos = cleared.photos.copy(addingPhoto = true),
            )
            _uiState.value = pending
            when (
                val result = replaceMockProfilePhotoUseCase.invoke(
                    profile = profile,
                    position = position,
                    isPersonPhoto = isPersonPhoto,
                    isFullBody = isFullBody,
                )
            ) {
                is ApiResult.Success -> {
                    when (val refreshedSession = provisionAndLoadProfile()) {
                        is ApiResult.Success -> {
                            val refreshedPhotos = getProfilePhotosUseCase.invoke()
                            val updatedPhotos = (refreshedPhotos as? ApiResult.Success)?.value
                                ?.sortedBy { it.position }
                                ?: pending.profilePhotos
                            _uiState.value = pending.copy(
                                session = refreshedSession.value,
                                photos = pending.photos.copy(
                                    profilePhotos = updatedPhotos,
                                    profilePhotosError = null,
                                    addingPhoto = false,
                                    photoActionMessage = "Foto reemplazada correctamente.",
                                ),
                            )
                        }

                        is ApiResult.Failure -> _uiState.value = pending.copy(
                            photos = pending.photos.copy(
                                addingPhoto = false,
                                photoActionError = refreshedSession.error,
                            ),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            addingPhoto = false,
                            photoActionError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                photos = cleared.photos.copy(addingPhoto = true),
            )
            _uiState.value = pending
            when (val result = replaceProfilePhotoFileUseCase.invoke(photoId = photoId, fileUri = fileUri)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
                    successMessage = "Foto reemplazada correctamente.",
                )

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            addingPhoto = false,
                            photoActionError = result.error,
                        ),
                    )
                }
            }
        }
    }

    val hasLocallyHiddenInteractions: Boolean
        get() = locallyHiddenPendingChatMatchIds.isNotEmpty() ||
                locallyHiddenVisualMatchIds.isNotEmpty()

    fun deleteProfilePhoto(photoId: String, position: Int) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                photos = cleared.photos.copy(addingPhoto = true),
            )
            _uiState.value = pending
            when (val result = deleteProfilePhotoUseCase.invoke(photoId)) {
                is ApiResult.Success -> {
                    val refreshedPhotos = getProfilePhotosUseCase.invoke()
                    val updatedPhotos = (refreshedPhotos as? ApiResult.Success)?.value
                        ?.sortedBy { it.position }
                        ?: pending.profilePhotos.filterNot { it.id == photoId }
                    _uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        photos = pending.photos.copy(
                            profilePhotos = updatedPhotos,
                            profilePhotosError = null,
                            addingPhoto = false,
                            photoActionMessage = "Foto eliminada.",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        photos = pending.photos.copy(
                            addingPhoto = false,
                            photoActionError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun activateProfile() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val cleared = current.clearProfileFeedback()
            val pending = cleared.copy(
                profile = cleared.profile.copy(activatingProfile = true),
            )
            _uiState.value = pending
            when (val result = activateProfileUseCase.invoke()) {
                is ApiResult.Success -> {
                    val updatedSession = pending.session.copy(
                        profileSnapshot = ProfileSnapshot.Found(result.value.profile),
                    )
                    _uiState.value = RealsRootUiState.ActivationComplete(
                        session = updatedSession,
                        result = result.value,
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        profile = pending.profile.copy(
                            activatingProfile = false,
                            profileActivationError = result.error,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun refreshAfterPhotoMutation(
        previous: RealsRootUiState.Ready,
        successMessage: String,
    ) {
        val refreshedPhotos = getProfilePhotosUseCase.invoke()
        val refreshedSession = provisionAndLoadProfile()

        if (refreshedPhotos is ApiResult.Success) {
            _uiState.value = previous.copy(
                session = (refreshedSession as? ApiResult.Success)?.value ?: previous.session,
                photos = previous.photos.copy(
                    profilePhotos = refreshedPhotos.value.sortedBy { it.position },
                    profilePhotosError = null,
                    addingPhoto = false,
                    photoActionMessage = successMessage,
                    photoActionError = null,
                ),
            )
            return
        }

        _uiState.value = previous.copy(
            session = (refreshedSession as? ApiResult.Success)?.value ?: previous.session,
            photos = previous.photos.copy(
                addingPhoto = false,
                photoActionError = (refreshedPhotos as ApiResult.Failure).error,
            ),
        )
    }

    private suspend fun loadVisualApprovalState(
        session: ProvisionedSession,
        matchId: String,
        initialMatch: Match?,
        previous: RealsRootUiState.VisualApproval? = null,
    ) {
        val loadingState = previous ?: RealsRootUiState.VisualApproval(
            session = session,
            matchId = matchId,
            match = initialMatch,
            loading = true,
        )
        _uiState.value = loadingState

        when (
            val result = visualApprovalCoordinator.load(
                session = session,
                matchId = matchId,
                initialMatch = initialMatch,
                previous = previous,
                locallyHidden = matchId in locallyHiddenVisualMatchIds,
            )
        ) {
            is VisualApprovalLoadResult.Show -> _uiState.value = result.state
            is VisualApprovalLoadResult.RouteHome -> loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = session,
                    home = HomeUiState(
                        homeLoading = true,
                        homeMessage = result.message,
                    ),
                ),
                autoNavigateEngagements = false,
            )
        }
    }

    private suspend fun loadPartnerProfile(
        session: ProvisionedSession,
        matchId: String,
        previous: RealsRootUiState.PartnerProfile? = null,
    ) {
        val loadingState = previous ?: RealsRootUiState.PartnerProfile(
            session = session,
            matchId = matchId,
            loading = true,
        )
        _uiState.value = loadingState

        when (val result = getVisualProfileUseCase(matchId)) {
            is ApiResult.Success -> _uiState.value = loadingState.copy(
                profile = result.value,
                loading = false,
                refreshing = false,
                error = null,
            )

            is ApiResult.Failure -> _uiState.value = loadingState.copy(
                loading = false,
                refreshing = false,
                error = result.error,
            )
        }
    }

    private suspend fun loadHomeForReady(
        ready: RealsRootUiState.Ready,
        publishLoadingState: Boolean = true,
        autoNavigateEngagements: Boolean = false,
        matchmakingBlockedReason: Boolean = false
    ) {
        if (publishLoadingState) {
            _uiState.value = ready.copy(
                home = ready.home.copy(
                    homeLoading = true,
                    homeError = null,
                ),
            )
        }

        when (val homeResult = getHomeUseCase()) {
            is ApiResult.Success -> {
                val visibleHomeState = homeResult.value.withLocallyHiddenMatchesAndPrunedState()

                routeFromHomeState(
                    ready = ready.copy(
                        home = ready.home.copy(
                            homeState = visibleHomeState,
                            homeLoading = false,
                            homeError = null,
                        ),
                    ),
                    autoNavigateEngagements = autoNavigateEngagements,
                )
            }

            is ApiResult.Failure -> {
                _uiState.value = ready.copy(
                    home = ready.home.copy(
                        homeLoading = false,
                        homeError = homeResult.error,
                        homeMessage = null,
                    ),
                )
            }
        }
    }

    private suspend fun routeFromHomeState(
        ready: RealsRootUiState.Ready,
        autoNavigateEngagements: Boolean,
    ) {
        val home = ready.homeState ?: run {
            _uiState.value = ready
            return
        }

        if (home.profileStatus != ProfileStatus.Active) {
            loadBackendSessionForActiveUser(ready.session.user)
            return
        }

        if (!autoNavigateEngagements) {
            _uiState.value = ready
            return
        }

        val firstChatMatch = home.activeMatches.firstOrNull {
            it.matchState == MatchState.ChatActive && it.firstChat != null
        }

        if (firstChatMatch?.firstChat != null) {
            openFirstChat(
                session = ready.session,
                matchId = firstChatMatch.matchId,
                chatId = firstChatMatch.firstChat.chatId,
            )
            return
        }

        _uiState.value = ready
    }

    private fun pendingConnectionTitle(state: ConnectionState): String = when (state) {
        ConnectionState.SchedulingPhase -> "Coordinacion pendiente"
        ConnectionState.SecondChatScheduled -> "Esperando segundo chat"
        ConnectionState.SecondChatAvailable,
        ConnectionState.SecondChat -> "Segundo chat pendiente"
        ConnectionState.Closed -> "Conexion cerrada"
        is ConnectionState.Unknown -> "Conexion no disponible"
    }

    private fun pendingConnectionBody(connection: HomeConnection): String {
        val secondChat = connection.secondChat
        return when (connection.connectionState) {
            ConnectionState.SchedulingPhase ->
                "Tenes una conexion esperando coordinacion. Esta parte de la experiencia todavia no esta disponible en la app."
            ConnectionState.SecondChatScheduled ->
                "Tenes un segundo chat programado. La pantalla de espera todavia no esta disponible en la app."
            ConnectionState.SecondChatAvailable,
            ConnectionState.SecondChat ->
                if (secondChat == null) {
                    "Tu segundo chat esta casi listo. Esta parte de la experiencia todavia no esta disponible en la app."
                } else {
                    "Tenes un segundo chat pendiente. Esta pantalla todavia no esta disponible en la app."
                }
            ConnectionState.Closed ->
                "Esta conexion ya termino. Actualiza Home para ver que sigue."
            is ConnectionState.Unknown ->
                "Esta conexion esta en un estado que todavia no podemos mostrar en la app."
        }
    }

    private fun HomeState.withLocallyHiddenMatches(): HomeState = copy(
        activeMatches = activeMatches.filterNot { match ->
            (match.matchState == MatchState.ChatActive &&
                match.matchId in locallyHiddenPendingChatMatchIds) ||
                (match.matchState == MatchState.VisualPhase &&
                    match.matchId in locallyHiddenVisualMatchIds)
        },
    )

    private fun HomeState.withLocallyHiddenMatchesAndPrunedState(): HomeState {
        val stillChatActiveIds = activeMatches
            .filter { it.matchState == MatchState.ChatActive }
            .map { it.matchId }
            .toSet()

        val stillVisualPhaseIds = activeMatches
            .filter { it.matchState == MatchState.VisualPhase }
            .map { it.matchId }
            .toSet()

        locallyHiddenPendingChatMatchIds.retainAll(stillChatActiveIds)
        locallyHiddenVisualMatchIds.retainAll(stillVisualPhaseIds)

        return copy(
            activeMatches = activeMatches.filterNot { match ->
                match.matchState == MatchState.ChatActive &&
                        match.matchId in locallyHiddenPendingChatMatchIds ||
                        match.matchState == MatchState.VisualPhase &&
                        match.matchId in locallyHiddenVisualMatchIds
            }
        )
    }

    private suspend fun reenterMatchmakingOrLoadHome(session: ProvisionedSession) {
        val location = lastSearchLocation
        val ready = RealsRootUiState.Ready(
            session = session,
            home = HomeUiState(homeLoading = true),
        )

        if (location == null) {
            loadHomeForReady(
                ready = ready,
                autoNavigateEngagements = false,
            )
            return
        }

        when (val enqueueResult = enqueueMatchmakingUseCase(location)) {
            is ApiResult.Success -> loadHomeForReady(
                ready = ready.copy(
                    home = ready.home.copy(matchmakingBlockedReason = null),
                ),
                autoNavigateEngagements = true,
            )

            is ApiResult.Failure -> {
                val reachedLimit = enqueueResult.error is ApiError.Backend &&
                        enqueueResult.error.code == "ACTIVE_MATCH_LIMIT_REACHED"

                loadHomeForReady(
                    ready = ready.copy(
                        home = ready.home.copy(
                            homeMessage = if (reachedLimit) {
                            "Aprobaste el chat. Ya tenés el máximo de interacciones activas."
                        } else {
                            "Aprobaste el chat. No pudimos volver a iniciar la búsqueda automáticamente."
                        },
                            matchmakingBlockedReason = enqueueResult.error,
                        ),
                    ),
                    autoNavigateEngagements = false,
                )
            }
        }
    }

    private suspend fun routeAfterFirstChatClosed(session: ProvisionedSession, state: MatchState?) {
        if (state == MatchState.VisualPhase) {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = session,
                    home = HomeUiState(
                        homeLoading = true,
                        homeMessage = firstChatExitMessage(state),
                    ),
                ),
                autoNavigateEngagements = false,
            )
            return
        }

        reenterMatchmakingOrLoadHome(session)
    }

    private fun runChatExitAction(
        closeOnSuccess: Boolean = false,
        action: suspend (chatId: String) -> ApiResult<*>,
    ) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        val chat = current.chat ?: return

        viewModelScope.launch {
            val pending = current.copy(actionLoading = true, error = null, message = null)
            _uiState.value = pending
            when (val result = action(chat.id)) {
                is ApiResult.Success -> {
                    val outcome = result.value as? ChatExitOutcome
                    if (closeOnSuccess || (outcome != null && outcome.chat.status != ChatStatus.Active)) {
                        locallyHiddenPendingChatMatchIds += current.matchId
                        routeAfterFirstChatClosed(current.session, null)
                        return@launch
                    }

                    val chatResult = getFirstChatForMatchUseCase(current.matchId)
                    val exitsResult = getChatExitRequestsUseCase(chat.id)
                    _uiState.value = pending.copy(
                        chat = (chatResult as? ApiResult.Success)?.value ?: pending.chat,
                        exitRequests = (exitsResult as? ApiResult.Success)?.value ?: pending.exitRequests,
                        actionLoading = false,
                        message = "Listo, enviamos tu solicitud.",
                        error = (chatResult as? ApiResult.Failure)?.error ?: (exitsResult as? ApiResult.Failure)?.error,
                    )
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    actionLoading = false,
                    error = result.error,
                )
            }
        }
    }

    private fun authenticate(
        email: String,
        password: String,
        action: suspend (email: String, password: String) -> AuthOperationResult,
    ) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _uiState.value = RealsRootUiState.Login(error = "Email y password son requeridos.")
            return
        }
        viewModelScope.launch {
            _uiState.value = RealsRootUiState.Login(loading = true)
            when (val result = action(cleanEmail, password)) {
                AuthOperationResult.Success -> loadBackendSession()
                is AuthOperationResult.Failure -> _uiState.value = RealsRootUiState.Login(error = result.message)
            }
        }
    }

    private fun loadBackendSession() {
        viewModelScope.launch {
            _uiState.value = RealsRootUiState.LoadingSession(authRepository.currentUserEmail())
            when (val userResult = getMeUseCase()) {
                is ApiResult.Success -> when (userResult.value.status) {
                    BackendUserStatus.Active -> loadBackendSessionForActiveUser(userResult.value)
                    BackendUserStatus.Deleted -> _uiState.value = RealsRootUiState.AccountDeletionPending(
                        user = userResult.value,
                    )

                    is BackendUserStatus.Unknown -> _uiState.value = RealsRootUiState.Failure(
                        ApiError.Unexpected("No pudimos leer el estado de tu cuenta.")
                    )
                }

                is ApiResult.Failure -> {
                    val backend = userResult.error as? ApiError.Backend
                    if (backend.shouldProvisionAfterGetMeFailure()) {
                        provisionAndLoadBackendSession()
                    } else {
                        handleSessionLoadFailure(userResult.error)
                    }
                }
            }
        }
    }

    private suspend fun provisionAndLoadBackendSession() {
        when (val result = provisionAndLoadProfile()) {
            is ApiResult.Success -> showReadySession(result.value)
            is ApiResult.Failure -> handleSessionLoadFailure(result.error)
        }
    }

    private suspend fun loadBackendSessionForActiveUser(user: BackendUser) {
        when (val result = provisionAndLoadProfile.loadProfileFor(user)) {
            is ApiResult.Success -> showReadySession(result.value)
            is ApiResult.Failure -> handleSessionLoadFailure(result.error)
        }
    }

    private suspend fun showReadySession(session: ProvisionedSession) {
        val snapshot = session.profileSnapshot
        if (snapshot is ProfileSnapshot.Found) {
            if (snapshot.profile.status == ProfileStatus.Active) {
                loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = session,
                        home = HomeUiState(homeLoading = true),
                    ),
                    publishLoadingState = false,
                    autoNavigateEngagements = true,
                )
                return
            }

            _uiState.value = RealsRootUiState.Ready(
                session = session,
                photos = PhotoManagementUiState(loadingPhotos = true),
            )
            when (val photos = getProfilePhotosUseCase.invoke()) {
                is ApiResult.Success -> _uiState.value = RealsRootUiState.Ready(
                    session = session,
                    photos = PhotoManagementUiState(
                        loadingPhotos = false,
                        profilePhotos = photos.value.sortedBy { it.position },
                    ),
                )

                is ApiResult.Failure -> {
                    if (photos.error.isAccountDeleted()) {
                        showAccountDeletionPendingFromBackend()
                    } else {
                        _uiState.value = RealsRootUiState.Ready(
                            session = session,
                            photos = PhotoManagementUiState(
                                loadingPhotos = false,
                                profilePhotosError = photos.error,
                            ),
                        )
                    }
                }
            }
        } else {
            _uiState.value = RealsRootUiState.Ready(session)
        }
    }

    private suspend fun handleSessionLoadFailure(error: ApiError) {
        if (error.isAccountDeleted()) {
            showAccountDeletionPendingFromBackend()
            return
        }

        _uiState.value = RealsRootUiState.Failure(error)
    }

    private suspend fun showAccountDeletionPendingFromBackend() {
        when (val userResult = getMeUseCase()) {
            is ApiResult.Success -> when (userResult.value.status) {
                BackendUserStatus.Deleted -> _uiState.value = RealsRootUiState.AccountDeletionPending(userResult.value)
                BackendUserStatus.Active -> loadBackendSessionForActiveUser(userResult.value)
                is BackendUserStatus.Unknown -> _uiState.value = RealsRootUiState.Failure(
                    ApiError.Unexpected("No pudimos leer el estado de tu cuenta.")
                )
            }

            is ApiResult.Failure -> {
                authRepository.signOut()
                _uiState.value = RealsRootUiState.Login(
                    error = "La cuenta esta pendiente de eliminacion. Volve a iniciar sesion para recuperarla."
                )
            }
        }
    }

    private fun ApiError.Backend?.shouldProvisionAfterGetMeFailure(): Boolean {
        if (this == null) return false
        return statusCode == 404 || statusCode == 403
    }
}

class RealsRootViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RealsRootViewModel::class.java)) {
            return RealsRootViewModel(
                dependencies = appContainer.rootDependencies,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}

