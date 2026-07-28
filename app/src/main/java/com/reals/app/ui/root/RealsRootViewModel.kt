package com.reals.app.ui.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.isLegalActionRequired
import com.reals.app.core.network.isTerminalAuthFailure
import com.reals.app.core.network.isUserPairBlocked
import com.reals.app.di.AppContainer
import com.reals.app.di.RealsRootDependencies
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.FirstChatGuidance
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.SecondChatCompletionDecision
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.VisualDecision
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_CONFIRMED
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_PROPOSALS_RECEIVED
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_REMINDER
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RealsRootViewModel(
    dependencies: RealsRootDependencies,
    autoRefreshSession: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Checking)
    private val authRepository = dependencies.session.authRepository
    private val getProfilePhotosUseCase = dependencies.profile.getProfilePhotos
    private val profileHandler = ProfileOperationHandler(
        uiState = _uiState,
        dependencies = dependencies.profile,
        authRepository = authRepository,
        localFirebaseEmailVerificationCoordinator =
            dependencies.session.localFirebaseEmailVerificationCoordinator,
        getProfilePhotosUseCase = getProfilePhotosUseCase,
        scope = viewModelScope,
        onTerminalAuthFailure = { sessionCoordinator.invalidateTerminalSession() },
    )
    private val profileEntryCoordinator = ProfileEntryCoordinator(
        getProfilePhotos = getProfilePhotosUseCase,
        getHome = dependencies.home.getHome,
    )
    private val firstChatCoordinator = FirstChatCoordinator(dependencies.firstChat)
    private val secondChatCoordinator = SecondChatCoordinator(dependencies.secondChat)
    private val visualApprovalCoordinator = VisualApprovalCoordinator(dependencies.visualApproval)
    private val partnerProfileCoordinator = PartnerProfileCoordinator(
        dependencies.visualApproval.getVisualProfile,
        dependencies.visualApproval.getPartnerPersonalMessage,
    )
    private val schedulingCoordinator = SchedulingCoordinator(dependencies.scheduling)
    private val manualBlockCoordinator = ManualBlockCoordinator(
        dependencies.manualBlock.blockMatchParticipant,
    )
    private val legalCoordinator = LegalCoordinator(dependencies.legal)
    private lateinit var sessionCoordinator: SessionCoordinator
    private var silentFirstChatRefreshJob: Job? = null
    private var silentSecondChatRefreshJob: Job? = null
    private var schedulingOpenJob: Job? = null
    private var schedulingRefreshJob: Job? = null
    private var silentSchedulingRefreshJob: Job? = null
    private var legalRerouteJob: Job? = null
    private var pairBlockedRerouteJob: Job? = null
    private var sessionInvalidationJob: Job? = null
    private var manualBlockJob: Job? = null
    private var pendingSecondChatLocalExpiryKey: SecondChatExpiryKey? = null
    private var completedSecondChatLocalExpiryKey: SecondChatExpiryKey? = null
    private val homeCoordinator = HomeCoordinator(
        uiState = _uiState,
        dependencies = dependencies.home,
        scope = viewModelScope,
        onOpenFirstChat = { session, matchId, chatId -> openFirstChat(session, matchId, chatId) },
        onOpenSecondChat = { session, connectionId, matchId, partnerName ->
            openSecondChat(session, connectionId, matchId, partnerName, joinIfAllowed = false)
        },
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
                showReactivatedSession(session)
            },
        )
        observeTerminalAuthFailure()
        observeLegalActionRequired()
        observeUserPairBlocked()
        if (autoRefreshSession) {
            refreshSession()
        }
    }

    fun refreshSession() = sessionCoordinator.refreshSession()

    fun signIn(email: String, password: String) = sessionCoordinator.signIn(email, password)

    fun signUp(email: String, password: String) = sessionCoordinator.signUp(email, password)

    fun requestPasswordReset(email: String) = sessionCoordinator.requestPasswordReset(email)

    fun currentUserHasPasswordProvider(): Boolean = authRepository.currentUserHasPasswordProvider()

    fun signOut() = sessionCoordinator.signOut()

    fun deleteAccount() = sessionCoordinator.deleteAccount()

    fun retryLegalRequirements() {
        val current = _uiState.value as? RealsRootUiState.LegalRequirements ?: return
        if (current.loading || current.submittingDocumentType != null || current.deletingAccount) return
        viewModelScope.launch {
            _uiState.value = current.copy(loading = true, error = null)
            applyLegalCoordinatorResult(
                legalCoordinator.load(
                    session = current.session,
                    resumeContext = current.resumeContext,
                )
            )
        }
    }

    fun recordLegalDocumentAction(documentKey: String) {
        val current = _uiState.value as? RealsRootUiState.LegalRequirements ?: return
        if (current.loading || current.submittingDocumentType != null || current.deletingAccount) return
        val requirement = current.documents.firstOrNull { it.key == documentKey } ?: return
        if (requirement.satisfied || requirement.requiredAction is LegalDocumentAction.Unknown) return

        viewModelScope.launch {
            val pending = current.copy(
                submittingDocumentType = requirement.type,
                error = null,
            )
            _uiState.value = pending
            applyLegalCoordinatorResult(
                legalCoordinator.recordRequiredAction(pending, requirement)
            )
        }
    }

    fun deferLegalRequirements() {
        val current = _uiState.value as? RealsRootUiState.LegalRequirements ?: return
        if (current.loading || current.submittingDocumentType != null || current.deletingAccount) return
        viewModelScope.launch {
            when (val resume = current.resumeContext) {
                LegalResumeContext.PostSession,
                LegalResumeContext.PostReactivation -> continueReadySession(current.session)

                is LegalResumeContext.ExistingState ->
                    _uiState.value = resume.state.clearLegalActionRequiredForResume()
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) =
        sessionCoordinator.changePassword(currentPassword, newPassword)

    fun reactivateAccount() = sessionCoordinator.reactivateAccount()

    fun onSystemBack() {
        val current = _uiState.value
        if (!current.canHandleSystemBack()) return

        when (current) {
            is RealsRootUiState.Ready -> {
                if (current.editingActiveProfile) {
                    closeProfileManagement()
                }
            }

            is RealsRootUiState.SecondChat -> closeSecondChat()
            is RealsRootUiState.VisualApproval -> closeVisualApproval()
            is RealsRootUiState.Scheduling -> closeScheduling()
            is RealsRootUiState.PartnerProfile -> closePartnerProfile()
            is RealsRootUiState.PendingEngagement -> returnToHomeFromPendingEngagement()
            is RealsRootUiState.ActivationComplete -> refreshSession()

            is RealsRootUiState.AccountDeletionPending,
            is RealsRootUiState.AccountDeletionScheduled,
            RealsRootUiState.Checking,
            is RealsRootUiState.Failure,
            is RealsRootUiState.FirstChat,
            is RealsRootUiState.LegalRequirements,
            is RealsRootUiState.LoadingSession,
            is RealsRootUiState.Login,
            is RealsRootUiState.MissingFirebase -> Unit
        }
    }

    fun refreshHomeState() {
        homeCoordinator.refreshHomeState()
    }

    fun pollHomeStateSilently() {
        homeCoordinator.pollHomeStateSilently()
    }

    fun handleExternalNotificationOpened(type: String?) {
        if (
            type != TYPE_VISUAL_REVIEW_AVAILABLE &&
            type != TYPE_VISUAL_REVIEW_REMINDER &&
            type != TYPE_SCHEDULING_AVAILABLE &&
            type != TYPE_SCHEDULING_PROPOSALS_RECEIVED &&
            type != TYPE_SCHEDULING_CONFIRMED &&
            type != TYPE_SECOND_CHAT_REMINDER
        ) return

        when (val current = _uiState.value) {
            is RealsRootUiState.Ready -> refreshHomeState()
            is RealsRootUiState.FirstChat -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.SecondChat -> {
                if (current.isJoinedActiveSecondChat()) {
                    refreshSecondChat(silent = true)
                } else {
                    returnHomeFromExternalNotification(current.session)
                }
            }
            is RealsRootUiState.VisualApproval -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.Scheduling -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.PartnerProfile -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.PendingEngagement -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.ActivationComplete -> returnHomeFromExternalNotification(current.session)
            is RealsRootUiState.LegalRequirements -> Unit
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
        closeProfileManagement(current)
    }

    private fun closeProfileManagement(current: RealsRootUiState.Ready) {
        if (current.session.profileSnapshot !is ProfileSnapshot.Found) return

        homeCoordinator.closeProfileManagementWithHomeReload(current)
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
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
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

        openSecondChat(session, cleanConnectionId, cleanMatchId, partnerName, joinIfAllowed = true)
    }

    private fun openSecondChat(
        session: ProvisionedSession,
        cleanConnectionId: String,
        cleanMatchId: String,
        partnerName: String? = null,
        joinIfAllowed: Boolean,
    ) {
        pendingSecondChatLocalExpiryKey = null
        completedSecondChatLocalExpiryKey = null
        viewModelScope.launch {
            _uiState.value = RealsRootUiState.SecondChat(
                session = session,
                connectionId = cleanConnectionId,
                matchId = cleanMatchId,
                partnerName = partnerName,
                loading = true,
            )
            applySecondChatLoadResult(
                secondChatCoordinator.load(
                    session = session,
                    connectionId = cleanConnectionId,
                    matchId = cleanMatchId,
                    partnerName = partnerName,
                    joinIfAllowed = joinIfAllowed,
                )
            )
        }
    }

    fun refreshSecondChat(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (current.refreshing || current.sending || current.actionLoading || current.manualBlock.loading) return
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
            applySecondChatLoadResult(result)
        }
        if (silent) {
            silentSecondChatRefreshJob = job
        }
    }

    fun sendSecondChatMessage(content: String): Boolean {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return false
        return when (val preparation = ChatMessageActionHandler.prepareSecondChatSend(current, content)) {
            is ChatMessageSendPreparation.Accepted -> {
                val instanceKey = preparation.pendingState.expiryKey()
                _uiState.value = preparation.pendingState
                viewModelScope.launch {
                    val result = secondChatCoordinator.sendMessage(
                        preparation.pendingState,
                        preparation.cleanContent,
                        preparation.localId,
                    )
                    val latest = _uiState.value as? RealsRootUiState.SecondChat ?: return@launch
                    if (latest.matches(instanceKey)) {
                        _uiState.value = result
                    }
                }
                true
            }

            is ChatMessageSendPreparation.Rejected -> {
                _uiState.value = preparation.state
                false
            }

            ChatMessageSendPreparation.Ignored -> false
        }
    }

    fun retrySecondChatMessage(localId: String, content: String) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        _uiState.value = ChatMessageActionHandler.retrySecondChat(current, localId)
        sendSecondChatMessage(content)
    }

    fun safetyCancelSecondChat(reason: ChatExitReason, details: String) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        viewModelScope.launch {
            applySecondChatActionResult(
                secondChatCoordinator.safetyCancel(
                    current = current,
                    reason = reason,
                    details = details,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun closeSecondChat() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (current.isJoinedActiveSecondChat()) return
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
    }

    fun handleSecondChatLocalAbsoluteExpiry() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (!current.lifecycle.timingPresentation().locallyExpired) return
        val expiryKey = current.expiryKey()
        if (
            completedSecondChatLocalExpiryKey == expiryKey ||
            pendingSecondChatLocalExpiryKey == expiryKey
        ) {
            return
        }
        pendingSecondChatLocalExpiryKey = expiryKey
        viewModelScope.launch {
            val latest = _uiState.value as? RealsRootUiState.SecondChat
            if (latest == null || !latest.matches(expiryKey)) {
                clearPendingSecondChatLocalExpiry(expiryKey)
                return@launch
            }
            val timing = latest.lifecycle.timingPresentation()
            if (!timing.locallyExpired && !latest.hasTerminalSecondChatStatus()) {
                clearPendingSecondChatLocalExpiry(expiryKey)
                return@launch
            }
            completedSecondChatLocalExpiryKey = expiryKey
            clearPendingSecondChatLocalExpiry(expiryKey)
            homeCoordinator.returnHome(
                session = latest.session,
                message = "El segundo chat venció.",
            )
        }
    }

    private fun clearPendingSecondChatLocalExpiry(expiryKey: SecondChatExpiryKey) {
        if (pendingSecondChatLocalExpiryKey == expiryKey) {
            pendingSecondChatLocalExpiryKey = null
        }
    }

    fun createSecondChatNoShowClaim() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        viewModelScope.launch {
            applySecondChatLoadResult(
                secondChatCoordinator.createNoShowClaim(
                    current = current,
                    onPending = { _uiState.value = it },
                )
            )
        }
    }

    fun requestSecondChatCompletion() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        val instanceKey = current.expiryKey()
        viewModelScope.launch {
            val result = secondChatCoordinator.createCompletionRequest(
                current = current,
                onPending = { setSecondChatPendingIfCurrent(it, instanceKey) },
            )
            applySecondChatLoadResultIfCurrent(result, instanceKey)
        }
    }

    fun decideSecondChatCompletion(
        requestId: String,
        decision: SecondChatCompletionDecision,
    ) {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        val instanceKey = current.expiryKey()
        viewModelScope.launch {
            val result = secondChatCoordinator.decideCompletionRequest(
                current = current,
                requestId = requestId,
                decision = decision,
                onPending = { setSecondChatPendingIfCurrent(it, instanceKey) },
            )
            applySecondChatLoadResultIfCurrent(result, instanceKey)
        }
    }

    fun claimSecondChatInactivity() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        val instanceKey = current.expiryKey()
        viewModelScope.launch {
            val result = secondChatCoordinator.createInactivityClaim(
                current = current,
                onPending = { setSecondChatPendingIfCurrent(it, instanceKey) },
            )
            applySecondChatLoadResultIfCurrent(result, instanceKey)
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
        if (current.manualBlock.loading) return
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
        if (schedulingOpenJob?.isActive == true) return

        val pending = RealsRootUiState.Scheduling(
            session = session,
            connectionId = cleanConnectionId,
            matchId = cleanMatchId,
            partnerName = partnerName,
            loading = true,
        )
        _uiState.value = pending

        val job = viewModelScope.launch {
            val result = schedulingCoordinator.refresh(pending, silent = false)
            val latest = _uiState.value as? RealsRootUiState.Scheduling ?: return@launch
            if (latest.connectionId != cleanConnectionId || latest.matchId != cleanMatchId) {
                return@launch
            }
            _uiState.value = result
        }
        schedulingOpenJob = job
        job.invokeOnCompletion {
            if (schedulingOpenJob == job) {
                schedulingOpenJob = null
            }
        }
    }

    fun refreshScheduling(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        if (current.refreshing || current.submitting || current.manualBlock.loading) return
        if (schedulingRefreshJob?.isActive == true) return
        if (silent && silentSchedulingRefreshJob?.isActive == true) return

        val connectionId = current.connectionId
        val matchId = current.matchId
        val job = viewModelScope.launch {
            val result = schedulingCoordinator.refresh(current, silent)
            val latest = _uiState.value as? RealsRootUiState.Scheduling ?: return@launch
            if (latest.connectionId != connectionId || latest.matchId != matchId) return@launch
            if (latest.submitting || latest.manualBlock.loading) return@launch
            _uiState.value = result
        }
        schedulingRefreshJob = job
        job.invokeOnCompletion {
            if (schedulingRefreshJob == job) {
                schedulingRefreshJob = null
            }
        }
        if (silent) {
            silentSchedulingRefreshJob = job
            job.invokeOnCompletion {
                if (silentSchedulingRefreshJob == job) {
                    silentSchedulingRefreshJob = null
                }
            }
        }
    }

    fun submitSchedulingProposals(proposedDateTimes: List<String>) {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        if (current.submitting) return

        silentSchedulingRefreshJob?.cancel()
        silentSchedulingRefreshJob = null
        val connectionId = current.connectionId
        viewModelScope.launch {
            val result = schedulingCoordinator.submitProposals(
                current.copy(submittingLabel = "Enviando horarios..."),
                proposedDateTimes,
                onPending = { pending ->
                    val latest = _uiState.value as? RealsRootUiState.Scheduling
                    if (latest?.connectionId == connectionId) {
                        _uiState.value = pending
                    }
                },
            )
            val latest = _uiState.value as? RealsRootUiState.Scheduling ?: return@launch
            if (latest.connectionId != connectionId) return@launch
            _uiState.value = result
        }
    }

    fun acceptSchedulingProposal(proposalId: String) {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        val cleanProposalId = proposalId.trim()
        if (current.submitting || cleanProposalId.isBlank()) return

        silentSchedulingRefreshJob?.cancel()
        silentSchedulingRefreshJob = null
        val connectionId = current.connectionId
        viewModelScope.launch {
            val result = schedulingCoordinator.acceptProposal(
                current.copy(submittingLabel = "Aceptando horario..."),
                cleanProposalId,
                onPending = { pending ->
                    val latest = _uiState.value as? RealsRootUiState.Scheduling
                    if (latest?.connectionId == connectionId) {
                        _uiState.value = pending
                    }
                },
            )
            val latest = _uiState.value as? RealsRootUiState.Scheduling ?: return@launch
            if (latest.connectionId != connectionId) return@launch
            _uiState.value = result
        }
    }

    fun rejectSchedulingPartnerProposals() {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        if (current.submitting) return

        silentSchedulingRefreshJob?.cancel()
        silentSchedulingRefreshJob = null
        val connectionId = current.connectionId
        viewModelScope.launch {
            val result = schedulingCoordinator.rejectPartnerProposals(
                current.copy(submittingLabel = "Rechazando opciones..."),
                onPending = { pending ->
                    val latest = _uiState.value as? RealsRootUiState.Scheduling
                    if (latest?.connectionId == connectionId) {
                        _uiState.value = pending
                    }
                },
            )
            val latest = _uiState.value as? RealsRootUiState.Scheduling ?: return@launch
            if (latest.connectionId != connectionId) return@launch
            _uiState.value = result
        }
    }

    fun closeScheduling() {
        val current = _uiState.value as? RealsRootUiState.Scheduling ?: return
        schedulingOpenJob?.cancel()
        schedulingOpenJob = null
        schedulingRefreshJob?.cancel()
        schedulingRefreshJob = null
        silentSchedulingRefreshJob = null
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
            _uiState.value = partnerProfileCoordinator.load(
                session = session,
                matchId = cleanMatchId,
                onPending = { _uiState.value = it },
            )
        }
    }

    fun refreshPartnerProfile() {
        val current = _uiState.value as? RealsRootUiState.PartnerProfile ?: return
        if (current.manualBlock.loading) return
        viewModelScope.launch {
            _uiState.value = partnerProfileCoordinator.refresh(
                current = current,
                onPending = { _uiState.value = it },
            )
        }
    }

    fun retryPartnerProfileMessage() {
        val current = _uiState.value as? RealsRootUiState.PartnerProfile ?: return
        if (current.manualBlock.loading) return
        viewModelScope.launch {
            _uiState.value = partnerProfileCoordinator.retryPartnerMessage(
                current = current,
                onPending = { _uiState.value = it },
            )
        }
    }

    fun closePartnerProfile() {
        val current = _uiState.value as? RealsRootUiState.PartnerProfile ?: return
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
    }

    fun blockCurrentMatchParticipant() {
        if (manualBlockJob?.isActive == true) return
        manualBlockJob = viewModelScope.launch {
            val current = _uiState.value
            when (
                val result = manualBlockCoordinator.block(
                    current = current,
                    onPending = {
                        cancelSilentRefreshFor(current)
                        _uiState.value = it
                    },
                )
            ) {
                ManualBlockResult.Ignore -> Unit
                is ManualBlockResult.Show -> _uiState.value = result.state
                is ManualBlockResult.ReturnHome -> homeCoordinator.returnHome(
                    session = result.session,
                    message = "Bloqueaste a ésta persona. Cerramos la interacción y no volverán a ser emparejados.",
                )
            }
        }
    }

    fun clearManualBlockError() {
        _uiState.value = _uiState.value.clearManualBlockError()
    }

    fun refreshFirstChat(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (
            current.refreshing || current.sending || current.actionLoading ||
            current.guidanceActionLoading || current.manualBlock.loading
        ) return
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
                        latest.actionLoading ||
                        latest.guidanceActionLoading
                    )
            ) {
                return@launch
            }
            when (result) {
                is FirstChatRefreshResult.Show -> _uiState.value = result.state.withFreshGuidanceFrom(latest)
                is FirstChatRefreshResult.Reopen -> openFirstChat(result.matchId, result.chatId)
                is FirstChatRefreshResult.Closed -> {
                    homeCoordinator.hideFirstChatLocally(current.matchId)
                    homeCoordinator.returnHome(
                        session = current.session,
                        message = result.chatStatus?.firstChatClosedMessage()
                            ?: firstChatExitMessage(result.matchState),
                    )
                }

                is FirstChatRefreshResult.ExitResolved -> {
                    homeCoordinator.hideFirstChatLocally(current.matchId)
                    homeCoordinator.returnHome(
                        session = current.session,
                        message = result.message,
                    )
                }
            }
        }
        if (silent) {
            silentFirstChatRefreshJob = job
        }
    }

    fun requestNextFirstChatGuidanceQuestion() {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatGuidanceActionResult(
                result = firstChatCoordinator.requestNextGuidanceQuestion(
                    current = current,
                    onPending = { _uiState.value = it },
                ),
                original = current,
            )
        }
    }

    fun returnToHomeFromPendingEngagement() {
        val current = _uiState.value as? RealsRootUiState.PendingEngagement ?: return
        viewModelScope.launch {
            homeCoordinator.returnHome(current.session)
        }
    }

    fun sendFirstChatMessage(content: String): Boolean {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return false
        return when (val preparation = ChatMessageActionHandler.prepareFirstChatSend(current, content)) {
            is ChatMessageSendPreparation.Accepted -> {
                _uiState.value = preparation.pendingState
                viewModelScope.launch {
                    applyFirstChatSendResult(
                        firstChatCoordinator.sendMessage(
                            preparation.pendingState,
                            preparation.cleanContent,
                            preparation.localId,
                        )
                    )
                }
                true
            }

            is ChatMessageSendPreparation.Rejected -> {
                _uiState.value = preparation.state
                false
            }

            ChatMessageSendPreparation.Ignored -> false
        }
    }

    fun retryFirstChatMessage(localId: String, content: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        _uiState.value = ChatMessageActionHandler.retryFirstChat(current, localId)
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

    fun handleFirstChatLocalExpiry(inactivity: Boolean) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        homeCoordinator.hideFirstChatLocally(current.matchId)
        viewModelScope.launch {
            homeCoordinator.returnHome(
                session = current.session,
                message = if (inactivity) {
                    "La conversaci\u00f3n se cerr\u00f3 por inactividad."
                } else {
                    "El chat venci\u00f3."
                },
            )
        }
    }

    fun handleSecondChatUnavailable() {
        val current = _uiState.value as? RealsRootUiState.SecondChat ?: return
        viewModelScope.launch {
            homeCoordinator.returnHome(
                session = current.session,
                message = "Este segundo chat ya no est\u00e1 disponible.",
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

    fun safetyCancelChat(reason: ChatExitReason, details: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        viewModelScope.launch {
            applyFirstChatActionResult(
                firstChatCoordinator.safetyCancel(
                    current = current,
                    reason = reason,
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

    fun loadCountriesIfNeeded() {
        profileHandler.loadCountriesIfNeeded()
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

    fun moveProfilePhoto(photoId: String, targetPosition: Int) {
        profileHandler.moveProfilePhoto(photoId, targetPosition)
    }

    fun activateProfile() {
        profileHandler.activateProfile()
    }

    fun resendEmailVerification() {
        profileHandler.resendEmailVerification()
    }

    fun checkEmailVerification() {
        profileHandler.checkEmailVerification()
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
                    allowDraftHomeWithoutInteractions = true,
                )
            }
        }
    }

    private suspend fun applyFirstChatActionResult(result: FirstChatActionResult) {
        when (result) {
            FirstChatActionResult.Ignore -> Unit
            is FirstChatActionResult.Show -> {
                val latest = _uiState.value as? RealsRootUiState.FirstChat
                _uiState.value = latest?.let { result.state.withFreshGuidanceFrom(it) } ?: result.state
            }
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
                    allowDraftHomeWithoutInteractions = true,
                )
            }
        }
    }

    private suspend fun applyFirstChatGuidanceActionResult(
        result: FirstChatActionResult,
        original: RealsRootUiState.FirstChat,
    ) {
        if (result !is FirstChatActionResult.Show) {
            applyFirstChatActionResult(result)
            return
        }

        val latest = _uiState.value as? RealsRootUiState.FirstChat
        if (latest == null || latest.matchId != original.matchId || latest.chatId != original.chatId) return

        val returnedGuidance = result.state.chat?.guidance.takeIf { result.state.error == null }
        _uiState.value = latest.copy(
            chat = latest.chat?.let { chat ->
                if (returnedGuidance != null) chat.copy(guidance = returnedGuidance) else chat
            } ?: result.state.chat,
            guidanceActionLoading = false,
            error = result.state.error,
            message = result.state.message,
        )
    }

    private suspend fun applyFirstChatSendResult(result: FirstChatSendResult) {
        when (result) {
            is FirstChatSendResult.Show -> {
                val latest = _uiState.value as? RealsRootUiState.FirstChat
                _uiState.value = latest?.let { result.state.withFreshGuidanceFrom(it) } ?: result.state
            }
            is FirstChatSendResult.ReturnHome -> {
                homeCoordinator.hideFirstChatLocally(result.hideFirstChatMatchId)
                homeCoordinator.returnHome(
                    session = result.session,
                    message = result.message,
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

    private suspend fun applySecondChatLoadResult(result: SecondChatLoadResult) {
        when (result) {
            is SecondChatLoadResult.Show -> _uiState.value = result.state
            is SecondChatLoadResult.ReturnHome -> homeCoordinator.returnHome(
                session = result.session,
                message = result.message,
            )
        }
    }

    private suspend fun applySecondChatLoadResultIfCurrent(
        result: SecondChatLoadResult,
        instanceKey: SecondChatExpiryKey,
    ) {
        val latest = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (!latest.matches(instanceKey)) return
        applySecondChatLoadResult(result)
    }

    private fun setSecondChatPendingIfCurrent(
        pending: RealsRootUiState.SecondChat,
        instanceKey: SecondChatExpiryKey,
    ) {
        val latest = _uiState.value as? RealsRootUiState.SecondChat ?: return
        if (latest.matches(instanceKey)) {
            _uiState.value = pending
        }
    }

    private suspend fun showReadySession(session: ProvisionedSession) {
        enterLegalRequirements(
            session = session,
            resumeContext = LegalResumeContext.PostSession,
            publishLoadingState = false,
        )
    }

    private suspend fun showReactivatedSession(session: ProvisionedSession) {
        enterLegalRequirements(
            session = session,
            resumeContext = LegalResumeContext.PostReactivation,
            publishLoadingState = false,
        )
    }

    private suspend fun continueReadySession(session: ProvisionedSession) {
        when (
            val result = profileEntryCoordinator.enter(
                session = session,
                onPending = { _uiState.value = it.state },
            )
        ) {
            is ProfileEntryResult.LoadHome -> homeCoordinator.loadHomeForReady(
                ready = result.ready,
                publishLoadingState = result.publishLoadingState,
                autoNavigateEngagements = result.autoNavigateEngagements,
                preloadedHome = result.preloadedHome,
            )

            is ProfileEntryResult.ShowReady -> _uiState.value = result.state

            ProfileEntryResult.AccountDeletionPendingFromBackend ->
                sessionCoordinator.showAccountDeletionPendingFromBackend()
        }
    }

    private suspend fun enterLegalRequirements(
        session: ProvisionedSession,
        resumeContext: LegalResumeContext,
        publishLoadingState: Boolean = true,
    ) {
        if (publishLoadingState) {
            _uiState.value = RealsRootUiState.LegalRequirements(
                session = session,
                resumeContext = resumeContext,
                loading = true,
            )
        }
        applyLegalCoordinatorResult(
            legalCoordinator.load(
                session = session,
                resumeContext = resumeContext,
            )
        )
    }

    private suspend fun applyLegalCoordinatorResult(result: LegalCoordinatorResult) {
        when (result) {
            is LegalCoordinatorResult.Show -> _uiState.value = result.state
            is LegalCoordinatorResult.Satisfied -> when (val resume = result.resumeContext) {
                LegalResumeContext.PostSession -> continueReadySession(result.session)
                LegalResumeContext.PostReactivation ->
                    homeCoordinator.reenterMatchmakingOrLoadHome(result.session)

                is LegalResumeContext.ExistingState ->
                    _uiState.value = resume.state.clearLegalActionRequiredForResume()
            }
        }
    }

    private fun observeLegalActionRequired() {
        viewModelScope.launch {
            uiState.collect { current ->
                if (current is RealsRootUiState.LegalRequirements) return@collect
                if (legalRerouteJob?.isActive == true) return@collect
                if (!current.hasLegalActionRequiredError()) return@collect
                val session = current.sessionForLegalResume() ?: return@collect
                legalRerouteJob = launch {
                    enterLegalRequirements(
                        session = session,
                        resumeContext = LegalResumeContext.ExistingState(current),
                    )
                }
            }
        }
    }

    private fun observeTerminalAuthFailure() {
        viewModelScope.launch {
            uiState.collect { current ->
                if (sessionInvalidationJob?.isActive == true) return@collect
                if (!current.hasTerminalAuthFailure()) return@collect
                cancelSilentRefreshFor(current)
                sessionInvalidationJob = launch {
                    sessionCoordinator.invalidateTerminalSession()
                }
            }
        }
    }

    private fun observeUserPairBlocked() {
        viewModelScope.launch {
            uiState.collect { current ->
                if (pairBlockedRerouteJob?.isActive == true) return@collect
                if (!current.hasUserPairBlockedInteractionError()) return@collect
                val session = current.blockedPairSession() ?: return@collect
                cancelSilentRefreshFor(current)
                pairBlockedRerouteJob = launch {
                    homeCoordinator.returnHome(
                        session = session,
                        message = "Esta interacción ya no está disponible. Actualizamos tu Home.",
                    )
                }
            }
        }
    }

    private fun cancelSilentRefreshFor(current: RealsRootUiState) {
        when (current) {
            is RealsRootUiState.FirstChat -> silentFirstChatRefreshJob?.cancel()
            is RealsRootUiState.SecondChat -> silentSecondChatRefreshJob?.cancel()
            is RealsRootUiState.Scheduling -> silentSchedulingRefreshJob?.cancel()
            else -> Unit
        }
    }

    private fun returnHomeFromExternalNotification(session: ProvisionedSession) {
        viewModelScope.launch {
            homeCoordinator.returnHome(session)
        }
    }
}

private fun RealsRootUiState.hasLegalActionRequiredError(): Boolean = when (this) {
    is RealsRootUiState.Ready ->
        profileCreateError.isLegalActionRequiredError() ||
            countriesError.isLegalActionRequiredError() ||
            profileUpdateError.isLegalActionRequiredError() ||
            matchFiltersError.isLegalActionRequiredError() ||
            profileActivationError.isLegalActionRequiredError() ||
            photoReorderError.isLegalActionRequiredError() ||
            photoActionError.isLegalActionRequiredError() ||
            homeError.isLegalActionRequiredError() ||
            matchmakingBlockedReason.isLegalActionRequiredError()

    is RealsRootUiState.FirstChat -> error.isLegalActionRequiredError()
    is RealsRootUiState.SecondChat -> error.isLegalActionRequiredError()
    is RealsRootUiState.VisualApproval -> error.isLegalActionRequiredError()
    is RealsRootUiState.Scheduling -> error.isLegalActionRequiredError()
    else -> false
}

internal fun RealsRootUiState.hasTerminalAuthFailure(): Boolean = when (this) {
    is RealsRootUiState.Failure -> error.isTerminalAuthFailure()
    is RealsRootUiState.AccountDeletionPending -> error.isTerminalAuthFailure()
    is RealsRootUiState.LegalRequirements ->
        error.isTerminalAuthFailure() || accountDeleteError.isTerminalAuthFailure()

    is RealsRootUiState.Ready ->
        profileCreateError.isTerminalAuthFailure() ||
            countriesError.isTerminalAuthFailure() ||
            profileUpdateError.isTerminalAuthFailure() ||
            matchFiltersError.isTerminalAuthFailure() ||
            profileActivationError.isTerminalAuthFailure() ||
            profilePhotosError.isTerminalAuthFailure() ||
            photoReorderError.isTerminalAuthFailure() ||
            photoActionError.isTerminalAuthFailure() ||
            homeError.isTerminalAuthFailure() ||
            matchmakingBlockedReason.isTerminalAuthFailure() ||
            accountDeleteError.isTerminalAuthFailure()

    is RealsRootUiState.FirstChat ->
        error.isTerminalAuthFailure() || manualBlock.error.isTerminalAuthFailure()

    is RealsRootUiState.SecondChat ->
        error.isTerminalAuthFailure() || manualBlock.error.isTerminalAuthFailure()

    is RealsRootUiState.VisualApproval ->
        error.isTerminalAuthFailure() ||
            partnerMessageError.isTerminalAuthFailure() ||
            manualBlock.error.isTerminalAuthFailure()

    is RealsRootUiState.Scheduling ->
        error.isTerminalAuthFailure() || manualBlock.error.isTerminalAuthFailure()

    is RealsRootUiState.PartnerProfile ->
        error.isTerminalAuthFailure() || manualBlock.error.isTerminalAuthFailure()

    else -> false
}

private fun RealsRootUiState.sessionForLegalResume(): ProvisionedSession? = when (this) {
    is RealsRootUiState.Ready -> session
    is RealsRootUiState.FirstChat -> session
    is RealsRootUiState.SecondChat -> session
    is RealsRootUiState.VisualApproval -> session
    is RealsRootUiState.Scheduling -> session
    else -> null
}

private fun ApiError?.isLegalActionRequiredError(): Boolean =
    this?.isLegalActionRequired() == true

private fun ApiError?.isTerminalAuthFailure(): Boolean =
    this?.isTerminalAuthFailure() == true

private fun RealsRootUiState.hasUserPairBlockedInteractionError(): Boolean = when (this) {
    is RealsRootUiState.FirstChat -> error.isUserPairBlockedError()
    is RealsRootUiState.SecondChat -> error.isUserPairBlockedError()
    is RealsRootUiState.VisualApproval -> error.isUserPairBlockedError()
    is RealsRootUiState.Scheduling -> error.isUserPairBlockedError()
    else -> false
}

private fun RealsRootUiState.blockedPairSession(): ProvisionedSession? = when (this) {
    is RealsRootUiState.FirstChat -> session
    is RealsRootUiState.SecondChat -> session
    is RealsRootUiState.VisualApproval -> session
    is RealsRootUiState.Scheduling -> session
    else -> null
}

private fun ApiError?.isUserPairBlockedError(): Boolean =
    this?.isUserPairBlocked() == true

private fun RealsRootUiState.FirstChat.withFreshGuidanceFrom(
    displayed: RealsRootUiState.FirstChat,
): RealsRootUiState.FirstChat {
    if (matchId != displayed.matchId || chatId != displayed.chatId) return this
    return copy(chat = chat.withFreshGuidanceFrom(displayed.chat))
}

private fun Chat?.withFreshGuidanceFrom(displayed: Chat?): Chat? {
    if (this == null || displayed == null || id != displayed.id) return this
    return copy(guidance = guidance.freshOrDisplayed(displayed.guidance))
}

private fun FirstChatGuidance?.freshOrDisplayed(
    displayed: FirstChatGuidance?,
): FirstChatGuidance? {
    if (displayed == null) return this
    if (this == null) return displayed
    if (questionOrdinal < displayed.questionOrdinal) return displayed
    if (questionOrdinal > displayed.questionOrdinal) return this

    val sameQuestion = question.id == displayed.question.id
    if (sameQuestion && displayed.completed && !completed) return displayed
    if (
        sameQuestion &&
        displayed.myNextRequested &&
        !myNextRequested &&
        !displayed.completed &&
        !completed
    ) {
        return displayed
    }

    return this
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

private data class SecondChatExpiryKey(
    val connectionId: String,
    val chatId: String?,
)

private fun RealsRootUiState.SecondChat.expiryKey(): SecondChatExpiryKey =
    SecondChatExpiryKey(
        connectionId = connectionId,
        chatId = chatId ?: lifecycle.status?.chatId,
    )

private fun RealsRootUiState.SecondChat.matches(key: SecondChatExpiryKey): Boolean =
    connectionId == key.connectionId &&
        (key.chatId == null || expiryKey().chatId == key.chatId)

private fun RealsRootUiState.SecondChat.hasTerminalSecondChatStatus(): Boolean =
    lifecycle.status?.chatStatus in setOf(
        ChatStatus.Expired,
        ChatStatus.Finished,
        ChatStatus.Abandoned,
    )
