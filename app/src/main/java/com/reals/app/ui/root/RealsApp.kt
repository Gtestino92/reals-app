package com.reals.app.ui.root

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reals.app.core.network.ErrorContext
import com.reals.app.di.AppContainer
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.VisualDecision
import com.reals.app.ui.account.AccountDeletionRecoveryScreen
import com.reals.app.ui.auth.LoginScreen
import com.reals.app.ui.chat.ChatScreen
import com.reals.app.ui.chat.PartnerProfileScreen
import com.reals.app.ui.chat.VisualApprovalScreen
import com.reals.app.ui.common.ApiErrorScreen
import com.reals.app.ui.common.FullScreenMessage
import com.reals.app.ui.common.formatBackendDate
import com.reals.app.ui.matchmaking.MatchmakingHomeScreen
import com.reals.app.ui.profile.CreateProfileScreen
import com.reals.app.ui.profile.ProfileActivationResultScreen
import com.reals.app.ui.profile.ProfileStatusScreen
import com.reals.app.ui.scheduling.SchedulingScreen

@Composable
fun RealsApp(
    appContainer: AppContainer,
    notificationOpenNonce: Long = 0L,
    notificationOpenType: String? = null,
) {
    val viewModel: RealsRootViewModel = viewModel(
        factory = RealsRootViewModelFactory(appContainer),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(notificationOpenNonce) {
        if (notificationOpenNonce != 0L) {
            viewModel.handleExternalNotificationOpened(notificationOpenType)
        }
    }

    NotificationPermissionGate(enabled = state is RealsRootUiState.Ready)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            RealsRootUiState.Checking -> FullScreenMessage(
                title = "Inicializando Reals",
                body = "Preparando autenticacion y cliente local.",
            )

            is RealsRootUiState.MissingFirebase -> FullScreenMessage(
                title = "Falta Firebase",
                body = current.message,
                primaryActionLabel = "Reintentar",
                onPrimaryAction = viewModel::refreshSession,
            )

            is RealsRootUiState.Login -> LoginScreen(
                loading = current.loading,
                error = current.error,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
            )

            is RealsRootUiState.LoadingSession -> FullScreenMessage(
                title = "Preparando tu cuenta",
                body = "Estamos cargando tu perfil${current.email?.let { " para $it" } ?: ""}.",
            )

            is RealsRootUiState.AccountDeletionScheduled -> FullScreenMessage(
                title = "Cuenta programada para eliminacion",
                body = "Tu cuenta fue programada para eliminacion. " +
                    "Podes recuperarla${
                        current.deletionFinalizesAt?.let { " hasta el ${formatBackendDate(it)}" }
                            ?: " durante 30 dias"
                    }.",
                primaryActionLabel = "Entendido",
                onPrimaryAction = viewModel::signOut,
            )

            is RealsRootUiState.AccountDeletionPending -> AccountDeletionRecoveryScreen(
                user = current.user,
                reactivating = current.reactivating,
                error = current.error,
                onReactivate = viewModel::reactivateAccount,
                onKeepDeletion = viewModel::signOut,
            )

            is RealsRootUiState.Ready -> when (current.session.profileSnapshot) {
                ProfileSnapshot.Missing -> CreateProfileScreen(
                    loading = current.creatingProfile,
                    error = current.profileCreateError,
                    accountDeleteLoading = current.deletingAccount,
                    accountDeleteError = current.accountDeleteError,
                    onSubmit = viewModel::createProfile,
                    onRefresh = viewModel::refreshSession,
                    onSignOut = viewModel::signOut,
                    onDeleteAccount = viewModel::deleteAccount,
                )

                is ProfileSnapshot.Found -> {
                    val profile = (current.session.profileSnapshot).profile
                    if (current.editingActiveProfile || profile.status != ProfileStatus.Active) {
                        ProfileStatusScreen(
                            session = current.session,
                            profileUpdateLoading = current.updatingProfile,
                            profileUpdateError = current.profileUpdateError,
                            profileUpdateMessage = current.profileUpdateMessage,
                            matchFiltersLoading = current.updatingMatchFilters,
                            matchFiltersError = current.matchFiltersError,
                            matchFiltersMessage = current.matchFiltersMessage,
                            photosLoading = current.loadingPhotos,
                            photos = current.profilePhotos,
                            photosError = current.profilePhotosError,
                            photoActionLoading = current.addingPhoto,
                            photoActionError = current.photoActionError,
                            photoActionMessage = current.photoActionMessage,
                            activationLoading = current.activatingProfile,
                            activationError = current.profileActivationError,
                            showDraftAfterEditNotice = current.editingActiveProfile &&
                                profile.status != ProfileStatus.Active,
                            onUpdateProfile = viewModel::updateProfile,
                            onUpdateMatchFilters = viewModel::updateMatchFilters,
                            onLoadPhotos = viewModel::loadProfilePhotos,
                            onAddMockPhoto = viewModel::addMockProfilePhoto,
                            onAddPhotoFile = viewModel::addProfilePhotoFile,
                            onReplaceMockPhoto = viewModel::replaceMockProfilePhoto,
                            onReplacePhotoFile = viewModel::replaceProfilePhotoFile,
                            onDeletePhoto = { photoId, position -> viewModel.deleteProfilePhoto(photoId, position) },
                            onActivateProfile = { viewModel.activateProfile() },
                            onRefresh = viewModel::refreshSession,
                            onSignOut = viewModel::signOut,
                            accountDeleteLoading = current.deletingAccount,
                            accountDeleteError = current.accountDeleteError,
                            onDeleteAccount = viewModel::deleteAccount,
                            onBackHome = if (
                                current.editingActiveProfile &&
                                profile.status == ProfileStatus.Active
                            ) {
                                viewModel::closeProfileManagement
                            } else {
                                null
                            },
                        )
                    } else {
                        MatchmakingHomeScreen(
                            profile = profile,
                            screenModel = current.home.screenModel,
                            homeLoading = current.homeLoading,
                            homeError = current.homeError,
                            homeMessage = current.homeMessage,
                            matchmakingSearchPhase = current.home.matchmakingSearchPhase,
                            accountDeleteLoading = current.deletingAccount,
                            accountDeleteError = current.accountDeleteError,
                            onEnqueue = viewModel::enqueueMatchmaking,
                            onDeviceLocationResolved = viewModel::enqueueMatchmakingFromResolvedDeviceLocation,
                            onCancelSearch = viewModel::cancelMatchmakingSearch,
                            onBeginLocationResolution = viewModel::beginMatchmakingLocationResolution,
                            onFailSearchPreparation = viewModel::failMatchmakingSearchPreparation,
                            onRefreshHome = viewModel::refreshHomeState,
                            onPollHome = viewModel::pollHomeStateSilently,
                            onOpenFirstChat = { matchId, chatId -> viewModel.openFirstChat(matchId, chatId) },
                            onOpenVisualApproval = viewModel::openVisualApproval,
                            onOpenScheduling = viewModel::openScheduling,
                            onOpenSecondChat = viewModel::openSecondChat,
                            onOpenConnectionPartnerProfile = viewModel::openConnectionPartnerProfile,
                            onDismissSecondChat = viewModel::dismissSecondChatFromHome,
                            onEditProfile = viewModel::openProfileManagement,
                            onSignOut = viewModel::signOut,
                            onDeleteAccount = viewModel::deleteAccount,
                        )
                    }
                }
            }

            is RealsRootUiState.FirstChat -> ChatScreen(
                currentUserId = current.session.user.id,
                match = current.match,
                chat = current.chat,
                messages = current.messages,
                optimisticMessages = current.optimisticMessages,
                exitRequests = current.exitRequests,
                loading = current.loading,
                sending = current.sending,
                actionLoading = current.actionLoading,
                actionLoadingLabel = current.actionLoadingLabel,
                error = current.error,
                message = current.message,
                onRefresh = { viewModel.refreshFirstChat(silent = true) },
                onSendMessage = viewModel::sendFirstChatMessage,
                onRetryOptimisticMessage = viewModel::retryFirstChatMessage,
                onApprove = { viewModel.submitFirstChatDecision(ChatContinueDecision.Approved) },
                onReject = { viewModel.submitFirstChatDecision(ChatContinueDecision.Rejected) },
                onRequestMutualExit = viewModel::requestMutualChatExit,
                onSafetyCancel = viewModel::safetyCancelChat,
                onAcceptExitRequest = viewModel::acceptChatExitRequest,
                onRejectExitRequest = viewModel::rejectChatExitRequest,
                onExitRequestTimeout = viewModel::timeoutChatExitRequest,
            )

            is RealsRootUiState.SecondChat -> ChatScreen(
                currentUserId = current.session.user.id,
                match = null,
                chat = current.chat,
                messages = current.messages,
                optimisticMessages = current.optimisticMessages,
                exitRequests = current.exitRequests,
                loading = current.loading,
                sending = current.sending,
                actionLoading = current.actionLoading,
                actionLoadingLabel = current.actionLoadingLabel,
                error = current.error,
                message = current.message,
                chatTitlePrefix = "Segundo chat",
                partnerNameFallback = current.partnerName,
                showDecisionActions = false,
                showExitActions = true,
                showMutualExitActions = false,
                allowAvailableChat = true,
                onBackHome = viewModel::closeSecondChat,
                onRefresh = { viewModel.refreshSecondChat(silent = true) },
                onSendMessage = viewModel::sendSecondChatMessage,
                onRetryOptimisticMessage = viewModel::retrySecondChatMessage,
                onApprove = {},
                onReject = {},
                onRequestMutualExit = {},
                onSafetyCancel = viewModel::safetyCancelSecondChat,
                onAcceptExitRequest = {},
                onRejectExitRequest = {},
                onExitRequestTimeout = {},
            )

            is RealsRootUiState.VisualApproval -> VisualApprovalScreen(
                matchId = current.matchId,
                match = current.match,
                profile = current.profile,
                partnerMessage = current.partnerMessage,
                partnerMessageLoaded = current.partnerMessageLoaded,
                myPersonalMessageSubmitted = current.myPersonalMessageSubmitted,
                loading = current.loading,
                refreshing = current.refreshing,
                writingMessage = current.writingMessage,
                deciding = current.deciding,
                decidingLabel = current.decidingLabel,
                error = current.error,
                message = current.message,
                onRefresh = viewModel::refreshVisualApproval,
                onSavePersonalMessage = viewModel::saveMyVisualPersonalMessage,
                onApprove = { viewModel.submitVisualDecision(VisualDecision.Approved) },
                onReject = { viewModel.submitVisualDecision(VisualDecision.Rejected) },
                onBackHome = viewModel::closeVisualApproval,
            )

            is RealsRootUiState.Scheduling -> SchedulingScreen(
                connectionId = current.connectionId,
                partnerName = current.partnerName,
                loading = current.loading,
                refreshing = current.refreshing,
                submitting = current.submitting,
                submittingLabel = current.submittingLabel,
                negotiation = current.negotiation,
                proposals = current.proposals,
                currentUserId = current.session.user.id,
                error = current.error,
                message = current.message,
                onRefresh = { viewModel.refreshScheduling(silent = true) },
                onSubmitProposals = viewModel::submitSchedulingProposals,
                onAcceptProposal = viewModel::acceptSchedulingProposal,
                onRejectRound = viewModel::rejectSchedulingRound,
                onOpenPartnerProfile = { viewModel.openConnectionPartnerProfile(current.matchId) },
                onBackHome = viewModel::closeScheduling,
            )

            is RealsRootUiState.PartnerProfile -> PartnerProfileScreen(
                profile = current.profile,
                loading = current.loading,
                refreshing = current.refreshing,
                error = current.error,
                onRefresh = viewModel::refreshPartnerProfile,
                onBackHome = viewModel::closePartnerProfile,
            )

            is RealsRootUiState.PendingEngagement -> FullScreenMessage(
                title = current.title,
                body = current.body,
                primaryActionLabel = "Volver a Home",
                onPrimaryAction = viewModel::returnToHomeFromPendingEngagement,
                secondaryActionLabel = "Cerrar sesion",
                onSecondaryAction = viewModel::signOut,
            )

            is RealsRootUiState.ActivationComplete -> ProfileActivationResultScreen(
                session = current.session,
                result = current.result,
                onContinueHome = viewModel::refreshSession,
                onSignOut = viewModel::signOut,
            )

            is RealsRootUiState.Failure -> ApiErrorScreen(
                error = current.error,
                context = ErrorContext.General,
                onRetry = viewModel::refreshSession,
                onDismiss = viewModel::signOut,
            )
        }
    }
}

@Composable
private fun NotificationPermissionGate(enabled: Boolean) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var requestedThisSession by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        requestedThisSession = true
    }

    LaunchedEffect(enabled, requestedThisSession) {
        if (requestedThisSession) return@LaunchedEffect
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) return@LaunchedEffect

        requestedThisSession = true
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
