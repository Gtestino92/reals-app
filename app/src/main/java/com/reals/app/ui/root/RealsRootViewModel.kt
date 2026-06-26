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
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.CreateProfileInput
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
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RealsRootViewModel(
    dependencies: RealsRootDependencies,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Checking)
    private val getProfilePhotosUseCase = dependencies.profile.getProfilePhotos
    private val profileHandler = ProfileOperationHandler(
        uiState = _uiState,
        dependencies = dependencies.profile,
        getProfilePhotosUseCase = getProfilePhotosUseCase,
        provisionAndLoadProfile = dependencies.session.provisionAndLoadProfile,
        scope = viewModelScope,
    )
    private val getFirstChatForMatchUseCase = dependencies.firstChat.getFirstChatForMatch
    private val submitChatDecisionUseCase = dependencies.firstChat.submitChatDecision
    private val getChatExitRequestsUseCase = dependencies.firstChat.getChatExitRequests
    private val requestMutualChatExitUseCase = dependencies.firstChat.requestMutualChatExit
    private val acceptChatExitRequestUseCase = dependencies.firstChat.acceptChatExitRequest
    private val rejectChatExitRequestUseCase = dependencies.firstChat.rejectChatExitRequest
    private val timeoutChatExitRequestUseCase = dependencies.firstChat.timeoutChatExitRequest
    private val cancelChatUseCase = dependencies.firstChat.cancelChat
    private val safetyCancelChatUseCase = dependencies.firstChat.safetyCancelChat
    private val getVisualProfileUseCase = dependencies.visualApproval.getVisualProfile
    private val firstChatCoordinator = FirstChatCoordinator(dependencies.firstChat)
    private val secondChatCoordinator = SecondChatCoordinator(dependencies.firstChat)
    private val visualApprovalCoordinator = VisualApprovalCoordinator(dependencies.visualApproval)
    private val schedulingCoordinator = SchedulingCoordinator(dependencies.scheduling)
    private lateinit var sessionCoordinator: SessionCoordinator
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
        refreshSession()
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
        if (current.chat == null) return openSecondChat(
            connectionId = current.connectionId,
            matchId = current.matchId,
            partnerName = current.partnerName,
        )

        viewModelScope.launch {
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

        val localId = optimisticMessageLocalId()
        val optimisticMessage = OptimisticOutgoingMessage(
            localId = localId,
            chatId = chat.id,
            senderId = current.session.user.id,
            content = cleanContent,
            createdAtMillis = System.currentTimeMillis(),
            deliveryState = OutgoingMessageDeliveryState.Sending,
        )
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
            optimisticMessages = current.optimisticMessages.filterNot { it.localId == localId },
        )
        sendSecondChatMessage(content)
    }

    fun safetyCancelSecondChat(details: String) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (current.loading || current.refreshing || current.sending || current.actionLoading) return
        val chat = current.chat ?: return
        val cleanDetails = TextSafety.normalizeMultiline(details, maxLength = 1_000)
        if (cleanDetails.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanDetails)) {
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El detalle del reporte no es valido."),
                message = null,
            )
            return
        }

        viewModelScope.launch {
            val pending = current.copy(
                actionLoading = true,
                actionLoadingLabel = "Enviando reporte...",
                error = null,
                message = null,
            )
            _uiState.value = pending

            when (val result = safetyCancelChatUseCase(
                chat.id,
                ChatExitReason.InappropriateBehavior,
                cleanDetails,
            )) {
                is ApiResult.Success -> homeCoordinator.returnHome(
                    session = current.session,
                    message = "Reporte enviado. Cerramos esta conversacion por seguridad.",
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    actionLoading = false,
                    actionLoadingLabel = null,
                    error = result.error,
                )
            }
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
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return
        if (cleanMatchId in homeCoordinator.localHiddenSnapshot().hiddenVisualMatchIds) {
            viewModelScope.launch {
                homeCoordinator.returnHome(session)
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
            homeCoordinator.returnHome(current.session)
        }
    }

    fun refreshVisualApproval() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        if (
            current.loading ||
            current.refreshing ||
            current.readingPartnerMessage ||
            current.writingMessage ||
            current.deciding
        ) {
            return
        }
        viewModelScope.launch {
            loadVisualApprovalState(
                session = current.session,
                matchId = current.matchId,
                initialMatch = current.match,
                previous = current.copy(refreshing = true, error = null, message = null),
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

        viewModelScope.launch {
            _uiState.value = schedulingCoordinator.refresh(current, silent)
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
        if (current.chat == null) return openFirstChat(current.matchId, current.chatId)

        viewModelScope.launch {
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

        val localId = optimisticMessageLocalId()
        val optimisticMessage = OptimisticOutgoingMessage(
            localId = localId,
            chatId = chat.id,
            senderId = current.session.user.id,
            content = cleanContent,
            createdAtMillis = System.currentTimeMillis(),
            deliveryState = OutgoingMessageDeliveryState.Sending,
        )
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
            optimisticMessages = current.optimisticMessages.filterNot { it.localId == localId },
        )
        sendFirstChatMessage(content)
    }

    fun submitFirstChatDecision(decision: ChatContinueDecision) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (current.loading || current.refreshing || current.sending || current.actionLoading) return
        val chat = current.chat
        if (chat != null && chat.myDecision != ChatDecisionState.Pending) {
            _uiState.value = current.copy(
                message = "Ya registramos tu decision para este chat.",
                error = null,
            )
            return
        }
        viewModelScope.launch {
            val pending = current.copy(
                actionLoading = true,
                actionLoadingLabel = if (decision == ChatContinueDecision.Approved) {
                    "Aprobando..."
                } else {
                    "Rechazando..."
                },
                error = null,
                message = null,
            )
            _uiState.value = pending
            when (val result = submitChatDecisionUseCase(current.matchId, decision)) {
                is ApiResult.Success -> {
                    when (val state = result.value.state) {
                        MatchState.ChatActive -> {
                            if (decision == ChatContinueDecision.Approved) {
                                homeCoordinator.hideFirstChatLocally(current.matchId)
                                homeCoordinator.loadHomeForReady(
                                    ready = RealsRootUiState.Ready(
                                        session = current.session,
                                        home = HomeUiState(
                                            homeLoading = true,
                                            homeMessage = "Aprobaste el chat. Te avisaremos si la otra persona también aprueba.",
                                            matchmakingBlockedReason = null,
                                        ),
                                    ),
                                    publishLoadingState = true,
                                    autoNavigateEngagements = false,
                                )
                            } else {
                                _uiState.value = pending.copy(
                                    match = result.value,
                                    actionLoading = false,
                                    actionLoadingLabel = null,
                                    message = firstChatDecisionMessage(state),
                                )
                            }
                        }

                        MatchState.VisualPhase,
                        MatchState.ChatRejected,
                        MatchState.Expired,
                        MatchState.VisualApproved,
                        MatchState.VisualRejected -> {
                            homeCoordinator.hideFirstChatLocally(current.matchId)
                            if (decision == ChatContinueDecision.Approved) {
                                homeCoordinator.loadHomeForReady(
                                    ready = RealsRootUiState.Ready(
                                        session = current.session,
                                        home = HomeUiState(
                                            homeLoading = true,
                                            homeMessage = firstChatExitMessage(state),
                                            matchmakingBlockedReason = null,
                                        ),
                                    ),
                                    publishLoadingState = true,
                                    autoNavigateEngagements = false,
                                )
                            } else {
                                homeCoordinator.returnHome(
                                    session = current.session,
                                    message = firstChatExitMessage(state),
                                )
                            }
                        }

                        is MatchState.Unknown -> _uiState.value = pending.copy(
                            match = result.value,
                            actionLoading = false,
                            actionLoadingLabel = null,
                            message = firstChatDecisionMessage(state),
                        )
                    }
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    actionLoading = false,
                    actionLoadingLabel = null,
                    error = result.error,
                )
            }
        }
    }

    fun requestMutualChatExit() {
        runChatExitAction(
            successMessage = "Enviamos tu solicitud de salida consensuada.",
            loadingLabel = "Solicitando salida...",
        ) { chatId ->
            requestMutualChatExitUseCase(chatId, ChatExitReason.NoLongerInterested, null)
        }
    }

    fun cancelChatUnilaterally() {
        runChatExitAction(
            successMessage = "Cerraste el chat.",
            loadingLabel = "Cerrando chat...",
        ) { chatId ->
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
        runChatExitAction(
            successMessage = "Reporte enviado. Cerramos esta conversacion por seguridad.",
            loadingLabel = "Enviando reporte...",
        ) { chatId ->
            safetyCancelChatUseCase(chatId, ChatExitReason.InappropriateBehavior, cleanDetails)
        }
    }

    fun acceptChatExitRequest(exitRequestId: String) {
        runChatExitAction(
            successMessage = "Aceptaste la salida consensuada.",
            loadingLabel = "Aceptando salida...",
        ) { chatId ->
            acceptChatExitRequestUseCase(chatId, exitRequestId)
        }
    }

    fun rejectChatExitRequest(exitRequestId: String) {
        runChatExitAction(
            successMessage = "Rechazaste la salida consensuada.",
            loadingLabel = "Rechazando salida...",
        ) { chatId ->
            rejectChatExitRequestUseCase(chatId, exitRequestId)
        }
    }

    fun timeoutChatExitRequest(exitRequestId: String) {
        runChatExitAction(
            successMessage = "La solicitud de salida vencio.",
            loadingLabel = "Cerrando por timeout...",
        ) { chatId ->
            timeoutChatExitRequestUseCase(chatId, exitRequestId)
        }
    }

    fun saveMyVisualPersonalMessage(message: String) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        if (current.readingPartnerMessage || current.writingMessage || current.deciding) return
        if (current.myPersonalMessageSubmitted) {
            _uiState.value = current.copy(
                writingMessage = false,
                error = null,
                message = "Ya habias guardado tu mensaje personal.",
            )
            return
        }
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

    fun readPartnerPersonalMessage() {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        if (
            current.loading ||
            current.refreshing ||
            current.readingPartnerMessage ||
            current.writingMessage ||
            current.deciding ||
            current.partnerMessageLoaded
        ) {
            return
        }

        val profile = current.profile ?: return
        if (!profile.partnerPersonalMessageSubmitted) return

        viewModelScope.launch {
            _uiState.value = current.copy(
                readingPartnerMessage = true,
                partnerMessageError = null,
                error = null,
                message = null,
            )
            _uiState.value = visualApprovalCoordinator.readPartnerPersonalMessage(current)
        }
    }

    fun submitVisualDecision(decision: VisualDecision) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        if (
            current.deciding ||
            current.writingMessage ||
            current.readingPartnerMessage ||
            current.profile?.decisionRequiresPartnerPersonalMessageRead == true
        ) {
            return
        }
        viewModelScope.launch {
            val pending = current.copy(
                deciding = true,
                decidingLabel = if (decision == VisualDecision.Approved) {
                    "Aprobando..."
                } else {
                    "Rechazando..."
                },
                error = null,
                message = null,
            )
            _uiState.value = pending
            when (val result =
                visualApprovalCoordinator.submitDecision(current.matchId, decision)) {
                is ApiResult.Success -> {
                    homeCoordinator.hideVisualReviewLocally(current.matchId)
                    when (result.value.state) {
                        MatchState.VisualPhase -> homeCoordinator.loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                home = HomeUiState(homeLoading = true),
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.VisualApproved -> homeCoordinator.loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                home = HomeUiState(homeLoading = true),
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.VisualRejected,
                        MatchState.ChatRejected,
                        MatchState.Expired -> homeCoordinator.loadHomeForReady(
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
                            decidingLabel = null,
                            message = "Guardamos tu decision.",
                        )
                    }
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    deciding = false,
                    decidingLabel = null,
                    error = result.error,
                )
            }
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

    fun addMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        profileHandler.addMockProfilePhoto(profile, position, isPersonPhoto, isFullBody)
    }

    fun addProfilePhotoFile(position: Int, fileUri: Uri) {
        profileHandler.addProfilePhotoFile(position, fileUri)
    }

    fun replaceMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        profileHandler.replaceMockProfilePhoto(profile, position, isPersonPhoto, isFullBody)
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
                locallyHidden = matchId in homeCoordinator.localHiddenSnapshot().hiddenVisualMatchIds,
            )
        ) {
            is VisualApprovalLoadResult.Show -> _uiState.value = result.state
            is VisualApprovalLoadResult.RouteHome -> homeCoordinator.loadHomeForReady(
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

    private fun runChatExitAction(
        successMessage: String = "Actualizamos el estado del chat.",
        loadingLabel: String = "Procesando...",
        action: suspend (chatId: String) -> ApiResult<*>,
    ) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (current.loading || current.refreshing || current.sending || current.actionLoading) return
        val chat = current.chat ?: return

        viewModelScope.launch {
            val pending = current.copy(
                actionLoading = true,
                actionLoadingLabel = loadingLabel,
                error = null,
                message = null,
            )
            _uiState.value = pending
            when (val result = action(chat.id)) {
                is ApiResult.Success -> {
                    val outcome = result.value as? ChatExitOutcome
                    if (outcome != null && outcome.chat.status != ChatStatus.Active) {
                        homeCoordinator.hideFirstChatLocally(current.matchId)
                        homeCoordinator.returnHome(
                            session = current.session,
                            message = successMessage,
                        )
                        return@launch
                    }
                    if (outcome?.exitRequest?.status.isResolvedExitStatus()) {
                        homeCoordinator.hideFirstChatLocally(current.matchId)
                        homeCoordinator.returnHome(
                            session = current.session,
                            message = "El chat fue cerrado.",
                        )
                        return@launch
                    }

                    val chatResult = getFirstChatForMatchUseCase(current.matchId)
                    val exitsResult = getChatExitRequestsUseCase(chat.id)
                    _uiState.value = pending.copy(
                        chat = (chatResult as? ApiResult.Success)?.value ?: pending.chat,
                        exitRequests = (exitsResult as? ApiResult.Success)?.value
                            ?: pending.exitRequests,
                        actionLoading = false,
                        actionLoadingLabel = null,
                        message = successMessage,
                        error = (chatResult as? ApiResult.Failure)?.error
                            ?: (exitsResult as? ApiResult.Failure)?.error,
                    )
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    actionLoading = false,
                    actionLoadingLabel = null,
                    error = result.error,
                )
            }
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

    private fun optimisticMessageLocalId(): String = "local-${System.currentTimeMillis()}"
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

