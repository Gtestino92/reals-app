package com.reals.app.ui.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.core.security.TextSafety
import com.reals.app.di.AppContainer
import com.reals.app.di.RealsRootDependencies
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.VisualDecision
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RealsRootViewModel(
    dependencies: RealsRootDependencies,
    autoRefreshSession: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Checking)
    private val getProfilePhotosUseCase = dependencies.profile.getProfilePhotos
    private val profileHandler = ProfileOperationHandler(
        uiState = _uiState,
        dependencies = dependencies.profile,
        getProfilePhotosUseCase = getProfilePhotosUseCase,
        scope = viewModelScope,
    )
    private val getVisualProfileUseCase = dependencies.visualApproval.getVisualProfile
    private val firstChatCoordinator = FirstChatCoordinator(dependencies.firstChat)
    private val secondChatCoordinator = SecondChatCoordinator(dependencies.firstChat)
    private val visualApprovalCoordinator = VisualApprovalCoordinator(dependencies.visualApproval)
    private val schedulingCoordinator = SchedulingCoordinator(dependencies.scheduling)
    private lateinit var sessionCoordinator: SessionCoordinator
    private var silentFirstChatRefreshJob: Job? = null
    private var silentSecondChatRefreshJob: Job? = null
    private var silentSchedulingRefreshJob: Job? = null
    private val homeCoordinator = HomeCoordinator(
        uiState = _uiState,
        dependencies = dependencies.home,
        scope = viewModelScope,
        onOpenFirstChat = { session, matchId, chatId -> openFirstChat(session, matchId, chatId) },
        onReloadActiveSession = { user -> sessionCoordinator.loadBackendSessionForActiveUser(user) },
    )
    val uiState: StateFlow<RealsRootUiState> = _uiState.asStateFlow()

    init {
        sessionCoordinator = SessionCoordinator(
            uiState = _uiState,
            dependencies = dependencies.session,
            accountDependencies = dependencies.account,
            scope = viewModelScope,
            onActiveSessionLoaded = { session -> showReadySession(session) },
            onReactivatedSessionLoaded = { session ->
                homeCoordinator.reenterMatchmakingOrLoadHome(session)
            },
        )
        if (autoRefreshSession) {
            refreshSession()
        }
    }

    fun refreshSession() = sessionCoordinator.refreshSession()

    fun signIn(email: String, password: String) = sessionCoordinator.signIn(email, password)

    fun signUp(email: String, password: String) = sessionCoordinator.signUp(email, password)

    fun signOut() = sessionCoordinator.signOut()

    fun deleteAccount() = sessionCoordinator.deleteAccount()

    fun reactivateAccount() = sessionCoordinator.reactivateAccount()

    fun refreshHomeState() {
        homeCoordinator.refreshHomeState()
    }

    fun pollHomeStateSilently() {
        homeCoordinator.pollHomeStateSilently()
    }

    fun handleExternalNotificationOpened(type: String?) {
        if (type != TYPE_VISUAL_REVIEW_AVAILABLE) return

        when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> refreshHomeState()
            is RealsRootUiState.FirstChat -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.SecondChat -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.VisualApproval -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.Scheduling -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.PartnerProfile -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.PendingEngagement -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.ActivationComplete -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.LoadingSession,
            RealsRootUiState.Checking -> refreshSession()
            is RealsRootUiState.AccountDeletionPending,
            is RealsRootUiState.AccountDeletionScheduled,
            is RealsRootUiState.Failure,
            is RealsRootUiState.Login,
            is RealsRootUiState.MissingFirebase -> Unit
        }
    }

    fun enqueueMatchmaking(location: SearchLocationInput) {
        homeCoordinator.enqueueMatchmaking(location)
    }

    fun enqueueMatchmakingFromResolvedDeviceLocation(location: SearchLocationInput) {
        homeCoordinator.enqueueMatchmakingFromResolvedDeviceLocation(location)
    }

    fun cancelMatchmakingSearch() {
        homeCoordinator.cancelMatchmakingSearch()
    }

    fun leaveMatchmakingQueue() {
        homeCoordinator.leaveMatchmakingQueue()
    }

    fun dismissSecondChatFromHome(connectionId: String) {
        homeCoordinator.dismissSecondChatFromHome(connectionId)
    }

    fun beginMatchmakingLocationResolution() {
        homeCoordinator.beginMatchmakingLocationResolution()
    }

    fun failMatchmakingSearchPreparation() {
        homeCoordinator.failMatchmakingSearchPreparation()
    }

    fun openProfileManagement() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        _uiState.value = current.copy(editingActiveProfile = true)
        loadProfilePhotos()
    }

    fun closeProfileManagement() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (profile.status != ProfileStatus.Active) return

        viewModelScope.launch {
            homeCoordinator.loadHomeForReady(
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
                is FirstChatLoadResult.RouteHome -> homeCoordinator.returnHome(
                    session = session,
                    message = result.message,
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

    fun openSecondChat(
        connectionId: String,
        matchId: String,
        partnerName: String? = null,
    ) {
        val session = when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> current.session
            is RealsRootUiState.SecondChat -> current.session
            else -> return
        }
        val cleanConnectionId = connectionId.trim()
        val cleanMatchId = matchId.trim()
        if (cleanConnectionId.isBlank() || cleanMatchId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = RealsRootUiState.SecondChat(
                session = session,
                connectionId = cleanConnectionId,
                matchId = cleanMatchId,
                partnerName = partnerName,
                loading = true,
            )
            _uiState.value = secondChatCoordinator.load(
                session = session,
                connectionId = cleanConnectionId,
                matchId = cleanMatchId,
                partnerName = partnerName,
            )
        }
    }

    fun refreshSecondChat(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (current.refreshing || current.sending || current.actionLoading) return
        if (silent && silentSecondChatRefreshJob?.isActive == true) return
        if (current.chat == null) return openSecondChat(
            connectionId = current.connectionId,
            matchId = current.matchId,
            partnerName = current.partnerName,
        )

        val job = viewModelScope.launch {
            if (!silent) {
                _uiState.value = current.copy(
                    refreshing = true,
                    error = null,
                    message = null,
                )
            }
            val result = secondChatCoordinator.refresh(current, silent)
            val latest = _uiState.value as? RealsRootUiState.SecondChat ?: return@launch
            if (silent && (
                    latest.connectionId != current.connectionId ||
                        latest.sending ||
                        latest.actionLoading
                    )
            ) {
                return@launch
            }
            _uiState.value = result
        }
        if (silent) {
            silentSecondChatRefreshJob = job
        }
    }

    fun sendSecondChatMessage(content: String): Boolean {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return false
        if (current.loading || current.refreshing || current.sending || current.actionLoading) return false
        val chat = current.chat ?: return false
        val cleanContent = TextSafety.normalizeMultiline(content, maxLength = 1_000)
        if (cleanContent.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanContent)) {
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El mensaje no es valido."),
                message = null,
            )
            return false
        }

        val optimisticMessage = newOptimisticOutgoingMessage(
            chatId = chat.id,
            senderId = current.session.user.id,
            content = cleanContent,
        )
        val localId = optimisticMessage.localId
        val pending = current.copy(
            optimisticMessages = current.optimisticMessages + optimisticMessage,
            sending = true,
            error = null,
            message = null,
        )
        _uiState.value = pending
        viewModelScope.launch {
            _uiState.value = secondChatCoordinator.sendMessage(pending, cleanContent, localId)
        }
        return true
    }

    fun retrySecondChatMessage(localId: String, content: String) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        _uiState.value = current.copy(
            optimisticMessages = current.optimisticMessages.withoutOptimisticMessage(localId),
        )
        sendSecondChatMessage(content)
    }

    fun safetyCancelSecondChat(details: String) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        viewModelScope.launch {
            applySecondChatActionResult(
                secondChatCoordinator.safetyCancel(
                    current = current,
                    details = details,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun closeSecondChat() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
    }

    fun openVisualApproval(matchId: String) {
        val session = when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> current.session
            is RealsRootUiState.FirstChat -> current.session
            is RealsRootUiState.VisualApproval -> current.session
            else -> return
        }

        viewModelScope.launch {
            applyVisualApprovalFlowResult(
                visualApprovalCoordinator.open(
                    session = session,
                    matchId = matchId,
                    locallyHidden = matchId.trim() in homeCoordinator.localHiddenSnapshot().hiddenVisualMatchIds,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun closeVisualApproval() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
    }

    fun refreshVisualApproval() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            applyVisualApprovalFlowResult(
                visualApprovalCoordinator.refresh(
                    current = current,
                    locallyHidden = current.matchId in homeCoordinator.localHiddenSnapshot().hiddenVisualMatchIds,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun openScheduling(
        connectionId: String,
        matchId: String,
        partnerName: String? = null,
    ) {
        val session = when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> current.session
            is RealsRootUiState.Scheduling -> current.session
            else -> return
        }
        val cleanConnectionId = connectionId.trim()
        val cleanMatchId = matchId.trim()
        if (cleanConnectionId.isBlank() || cleanMatchId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = schedulingCoordinator.load(
                session = session,
                connectionId = cleanConnectionId,
                matchId = cleanMatchId,
                partnerName = partnerName,
            )
        }
    }

    fun refreshScheduling(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        if (current.refreshing || current.submitting) return
        if (silent && silentSchedulingRefreshJob?.isActive == true) return

        val job = viewModelScope.launch {
            _uiState.value = schedulingCoordinator.refresh(current, silent)
        }
        if (silent) {
            silentSchedulingRefreshJob = job
        }
    }

    fun submitSchedulingProposals(proposedDateTimes: List<String>) {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        if (current.submitting) return

        viewModelScope.launch {
            _uiState.value = schedulingCoordinator.submitProposals(
                current.copy(submittingLabel = "Enviando horarios..."),
                proposedDateTimes,
            )
        }
    }

    fun acceptSchedulingProposal(proposalId: String) {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        val cleanProposalId = proposalId.trim()
        if (current.submitting || cleanProposalId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = schedulingCoordinator.acceptProposal(
                current.copy(submittingLabel = "Aceptando horario..."),
                cleanProposalId,
            )
        }
    }

    fun rejectSchedulingRound() {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        if (current.submitting) return

        viewModelScope.launch {
            _uiState.value = schedulingCoordinator.rejectRound(
                current.copy(submittingLabel = "Rechazando ronda..."),
            )
        }
    }

    fun closeScheduling() {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
    }

    fun openConnectionPartnerProfile(matchId: String) {
        val session = when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> current.session
            is RealsRootUiState.Scheduling -> current.session
            else -> return
        }
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return

        viewModelScope.launch {
            loadPartnerProfile(
                session = session,
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
            homeCoordinator.returnHome(current.session)
        }
    }

    fun refreshFirstChat(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (current.refreshing || current.sending || current.actionLoading) return
        if (silent && silentFirstChatRefreshJob?.isActive == true) return
        if (current.chat == null) return openFirstChat(current.matchId, current.chatId)

        val job = viewModelScope.launch {
            if (!silent) {
                _uiState.value = current.copy(
                    refreshing = true,
                    error = null,
                    message = null,
                )
            }
            val result = firstChatCoordinator.refresh(current, silent)
            val latest = _uiState.value as? RealsRootUiState.FirstChat ?: return@launch
            if (silent && (
                    latest.matchId != current.matchId ||
                        latest.sending ||
                        latest.actionLoading
                    )
            ) {
                return@launch
            }
            when (result) {
                is FirstChatRefreshResult.Show -> _uiState.value = result.state
                is FirstChatRefreshResult.Reopen -> openFirstChat(result.matchId, result.chatId)
                is FirstChatRefreshResult.Closed -> {
                    homeCoordinator.hideFirstChatLocally(current.matchId)
                    homeCoordinator.returnHome(
                        session = current.session,
                        message = firstChatExitMessage(result.matchState),
                    )
                }

                FirstChatRefreshResult.ExitResolved -> {
                    homeCoordinator.hideFirstChatLocally(current.matchId)
                    homeCoordinator.returnHome(
                        session = current.session,
                        message = "El chat fue cerrado.",
                    )
                }
            }
        }
        if (silent) {
            silentFirstChatRefreshJob = job
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

    fun sendFirstChatMessage(content: String): Boolean {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return false
        if (current.loading || current.refreshing || current.sending || current.actionLoading) return false
        val chat = current.chat ?: return false
        val cleanContent = TextSafety.normalizeMultiline(content, maxLength = 1_000)
        if (cleanContent.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanContent)) {
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El mensaje no es valido."),
                message = null,
            )
            return false
        }

        val optimisticMessage = newOptimisticOutgoingMessage(
            chatId = chat.id,
            senderId = current.session.user.id,
            content = cleanContent,
        )
        val localId = optimisticMessage.localId
        val pending = current.copy(
            optimisticMessages = current.optimisticMessages + optimisticMessage,
            sending = true,
            error = null,
            message = null,
        )
        _uiState.value = pending
        viewModelScope.launch {
            _uiState.value = firstChatCoordinator.sendMessage(pending, cleanContent, localId)
        }
        return true
    }

    fun retryFirstChatMessage(localId: String, content: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        _uiState.value = current.copy(
            optimisticMessages = current.optimisticMessages.withoutOptimisticMessage(localId),
        )
        sendFirstChatMessage(content)
    }

    fun submitFirstChatDecision(decision: ChatContinueDecision) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.submitDecision(
                    current = current,
                    decision = decision,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun requestMutualChatExit() {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.requestMutualExit(
                    current = current,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun cancelChatUnilaterally() {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.cancelUnilaterally(
                    current = current,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun safetyCancelChat(details: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.safetyCancel(
                    current = current,
                    details = details,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun acceptChatExitRequest(exitRequestId: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.acceptExitRequest(
                    current = current,
                    exitRequestId = exitRequestId,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun rejectChatExitRequest(exitRequestId: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.rejectExitRequest(
                    current = current,
                    exitRequestId = exitRequestId,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun timeoutChatExitRequest(exitRequestId: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.timeoutExitRequest(
                    current = current,
                    exitRequestId = exitRequestId,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun saveMyVisualPersonalMessage(message: String) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            applyVisualApprovalFlowResult(
                visualApprovalCoordinator.savePersonalMessageAction(
                    current = current,
                    message = message,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun readPartnerPersonalMessage() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            applyVisualApprovalFlowResult(
                visualApprovalCoordinator.readPartnerPersonalMessageAction(
                    current = current,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun submitVisualDecision(decision: VisualDecision) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            applyVisualApprovalFlowResult(
                visualApprovalCoordinator.submitDecision(
                    current = current,
                    decision = decision,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun createProfile(input: CreateProfileInput) {
        profileHandler.createProfile(input)
    }

    fun updateProfile(input: UpdateProfileInput) {
        profileHandler.updateProfile(input)
    }

    fun updateMatchFilters(input: UpdateMatchFiltersInput) {
        profileHandler.updateMatchFilters(input)
    }

    fun loadProfilePhotos() {
        profileHandler.loadProfilePhotos()
    }

    fun addProfilePhotoFile(position: Int, fileUri: Uri) {
        profileHandler.addProfilePhotoFile(position, fileUri)
    }

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri) {
        profileHandler.replaceProfilePhotoFile(photoId, position, fileUri)
    }

    fun deleteProfilePhoto(photoId: String, position: Int) {
        profileHandler.deleteProfilePhoto(photoId, position)
    }

    fun activateProfile() {
        profileHandler.activateProfile()
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

    private suspend fun applyVisualApprovalFlowResult(result: VisualApprovalFlowResult) {
        when (result) {
            VisualApprovalFlowResult.Ignore -> Unit
            is VisualApprovalFlowResult.Show -> {
                result.hideVisualMatchId?.let(homeCoordinator::hideVisualReviewLocally)
                _uiState.value = result.state
            }

            is VisualApprovalFlowResult.ReturnHome -> {
                result.hideVisualMatchId?.let(homeCoordinator::hideVisualReviewLocally)
                homeCoordinator.returnHome(
                    session = result.session,
                    message = result.message,
                )
            }

            is VisualApprovalFlowResult.ReloadHome -> {
                result.hideVisualMatchId?.let(homeCoordinator::hideVisualReviewLocally)
                homeCoordinator.loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = result.session,
                        home = HomeUiState(
                            homeLoading = true,
                            homeMessage = result.message,
                        ),
                    ),
                    autoNavigateEngagements = result.autoNavigateEngagements,
                )
            }
        }
    }

    private suspend fun applyFirstChatActionResult(result: FirstChatActionResult) {
        when (result) {
            FirstChatActionResult.Ignore -> Unit
            is FirstChatActionResult.Show -> _uiState.value = result.state
            is FirstChatActionResult.ReturnHome -> {
                result.hideFirstChatMatchId?.let(homeCoordinator::hideFirstChatLocally)
                homeCoordinator.returnHome(
                    session = result.session,
                    message = result.message,
                )
            }

            is FirstChatActionResult.ReloadHome -> {
                result.hideFirstChatMatchId?.let(homeCoordinator::hideFirstChatLocally)
                homeCoordinator.loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = result.session,
                        home = HomeUiState(
                            homeLoading = true,
                            homeMessage = result.message,
                            matchmakingBlockedReason = null,
                        ),
                    ),
                    publishLoadingState = true,
                    autoNavigateEngagements = result.autoNavigateEngagements,
                )
            }
        }
    }

    private suspend fun applySecondChatActionResult(result: SecondChatActionResult) {
        when (result) {
            SecondChatActionResult.Ignore -> Unit
            is SecondChatActionResult.Show -> _uiState.value = result.state
            is SecondChatActionResult.ReturnHome -> homeCoordinator.returnHome(
                session = result.session,
                message = result.message,
            )
        }
    }

    private suspend fun showReadySession(session: ProvisionedSession) {
        val snapshot = session.profileSnapshot
        if (snapshot is ProfileSnapshot.Found) {
            if (snapshot.profile.status == ProfileStatus.Active) {
                homeCoordinator.loadHomeForReady(
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
                        sessionCoordinator.showAccountDeletionPendingFromBackend()
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

    private fun returnHomeFromExternalNotification(session: ProvisionedSession) {
        viewModelScope.launch {
            homeCoordinator.returnHome(session)
        }
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

