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
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.BackendUserStatus
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ChatDecisionState
import com.reals.app.domain.model.ChatExitOutcome
import com.reals.app.domain.model.ChatExitReason
import com.reals.app.domain.model.ChatExitRequest
import com.reals.app.domain.model.ChatExitRequestStatus
import com.reals.app.domain.model.ChatMessage
import com.reals.app.domain.model.ChatStatus
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.Match
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.model.VisualDecision
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddMockProfilePhotoUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.ReplaceMockProfilePhotoUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RealsRootUiState {
    data object Checking : RealsRootUiState
    data class MissingFirebase(val message: String) : RealsRootUiState
    data class Login(val loading: Boolean = false, val error: String? = null) : RealsRootUiState
    data class LoadingSession(val email: String?) : RealsRootUiState
    data class AccountDeletionScheduled(
        val deletionFinalizesAt: String?,
    ) : RealsRootUiState
    data class AccountDeletionPending(
        val user: BackendUser,
        val reactivating: Boolean = false,
        val error: ApiError? = null,
    ) : RealsRootUiState
    data class Ready(
        val session: ProvisionedSession,
        val creatingProfile: Boolean = false,
        val profileCreateError: ApiError? = null,
        val updatingProfile: Boolean = false,
        val profileUpdateError: ApiError? = null,
        val profileUpdateMessage: String? = null,
        val updatingMatchFilters: Boolean = false,
        val matchFiltersError: ApiError? = null,
        val matchFiltersMessage: String? = null,
        val loadingPhotos: Boolean = false,
        val profilePhotos: List<ProfilePhoto> = emptyList(),
        val profilePhotosError: ApiError? = null,
        val addingPhoto: Boolean = false,
        val photoActionError: ApiError? = null,
        val photoActionMessage: String? = null,
        val activatingProfile: Boolean = false,
        val profileActivationError: ApiError? = null,
        val deletingAccount: Boolean = false,
        val accountDeleteError: ApiError? = null,
        val homeState: HomeState? = null,
        val homeLoading: Boolean = false,
        val homeError: ApiError? = null,
        val homeMessage: String? = null,
        val editingActiveProfile: Boolean = false,
    ) : RealsRootUiState
    data class FirstChat(
        val session: ProvisionedSession,
        val matchId: String,
        val chatId: String? = null,
        val match: Match? = null,
        val chat: Chat? = null,
        val messages: List<ChatMessage> = emptyList(),
        val exitRequests: List<ChatExitRequest> = emptyList(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val sending: Boolean = false,
        val actionLoading: Boolean = false,
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState
    data class VisualApproval(
        val session: ProvisionedSession,
        val matchId: String,
        val match: Match? = null,
        val profile: VisualProfile? = null,
        val partnerMessage: String? = null,
        val partnerMessageLoaded: Boolean = false,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val writingMessage: Boolean = false,
        val deciding: Boolean = false,
        val error: ApiError? = null,
        val message: String? = null,
    ) : RealsRootUiState
    data class PendingEngagement(
        val session: ProvisionedSession,
        val title: String,
        val body: String,
    ) : RealsRootUiState
    data class ActivationComplete(
        val session: ProvisionedSession,
        val result: ProfileActivationResult,
    ) : RealsRootUiState
    data class Failure(val error: ApiError) : RealsRootUiState
}

class RealsRootViewModel(
    private val authRepository: FirebaseAuthRepository,
    private val provisionAndLoadProfile: ProvisionAndLoadProfileUseCase,
    private val createProfileUseCase: CreateProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val updateMatchFiltersUseCase: UpdateMatchFiltersUseCase,
    private val getMeUseCase: GetMeUseCase,
    private val getProfilePhotosUseCase: GetProfilePhotosUseCase,
    private val addMockProfilePhotoUseCase: AddMockProfilePhotoUseCase,
    private val addProfilePhotoFileUseCase: AddProfilePhotoFileUseCase,
    private val replaceMockProfilePhotoUseCase: ReplaceMockProfilePhotoUseCase,
    private val replaceProfilePhotoFileUseCase: ReplaceProfilePhotoFileUseCase,
    private val deleteProfilePhotoUseCase: DeleteProfilePhotoUseCase,
    private val activateProfileUseCase: ActivateProfileUseCase,
    private val reactivateAccountUseCase: ReactivateAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val enqueueMatchmakingUseCase: EnqueueMatchmakingUseCase,
    private val getHomeUseCase: GetHomeUseCase,
    private val leaveQueueUseCase: LeaveQueueUseCase,
    private val getMatchUseCase: GetMatchUseCase,
    private val getFirstChatForMatchUseCase: GetFirstChatForMatchUseCase,
    private val submitChatDecisionUseCase: SubmitChatDecisionUseCase,
    private val getVisualProfileUseCase: GetVisualProfileUseCase,
    private val submitVisualDecisionUseCase: SubmitVisualDecisionUseCase,
    private val putMyPersonalMessageUseCase: PutMyPersonalMessageUseCase,
    private val getPartnerPersonalMessageUseCase: GetPartnerPersonalMessageUseCase,
    private val getChatMessagesUseCase: GetChatMessagesUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val getChatExitRequestsUseCase: GetChatExitRequestsUseCase,
    private val requestMutualChatExitUseCase: RequestMutualChatExitUseCase,
    private val acceptChatExitRequestUseCase: AcceptChatExitRequestUseCase,
    private val rejectChatExitRequestUseCase: RejectChatExitRequestUseCase,
    private val cancelChatUseCase: CancelChatUseCase,
    private val safetyCancelChatUseCase: SafetyCancelChatUseCase,
) : ViewModel() {
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
                deletingAccount = true,
                accountDeleteError = null,
            )

            when (val result = deleteAccountUseCase()) {
                is ApiResult.Success -> {
                    _uiState.value = RealsRootUiState.AccountDeletionScheduled(
                        deletionFinalizesAt = result.value.deletionFinalizesAt,
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        deletingAccount = false,
                        accountDeleteError = result.error,
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
                ready = current.copy(homeLoading = true, homeError = null, homeMessage = null),
                autoNavigateEngagements = current.homeState?.queue?.inQueue == true,
            )
        }
    }

    fun enqueueMatchmaking(location: SearchLocationInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            lastSearchLocation = location
            val pending = current.copy(homeLoading = true, homeError = null, homeMessage = null)
            _uiState.value = pending
            when (val result = enqueueMatchmakingUseCase(location)) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        homeLoading = true,
                        homeMessage = null,
                    ),
                    autoNavigateEngagements = true,
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    homeLoading = false,
                    homeError = result.error,
                )
            }
        }
    }

    fun leaveMatchmakingQueue() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return

        viewModelScope.launch {
            val pending = current.copy(homeLoading = true, homeError = null, homeMessage = null)
            _uiState.value = pending
            when (val result = leaveQueueUseCase()) {
                is ApiResult.Success -> loadHomeForReady(
                    ready = pending.copy(
                        homeLoading = true,
                        homeMessage = null,
                    ),
                    autoNavigateEngagements = false,
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    homeLoading = false,
                    homeError = result.error,
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
                        homeLoading = true,
                        homeError = null,
                        homeMessage = null,
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
        val cleanMatchId = matchId.trim()
        if (cleanMatchId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = RealsRootUiState.FirstChat(
                session = session,
                matchId = cleanMatchId,
                chatId = chatId,
                loading = true,
            )
            val matchResult = getMatchUseCase(cleanMatchId)
            if (matchResult is ApiResult.Failure) {
                _uiState.value = RealsRootUiState.FirstChat(
                    session = session,
                    matchId = cleanMatchId,
                    chatId = chatId,
                    loading = false,
                    error = matchResult.error,
                )
                return@launch
            }
            val match = (matchResult as ApiResult.Success).value
            if (match.state !is MatchState.Unknown && match.state != MatchState.ChatActive) {
                loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = session,
                        homeLoading = true,
                        homeMessage = firstChatExitMessage(match.state),
                    ),
                    autoNavigateEngagements = false,
                )
                return@launch
            }
            val chatResult = getFirstChatForMatchUseCase(cleanMatchId)
            if (chatResult is ApiResult.Failure) {
                _uiState.value = RealsRootUiState.FirstChat(
                    session = session,
                    matchId = cleanMatchId,
                    chatId = chatId,
                    match = match,
                    loading = false,
                    error = chatResult.error,
                )
                return@launch
            }
            val chat = (chatResult as ApiResult.Success).value
            if (!chat.status.isOpenFirstChatStatus()) {
                loadHomeForReady(
                    ready = RealsRootUiState.Ready(
                        session = session,
                        homeLoading = true,
                        homeMessage = "El chat cambio de estado. Actualizamos tu Home.",
                    ),
                    autoNavigateEngagements = false,
                )
                return@launch
            }
            val messagesResult = getChatMessagesUseCase(chat.id)
            val exitsResult = getChatExitRequestsUseCase(chat.id)
            _uiState.value = RealsRootUiState.FirstChat(
                session = session,
                matchId = cleanMatchId,
                chatId = chat.id,
                match = match,
                chat = chat,
                messages = (messagesResult as? ApiResult.Success)?.value.orEmpty(),
                exitRequests = (exitsResult as? ApiResult.Success)?.value.orEmpty(),
                loading = false,
                error = (messagesResult as? ApiResult.Failure)?.error ?: (exitsResult as? ApiResult.Failure)?.error,
            )
        }
    }

    fun closeFirstChat() {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        _uiState.value = RealsRootUiState.Ready(current.session, homeLoading = true)
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
                    ready = RealsRootUiState.Ready(session = session, homeLoading = true),
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
                ready = RealsRootUiState.Ready(current.session, homeLoading = true),
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

    fun refreshFirstChat(silent: Boolean = false) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        if (current.refreshing || current.sending || current.actionLoading) return
        val chat = current.chat ?: return openFirstChat(current.matchId, current.chatId)

        viewModelScope.launch {
            val pending = current.copy(
                refreshing = true,
                error = if (silent) current.error else null,
                message = if (silent) current.message else null,
            )
            _uiState.value = pending
            val chatResult = getFirstChatForMatchUseCase(current.matchId)
            val matchResult = getMatchUseCase(current.matchId)
            val messagesResult = getChatMessagesUseCase(chat.id, pending.messages.lastMessageCursor())
            val exitsResult = getChatExitRequestsUseCase(chat.id)
            val updatedMatch = (matchResult as? ApiResult.Success)?.value ?: pending.match
            val updatedChat = (chatResult as? ApiResult.Success)?.value ?: pending.chat
            val updatedExitRequests = (exitsResult as? ApiResult.Success)?.value ?: pending.exitRequests
            if (
                (updatedMatch != null && updatedMatch.state !is MatchState.Unknown && updatedMatch.state != MatchState.ChatActive) ||
                (updatedChat != null && !updatedChat.status.isOpenFirstChatStatus())
            ) {
                locallyHiddenPendingChatMatchIds += current.matchId
                routeAfterFirstChatClosed(current.session, updatedMatch?.state)
                return@launch
            }
            if (updatedExitRequests.latestExitRequest()?.status.isResolvedExitStatus()) {
                locallyHiddenPendingChatMatchIds += current.matchId
                reenterMatchmakingOrLoadHome(current.session)
                return@launch
            }
            _uiState.value = pending.copy(
                match = updatedMatch,
                chat = updatedChat,
                messages = (messagesResult as? ApiResult.Success)?.value
                    ?.let { pending.messages.appendUnique(it) }
                    ?: pending.messages,
                exitRequests = updatedExitRequests,
                refreshing = false,
                error = if (silent) {
                    pending.error
                } else {
                    (chatResult as? ApiResult.Failure)?.error
                        ?: (matchResult as? ApiResult.Failure)?.error
                        ?: (messagesResult as? ApiResult.Failure)?.error
                        ?: (exitsResult as? ApiResult.Failure)?.error
                },
            )
        }
    }

    fun returnToHomeFromPendingEngagement() {
        val current = _uiState.value as? RealsRootUiState.PendingEngagement ?: return
        _uiState.value = RealsRootUiState.Ready(current.session, homeLoading = true)
        refreshHomeState()
    }

    fun sendFirstChatMessage(content: String) {
        val current = _uiState.value as? RealsRootUiState.FirstChat ?: return
        val chat = current.chat ?: return
        val cleanContent = TextSafety.normalizeMultiline(content, maxLength = 1_000)
        if (cleanContent.isBlank() || TextSafety.containsHtmlLikeMarkup(cleanContent)) {
            _uiState.value = current.copy(
                error = ApiError.Unexpected("El mensaje no es valido."),
                message = null,
            )
            return
        }

        viewModelScope.launch {
            val cursorBeforeSend = current.messages.lastMessageCursor()
            val pending = current.copy(sending = true, error = null, message = null)
            _uiState.value = pending
            when (val result = sendChatMessageUseCase(chat.id, cleanContent)) {
                is ApiResult.Success -> {
                    val messagesResult = getChatMessagesUseCase(chat.id, cursorBeforeSend)
                    val chatResult = getFirstChatForMatchUseCase(current.matchId)
                    _uiState.value = pending.copy(
                        chat = (chatResult as? ApiResult.Success)?.value ?: pending.chat,
                        messages = pending.messages.appendUnique(
                            (messagesResult as? ApiResult.Success)?.value.orEmpty() + result.value
                        ),
                        sending = false,
                        error = (messagesResult as? ApiResult.Failure)?.error
                            ?: (chatResult as? ApiResult.Failure)?.error,
                    )
                }

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    sending = false,
                    error = result.error,
                )
            }
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
                    if (decision == ChatContinueDecision.Approved && result.value.state == MatchState.ChatActive) {
                        locallyHiddenPendingChatMatchIds += current.matchId
                        reenterMatchmakingOrLoadHome(current.session)
                        return@launch
                    }

                    if (decision == ChatContinueDecision.Rejected) {
                        locallyHiddenPendingChatMatchIds += current.matchId
                        reenterMatchmakingOrLoadHome(current.session)
                        return@launch
                    }

                    loadHomeForReady(
                        ready = RealsRootUiState.Ready(
                            session = current.session,
                            homeLoading = true,
                            homeMessage = firstChatDecisionMessage(result.value.state),
                        ),
                        autoNavigateEngagements = false,
                    )
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
            val pending = current.copy(writingMessage = true, error = null, message = null)
            _uiState.value = pending
            when (val result = putMyPersonalMessageUseCase(current.matchId, cleanMessage)) {
                is ApiResult.Success -> _uiState.value = pending.copy(
                    writingMessage = false,
                    message = "Guardamos tu mensaje personal.",
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    writingMessage = false,
                    error = result.error,
                )
            }
        }
    }

    fun submitVisualDecision(decision: VisualDecision) {
        val current = _uiState.value as? RealsRootUiState.VisualApproval ?: return
        viewModelScope.launch {
            val pending = current.copy(deciding = true, error = null, message = null)
            _uiState.value = pending
            when (val result = submitVisualDecisionUseCase(current.matchId, decision)) {
                is ApiResult.Success -> {
                    locallyHiddenVisualMatchIds += current.matchId
                    when (result.value.state) {
                        MatchState.VisualPhase -> loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                homeLoading = true,
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.VisualApproved -> loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                homeLoading = true,
                            ),
                            autoNavigateEngagements = false,
                        )

                        MatchState.VisualRejected,
                        MatchState.ChatRejected,
                        MatchState.Expired -> loadHomeForReady(
                            ready = RealsRootUiState.Ready(
                                session = current.session,
                                homeLoading = true,
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
            _uiState.value = current.copy(creatingProfile = true, profileCreateError = null)
            when (val result = createProfileUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = current.copy(
                        session = current.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        creatingProfile = false,
                        profileCreateError = null,
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        creatingProfile = false,
                        profileCreateError = result.error,
                    )
                }
            }
        }
    }

    fun updateProfile(input: UpdateProfileInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(
                updatingProfile = true,
            )
            _uiState.value = pending
            when (val result = updateProfileUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        updatingProfile = false,
                        profileUpdateMessage = "Perfil actualizado.",
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        updatingProfile = false,
                        profileUpdateError = result.error,
                    )
                }
            }
        }
    }

    fun updateMatchFilters(input: UpdateMatchFiltersInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(
                updatingMatchFilters = true,
            )
            _uiState.value = pending
            when (val result = updateMatchFiltersUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = pending.copy(
                        session = pending.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        updatingMatchFilters = false,
                        matchFiltersMessage = "Filtros actualizados.",
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        updatingMatchFilters = false,
                        matchFiltersError = result.error,
                    )
                }
            }
        }
    }

    fun loadProfilePhotos() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        if (current.session.profileSnapshot !is ProfileSnapshot.Found) return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(loadingPhotos = true)
            _uiState.value = pending
            when (val result = getProfilePhotosUseCase.invoke()) {
                is ApiResult.Success -> _uiState.value = pending.copy(
                    loadingPhotos = false,
                    profilePhotos = result.value.sortedBy { it.position },
                )

                is ApiResult.Failure -> _uiState.value = pending.copy(
                    loadingPhotos = false,
                    profilePhotosError = result.error,
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
            val pending = current.clearProfileFeedback().copy(
                addingPhoto = true,
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
                                profilePhotos = updatedPhotos,
                                profilePhotosError = null,
                                addingPhoto = false,
                                photoActionMessage = "Foto agregada correctamente.",
                            )
                        }

                        is ApiResult.Failure -> _uiState.value = pending.copy(
                            addingPhoto = false,
                            photoActionError = refreshedSession.error,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        addingPhoto = false,
                        photoActionError = result.error,
                    )
                }
            }
        }
    }

    fun addProfilePhotoFile(position: Int, fileUri: Uri) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(
                addingPhoto = true,
            )
            _uiState.value = pending
            when (val result = addProfilePhotoFileUseCase.invoke(fileUri = fileUri, position = position)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
                    successMessage = "Foto subida correctamente.",
                )

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        addingPhoto = false,
                        photoActionError = result.error,
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
            val pending = current.clearProfileFeedback().copy(
                addingPhoto = true,
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
                                profilePhotos = updatedPhotos,
                                profilePhotosError = null,
                                addingPhoto = false,
                                photoActionMessage = "Foto reemplazada correctamente.",
                            )
                        }

                        is ApiResult.Failure -> _uiState.value = pending.copy(
                            addingPhoto = false,
                            photoActionError = refreshedSession.error,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        addingPhoto = false,
                        photoActionError = result.error,
                    )
                }
            }
        }
    }

    fun replaceProfilePhotoFile(photoId: String, position: Int, fileUri: Uri) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(
                addingPhoto = true,
            )
            _uiState.value = pending
            when (val result = replaceProfilePhotoFileUseCase.invoke(photoId = photoId, fileUri = fileUri)) {
                is ApiResult.Success -> refreshAfterPhotoMutation(
                    previous = pending,
                    successMessage = "Foto reemplazada correctamente.",
                )

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        addingPhoto = false,
                        photoActionError = result.error,
                    )
                }
            }
        }
    }

    fun deleteProfilePhoto(photoId: String, position: Int) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(
                addingPhoto = true,
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
                        profilePhotos = updatedPhotos,
                        profilePhotosError = null,
                        addingPhoto = false,
                        photoActionMessage = "Foto eliminada.",
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = pending.copy(
                        addingPhoto = false,
                        photoActionError = result.error,
                    )
                }
            }
        }
    }

    fun activateProfile() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            val pending = current.clearProfileFeedback().copy(activatingProfile = true)
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
                        activatingProfile = false,
                        profileActivationError = result.error,
                    )
                }
            }
        }
    }

    private suspend fun refreshAfterPhotoMutation(
        previous: RealsRootUiState.Ready,
        successMessage: String,
    ) {
        when (val refreshedSession = provisionAndLoadProfile()) {
            is ApiResult.Success -> {
                val refreshedPhotos = getProfilePhotosUseCase.invoke()
                val updatedPhotos = (refreshedPhotos as? ApiResult.Success)?.value
                    ?.sortedBy { it.position }
                    ?: previous.profilePhotos
                _uiState.value = previous.copy(
                    session = refreshedSession.value,
                    profilePhotos = updatedPhotos,
                    profilePhotosError = null,
                    addingPhoto = false,
                    photoActionMessage = successMessage,
                )
            }

            is ApiResult.Failure -> _uiState.value = previous.copy(
                addingPhoto = false,
                photoActionError = refreshedSession.error,
            )
        }
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

        val matchResult = getMatchUseCase(matchId)
        val match = (matchResult as? ApiResult.Success)?.value ?: initialMatch
        if (matchId in locallyHiddenVisualMatchIds) {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(session = session, homeLoading = true),
                autoNavigateEngagements = false,
            )
            return
        }
        if (match != null && match.state != MatchState.VisualPhase && match.state !is MatchState.Unknown) {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = session,
                    homeLoading = true,
                    homeMessage = "La revision visual cambio de estado. Actualizamos tu Home.",
                ),
                autoNavigateEngagements = false,
            )
            return
        }

        val profileResult = getVisualProfileUseCase(matchId)
        val partnerMessageResult = getPartnerPersonalMessageUseCase(matchId)
        _uiState.value = loadingState.copy(
            match = match,
            profile = (profileResult as? ApiResult.Success)?.value ?: loadingState.profile,
            partnerMessage = (partnerMessageResult as? ApiResult.Success)?.value ?: loadingState.partnerMessage,
            partnerMessageLoaded = partnerMessageResult is ApiResult.Success || loadingState.partnerMessageLoaded,
            loading = false,
            refreshing = false,
            error = (matchResult as? ApiResult.Failure)?.error
                ?: (profileResult as? ApiResult.Failure)?.error
                ?: (partnerMessageResult as? ApiResult.Failure)?.error,
        )
    }

    private fun RealsRootUiState.Ready.clearProfileFeedback(): RealsRootUiState.Ready = copy(
        profileUpdateError = null,
        profileUpdateMessage = null,
        matchFiltersError = null,
        matchFiltersMessage = null,
        profilePhotosError = null,
        photoActionError = null,
        photoActionMessage = null,
        profileActivationError = null,
    )

    private suspend fun loadHomeForReady(
        ready: RealsRootUiState.Ready,
        publishLoadingState: Boolean = true,
        autoNavigateEngagements: Boolean = false,
    ) {
        if (publishLoadingState) {
            _uiState.value = ready.copy(homeLoading = true, homeError = null)
        }
        when (val homeResult = getHomeUseCase()) {
            is ApiResult.Success -> routeFromHomeState(
                ready = ready.copy(
                    homeState = homeResult.value.withLocallyHiddenMatches(),
                    homeLoading = false,
                    homeError = null,
                ),
                autoNavigateEngagements = autoNavigateEngagements,
            )

            is ApiResult.Failure -> _uiState.value = ready.copy(
                homeLoading = false,
                homeError = homeResult.error,
                homeMessage = null,
            )
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
            openFirstChat(firstChatMatch.matchId, firstChatMatch.firstChat.chatId)
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

    private fun ChatStatus.isOpenFirstChatStatus(): Boolean = this == ChatStatus.Active

    private fun HomeState.withLocallyHiddenMatches(): HomeState = copy(
        activeMatches = activeMatches.filterNot { match ->
            (match.matchState == MatchState.ChatActive &&
                match.matchId in locallyHiddenPendingChatMatchIds) ||
                (match.matchState == MatchState.VisualPhase &&
                    match.matchId in locallyHiddenVisualMatchIds)
        },
    )

    private fun firstChatDecisionMessage(state: MatchState): String = when (state) {
        MatchState.ChatActive -> "Guardamos tu decision. Esperamos la respuesta de la otra persona."
        MatchState.VisualPhase -> "Ambas personas aprobaron. La revision visual ya esta pendiente."
        MatchState.ChatRejected -> "Buscando una nueva conversacion."
        MatchState.Expired -> "El chat expiro. Actualizamos tu Home."
        MatchState.VisualApproved -> "La revision ya fue aprobada. Actualizamos tu Home."
        MatchState.VisualRejected -> "La revision visual quedo cerrada. Actualizamos tu Home."
        is MatchState.Unknown -> "Guardamos tu decision. Actualizamos tu Home."
    }

    private suspend fun reenterMatchmakingOrLoadHome(session: ProvisionedSession) {
        val location = lastSearchLocation
        val ready = RealsRootUiState.Ready(session = session, homeLoading = true)
        if (location == null) {
            loadHomeForReady(ready = ready, autoNavigateEngagements = false)
            return
        }

        when (val enqueueResult = enqueueMatchmakingUseCase(location)) {
            is ApiResult.Success -> loadHomeForReady(
                ready = ready,
                autoNavigateEngagements = true,
            )

            is ApiResult.Failure -> loadHomeForReady(
                ready = ready.copy(homeError = enqueueResult.error),
                autoNavigateEngagements = false,
            )
        }
    }

    private suspend fun routeAfterFirstChatClosed(session: ProvisionedSession, state: MatchState?) {
        if (state == MatchState.VisualPhase) {
            loadHomeForReady(
                ready = RealsRootUiState.Ready(
                    session = session,
                    homeLoading = true,
                    homeMessage = firstChatExitMessage(state),
                ),
                autoNavigateEngagements = false,
            )
            return
        }

        reenterMatchmakingOrLoadHome(session)
    }

    private fun List<ChatMessage>.lastMessageCursor(): String? =
        maxByOrNull { it.sentAt }?.id

    private fun List<ChatMessage>.appendUnique(newMessages: List<ChatMessage>): List<ChatMessage> {
        val seen = map { it.id }.toMutableSet()
        return (this + newMessages.filter { seen.add(it.id) }).sortedBy { it.sentAt }
    }

    private fun List<ChatExitRequest>.latestExitRequest(): ChatExitRequest? =
        maxByOrNull { it.createdAt }

    private fun ChatExitRequestStatus?.isResolvedExitStatus(): Boolean =
        this == ChatExitRequestStatus.Accepted || this == ChatExitRequestStatus.Rejected

    private fun firstChatExitMessage(state: MatchState?): String = when (state) {
        MatchState.VisualPhase -> "El chat paso a revision visual. Actualizamos tu lista."
        MatchState.ChatRejected -> "Buscando una nueva conversacion."
        MatchState.Expired -> "El chat expiro. Actualizamos tu Home."
        MatchState.VisualApproved -> "La revision ya fue aprobada. Actualizamos tu Home."
        MatchState.VisualRejected -> "La revision visual quedo cerrada. Actualizamos tu Home."
        MatchState.ChatActive,
        null,
        is MatchState.Unknown -> "El chat cambio de estado. Actualizamos tu Home."
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
                    RealsRootUiState.Ready(
                        session = session,
                        homeLoading = true,
                    ),
                    publishLoadingState = false,
                )
                return
            }

            _uiState.value = RealsRootUiState.Ready(session, loadingPhotos = true)
            when (val photos = getProfilePhotosUseCase.invoke()) {
                is ApiResult.Success -> _uiState.value = RealsRootUiState.Ready(
                    session = session,
                    loadingPhotos = false,
                    profilePhotos = photos.value.sortedBy { it.position },
                )

                is ApiResult.Failure -> {
                    if (photos.error.isAccountDeleted()) {
                        showAccountDeletionPendingFromBackend()
                    } else {
                        _uiState.value = RealsRootUiState.Ready(
                            session = session,
                            loadingPhotos = false,
                            profilePhotosError = photos.error,
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
                authRepository = appContainer.authRepository,
                provisionAndLoadProfile = appContainer.provisionAndLoadProfileUseCase,
                createProfileUseCase = appContainer.createProfileUseCase,
                updateProfileUseCase = appContainer.updateProfileUseCase,
                updateMatchFiltersUseCase = appContainer.updateMatchFiltersUseCase,
                getMeUseCase = appContainer.getMeUseCase,
                getProfilePhotosUseCase = appContainer.getProfilePhotosUseCase,
                addMockProfilePhotoUseCase = appContainer.addMockProfilePhotoUseCase,
                addProfilePhotoFileUseCase = appContainer.addProfilePhotoFileUseCase,
                replaceMockProfilePhotoUseCase = appContainer.replaceMockProfilePhotoUseCase,
                replaceProfilePhotoFileUseCase = appContainer.replaceProfilePhotoFileUseCase,
                deleteProfilePhotoUseCase = appContainer.deleteProfilePhotoUseCase,
                activateProfileUseCase = appContainer.activateProfileUseCase,
                reactivateAccountUseCase = appContainer.reactivateAccountUseCase,
                deleteAccountUseCase = appContainer.deleteAccountUseCase,
                enqueueMatchmakingUseCase = appContainer.enqueueMatchmakingUseCase,
                getHomeUseCase = appContainer.getHomeUseCase,
                leaveQueueUseCase = appContainer.leaveQueueUseCase,
                getMatchUseCase = appContainer.getMatchUseCase,
                getFirstChatForMatchUseCase = appContainer.getFirstChatForMatchUseCase,
                submitChatDecisionUseCase = appContainer.submitChatDecisionUseCase,
                getVisualProfileUseCase = appContainer.getVisualProfileUseCase,
                submitVisualDecisionUseCase = appContainer.submitVisualDecisionUseCase,
                putMyPersonalMessageUseCase = appContainer.putMyPersonalMessageUseCase,
                getPartnerPersonalMessageUseCase = appContainer.getPartnerPersonalMessageUseCase,
                getChatMessagesUseCase = appContainer.getChatMessagesUseCase,
                sendChatMessageUseCase = appContainer.sendChatMessageUseCase,
                getChatExitRequestsUseCase = appContainer.getChatExitRequestsUseCase,
                requestMutualChatExitUseCase = appContainer.requestMutualChatExitUseCase,
                acceptChatExitRequestUseCase = appContainer.acceptChatExitRequestUseCase,
                rejectChatExitRequestUseCase = appContainer.rejectChatExitRequestUseCase,
                cancelChatUseCase = appContainer.cancelChatUseCase,
                safetyCancelChatUseCase = appContainer.safetyCancelChatUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}

