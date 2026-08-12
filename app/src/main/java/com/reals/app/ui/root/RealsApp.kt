package com.reals.app.ui.root

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reals.app.core.network.ErrorContext
import com.reals.app.di.AppContainer
import com.reals.app.domain.model.ChatContinueDecision
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.VisualDecision
import com.reals.app.foreground.ForegroundDestinationLifecyclePublisher
import com.reals.app.ui.account.AccountDeletionRecoveryScreen
import com.reals.app.ui.auth.GoogleCredentialClient
import com.reals.app.ui.auth.LoginScreen
import com.reals.app.ui.chat.ChatScreen
import com.reals.app.ui.chat.PartnerProfileScreen
import com.reals.app.ui.chat.VisualApprovalScreen
import com.reals.app.ui.common.ApiErrorScreen
import com.reals.app.ui.common.FullScreenMessage
import com.reals.app.ui.common.formatBackendDate
import com.reals.app.ui.legal.LegalRequirementsScreen
import com.reals.app.ui.matchmaking.MatchmakingHomeScreen
import com.reals.app.ui.profile.AffinityQuestionnaireScreen
import com.reals.app.ui.profile.CreateProfileScreen
import com.reals.app.ui.profile.ProfileActivationResultScreen
import com.reals.app.ui.profile.ProfileQuestionScreen
import com.reals.app.ui.profile.ProfileStatusScreen
import com.reals.app.ui.scheduling.SchedulingScreen
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val googleCredentialClient = remember(context) { GoogleCredentialClient(context) }
    PublishForegroundDestination(
        appContainer = appContainer,
        state = state,
    )

    LaunchedEffect(appContainer.homeRefreshSignal) {
        appContainer.homeRefreshSignal.requests.collect {
            viewModel.pollHomeStateSilently()
        }
    }

    LaunchedEffect(notificationOpenNonce) {
        if (notificationOpenNonce != 0L) {
            viewModel.handleExternalNotificationOpened(notificationOpenType)
        }
    }

    NotificationPermissionGate(enabled = state is RealsRootUiState.Ready)

    BackHandler(enabled = state.canHandleSystemBack()) {
        viewModel.onSystemBack()
    }

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
                passwordResetLoading = current.passwordResetLoading,
                passwordResetMessage = current.passwordResetMessage,
                passwordResetAvailableAtMillis = current.passwordResetAvailableAtMillis,
                googleLoading = current.googleLoading,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
                onPasswordReset = viewModel::requestPasswordReset,
                onGoogleSignIn = {
                    val attemptId = viewModel.beginGoogleSignIn() ?: return@LoginScreen
                    coroutineScope.launch {
                        viewModel.completeGoogleSignIn(
                            attemptId = attemptId,
                            result = googleCredentialClient.getGoogleIdToken(context.findActivity()),
                        )
                    }
                },
            )

            is RealsRootUiState.LoadingSession -> FullScreenMessage(
                title = "Preparando tu cuenta",
                body = "Estamos cargando tu perfil${current.email?.let { " para $it" } ?: ""}.",
            )

            is RealsRootUiState.AccountDeletionScheduled -> FullScreenMessage(
                title = "Cuenta programada para eliminación",
                body = "Tu cuenta fue programada para eliminación. " +
                    "Podés recuperarla${
                        current.deletionFinalizesAt?.let { " hasta el ${formatBackendDate(it)}" }
                            ?: " durante 30 días"
                    }.",
                primaryActionLabel = "Entendido",
                onPrimaryAction = viewModel::signOut,
            )

            is RealsRootUiState.AccountDeletionPending -> AccountDeletionRecoveryScreen(
                user = current.user,
                reactivating = current.reactivating,
                finalizingDeletion = current.finalizingDeletion,
                error = current.error,
                onReactivate = viewModel::reactivateAccount,
                onKeepDeletion = viewModel::signOut,
                onFinalizeDeletion = viewModel::finalizeAccountDeletion,
            )

            is RealsRootUiState.LegalRequirements -> LegalRequirementsScreen(
                documents = current.documents,
                loading = current.loading,
                submittingDocumentType = current.submittingDocumentType,
                error = current.error,
                accountDeleteLoading = current.deletingAccount,
                accountDeleteError = current.accountDeleteError,
                onRecordRequiredAction = viewModel::recordLegalDocumentAction,
                onRetryLoad = viewModel::retryLegalRequirements,
                onDefer = viewModel::deferLegalRequirements,
                onSignOut = viewModel::signOut,
                onDeleteAccount = viewModel::deleteAccount,
            )

            is RealsRootUiState.Ready -> when (current.session.profileSnapshot) {
                ProfileSnapshot.Missing -> CreateProfileScreen(
                    loading = current.creatingProfile,
                    error = current.profileCreateError,
                    countriesLoading = current.countriesLoading,
                    countries = current.countries,
                    countriesError = current.countriesError,
                    accountDeleteLoading = current.deletingAccount,
                    accountDeleteError = current.accountDeleteError,
                    onSubmit = viewModel::createProfile,
                    onLoadCountries = viewModel::loadCountriesIfNeeded,
                    onRefresh = viewModel::refreshSession,
                    onSignOut = viewModel::signOut,
                    onDeleteAccount = viewModel::deleteAccount,
                )

                is ProfileSnapshot.Found -> {
                    val profile = (current.session.profileSnapshot).profile
                    val homeAvailable = current.shouldRenderHomeSurface()
                    if (current.shouldRenderAffinityQuestionnaireSurface()) {
                        AffinityQuestionnaireScreen(
                            state = current.affinityQuestionnaire,
                            onBack = viewModel::navigateBackAffinityQuestionnaire,
                            onRetry = viewModel::refreshAffinityQuestionnaire,
                            onStartContinue = viewModel::startAffinityQuestionnaireContinue,
                            onOpenCategories = viewModel::openAffinityQuestionnaireCategories,
                            onOpenReview = viewModel::openAffinityQuestionnaireReview,
                            onOpenCategory = viewModel::openAffinityQuestionnaireCategory,
                            onOpenReviewedAnswer = viewModel::openAffinityQuestionnaireReviewedAnswer,
                            onSkipQuestion = viewModel::skipAffinityQuestion,
                            onNextQuestion = viewModel::nextAffinityQuestion,
                            onSelectAnswer = viewModel::selectAffinityAnswer,
                            onDeleteAnswer = viewModel::deleteAffinityAnswer,
                        )
                    } else if (current.profileQuestions.open) {
                        ProfileQuestionScreen(
                            state = current.profileQuestions,
                            onBack = viewModel::navigateBackProfileQuestions,
                            onRetry = viewModel::refreshProfileQuestions,
                            onOpenQuestions = viewModel::openProfileQuestionList,
                            onOpenEditor = viewModel::openProfileQuestionEditor,
                            onOpenSelection = viewModel::openProfileQuestionSelection,
                            onSelectionDraftChange = viewModel::updateProfileQuestionSelectionDraft,
                            onSaveSelection = viewModel::saveProfileQuestionSelection,
                            onSaveAnswer = viewModel::saveProfileQuestionAnswer,
                            onDeleteAnswer = viewModel::deleteProfileQuestionAnswer,
                        )
                    } else if (current.editingActiveProfile || !homeAvailable) {
                        ProfileStatusScreen(
                            session = current.session,
                            profileUpdateLoading = current.updatingProfile,
                            profileUpdateError = current.profileUpdateError,
                            profileUpdateMessage = current.profileUpdateMessage,
                            countriesLoading = current.countriesLoading,
                            countries = current.countries,
                            countriesError = current.countriesError,
                            matchFiltersLoading = current.updatingMatchFilters,
                            matchFiltersError = current.matchFiltersError,
                            matchFiltersMessage = current.matchFiltersMessage,
                            photosLoading = current.loadingPhotos,
                            photos = current.profilePhotos,
                            photosError = current.profilePhotosError,
                            photoActionLoading = current.addingPhoto,
                            photoActionError = current.photoActionError,
                            photoActionMessage = current.photoActionMessage,
                            photoReorderLoading = current.reorderingPhotos,
                            photoReorderError = current.photoReorderError,
                            photoReorderMessage = current.photoReorderMessage,
                            activationLoading = current.activatingProfile,
                            activationError = current.profileActivationError,
                            emailVerificationSending = current.sendingEmailVerification,
                            emailVerificationChecking = current.checkingEmailVerification,
                            emailVerificationMessage = current.emailVerificationMessage,
                            emailVerificationError = current.emailVerificationError,
                            emailVerificationRequired = current.emailVerificationRequired,
                            emailVerificationLocallyVerified = current.emailVerificationLocallyVerified,
                            resendEmailVerificationAvailableAtMillis =
                                current.resendEmailVerificationAvailableAtMillis,
                            checkEmailVerificationAvailableAtMillis =
                                current.checkEmailVerificationAvailableAtMillis,
                            showDraftAfterEditNotice = current.editingActiveProfile &&
                                profile.status != ProfileStatus.Active,
                            onUpdateProfile = viewModel::updateProfile,
                            onLoadCountries = viewModel::loadCountriesIfNeeded,
                            onUpdateMatchFilters = viewModel::updateMatchFilters,
                            onLoadPhotos = viewModel::loadProfilePhotos,
                            onAddPhotoFile = viewModel::addProfilePhotoFile,
                            onReplacePhotoFile = viewModel::replaceProfilePhotoFile,
                            onDeletePhoto = { photoId, position -> viewModel.deleteProfilePhoto(photoId, position) },
                            onMovePhoto = viewModel::moveProfilePhoto,
                            onActivateProfile = { viewModel.activateProfile() },
                            onResendEmailVerification = viewModel::resendEmailVerification,
                            onCheckEmailVerification = viewModel::checkEmailVerification,
                            onOpenAffinityQuestions = viewModel::openAffinityQuestionnaire,
                            onOpenProfileQuestions = viewModel::openProfileQuestions,
                            onRefresh = viewModel::refreshSession,
                            onSignOut = viewModel::signOut,
                            accountDeleteLoading = current.deletingAccount,
                            accountDeleteError = current.accountDeleteError,
                            homeLoading = current.homeLoading,
                            homeError = current.homeError,
                            onDeleteAccount = viewModel::deleteAccount,
                            onRetryHome = if (current.editingActiveProfile) {
                                viewModel::closeProfileManagement
                            } else {
                                viewModel::refreshSession
                            },
                            onBackHome = if (current.editingActiveProfile) {
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
                            changePasswordLoading = current.changingPassword,
                            changePasswordError = current.changePasswordError,
                            changePasswordMessage = current.changePasswordMessage,
                            canChangePassword = current.session.user.passwordManagementAllowed,
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
                            onChangePassword = viewModel::changePassword,
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
                serverClockSnapshot = current.serverClockSnapshot,
                dismissedUnansweredPeriodReference = current.dismissedUnansweredPeriodReference,
                loading = current.loading,
                refreshing = current.refreshing,
                sending = current.sending,
                audioUpload = current.audioUpload,
                audioDraft = current.audioDraft,
                actionLoading = current.actionLoading,
                actionLoadingLabel = current.actionLoadingLabel,
                guidance = current.chat?.guidance,
                guidanceActionLoading = current.guidanceActionLoading,
                manualBlockLoading = current.manualBlock.loading,
                manualBlockError = current.manualBlock.error,
                error = current.error,
                message = current.message,
                onRefresh = { viewModel.refreshFirstChat(silent = true) },
                onFirstChatLocalExpiry = viewModel::handleFirstChatLocalExpiry,
                onRequestNextGuidanceQuestion = viewModel::requestNextFirstChatGuidanceQuestion,
                onSendMessage = viewModel::sendFirstChatMessage,
                onSendAudioMessage = viewModel::sendFirstChatAudioMessage,
                onClearAudioUploadState = viewModel::clearFirstChatAudioUploadState,
                onAudioDraftReady = viewModel::setFirstChatAudioDraft,
                onAudioDraftReadyAndSend = viewModel::setAndSendFirstChatAudioDraft,
                onDeleteAudioDraft = viewModel::deleteFirstChatAudioDraft,
                onRefreshAudioUrl = viewModel::refreshFirstChatAudioUrl,
                onRetryOptimisticMessage = viewModel::retryFirstChatMessage,
                onApprove = { viewModel.submitFirstChatDecision(ChatContinueDecision.Approved) },
                onReject = { viewModel.submitFirstChatDecision(ChatContinueDecision.Rejected) },
                onRequestMutualExit = viewModel::requestMutualChatExit,
                onDismissFirstChatUnansweredSuggestion = viewModel::dismissFirstChatUnansweredSuggestion,
                onSafetyCancel = viewModel::safetyCancelChat,
                onManualBlock = viewModel::blockCurrentMatchParticipant,
                onClearManualBlockError = viewModel::clearManualBlockError,
                onAcceptExitRequest = viewModel::acceptChatExitRequest,
                onRejectExitRequest = viewModel::rejectChatExitRequest,
                onExitRequestTimeout = viewModel::timeoutChatExitRequest,
                onBackHome = if (current.canRecoverFirstChatToHome()) {
                    viewModel::closeFirstChat
                } else {
                    null
                },
            )

            is RealsRootUiState.SecondChat -> ChatScreen(
                currentUserId = current.session.user.id,
                match = null,
                chat = current.chat,
                messages = current.messages,
                optimisticMessages = current.optimisticMessages,
                exitRequests = current.exitRequests,
                loading = current.loading,
                refreshing = current.refreshing,
                sending = current.sending,
                audioUpload = current.audioUpload,
                audioDraft = current.audioDraft,
                actionLoading = current.actionLoading,
                actionLoadingLabel = current.actionLoadingLabel,
                secondChatLifecycle = current.lifecycle,
                manualBlockLoading = current.manualBlock.loading,
                manualBlockError = current.manualBlock.error,
                error = current.error,
                message = current.message,
                chatTitlePrefix = "Segundo chat",
                loadingChatTitle = "Segundo chat",
                partnerNameFallback = current.partnerName,
                showDecisionActions = false,
                showExitActions = true,
                showMutualExitActions = false,
                allowAvailableChat = true,
                onBackHome = if (current.isJoinedActiveSecondChat()) null else viewModel::closeSecondChat,
                onRefresh = { viewModel.refreshSecondChat(silent = true) },
                onSecondChatUnavailable = viewModel::handleSecondChatUnavailable,
                onSecondChatLocalAbsoluteExpiry = viewModel::handleSecondChatLocalAbsoluteExpiry,
                onClaimSecondChatNoShow = viewModel::createSecondChatNoShowClaim,
                onRequestSecondChatCompletion = viewModel::requestSecondChatCompletion,
                onDecideSecondChatCompletion = viewModel::decideSecondChatCompletion,
                onClaimSecondChatInactivity = viewModel::claimSecondChatInactivity,
                onSendMessage = viewModel::sendSecondChatMessage,
                onSendAudioMessage = viewModel::sendSecondChatAudioMessage,
                onClearAudioUploadState = viewModel::clearSecondChatAudioUploadState,
                onAudioDraftReady = viewModel::setSecondChatAudioDraft,
                onAudioDraftReadyAndSend = viewModel::setAndSendSecondChatAudioDraft,
                onDeleteAudioDraft = viewModel::deleteSecondChatAudioDraft,
                onRefreshAudioUrl = viewModel::refreshSecondChatAudioUrl,
                onRetryOptimisticMessage = viewModel::retrySecondChatMessage,
                onApprove = {},
                onReject = {},
                onRequestMutualExit = {},
                onSafetyCancel = viewModel::safetyCancelSecondChat,
                onManualBlock = viewModel::blockCurrentMatchParticipant,
                onClearManualBlockError = viewModel::clearManualBlockError,
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
                readingPartnerMessage = current.readingPartnerMessage,
                partnerMessageError = current.partnerMessageError,
                myPersonalMessageSubmitted = current.myPersonalMessageSubmitted,
                loading = current.loading,
                refreshing = current.refreshing,
                writingMessage = current.writingMessage,
                deciding = current.deciding,
                decidingLabel = current.decidingLabel,
                manualBlockLoading = current.manualBlock.loading,
                manualBlockError = current.manualBlock.error,
                error = current.error,
                message = current.message,
                onRefresh = viewModel::refreshVisualApproval,
                onReadPartnerMessage = viewModel::readPartnerPersonalMessage,
                onSavePersonalMessage = viewModel::saveMyVisualPersonalMessage,
                onApprove = { viewModel.submitVisualDecision(VisualDecision.Approved) },
                onReject = { viewModel.submitVisualDecision(VisualDecision.Rejected) },
                onManualBlock = viewModel::blockCurrentMatchParticipant,
                onClearManualBlockError = viewModel::clearManualBlockError,
                onBackHome = viewModel::closeVisualApproval,
            )

            is RealsRootUiState.Scheduling -> SchedulingScreen(
                connectionId = current.connectionId,
                partnerName = current.partnerName,
                loading = current.loading,
                refreshing = current.refreshing,
                submitting = current.submitting,
                submittingLabel = current.submittingLabel,
                manualBlockLoading = current.manualBlock.loading,
                manualBlockError = current.manualBlock.error,
                negotiation = current.negotiation,
                proposals = current.proposals,
                availability = current.availability,
                currentUserId = current.session.user.id,
                error = current.error,
                message = current.message,
                onRefresh = { viewModel.refreshScheduling(silent = true) },
                onSubmitProposals = viewModel::submitSchedulingProposals,
                onAcceptProposal = viewModel::acceptSchedulingProposal,
                onRejectPartnerProposals = viewModel::rejectSchedulingPartnerProposals,
                onOpenPartnerProfile = { viewModel.openConnectionPartnerProfile(current.matchId) },
                onManualBlock = viewModel::blockCurrentMatchParticipant,
                onClearManualBlockError = viewModel::clearManualBlockError,
                onBackHome = viewModel::closeScheduling,
            )

            is RealsRootUiState.PartnerProfile -> PartnerProfileScreen(
                profile = current.profile,
                partnerMessage = current.partnerMessage,
                partnerMessageLoaded = current.partnerMessageLoaded,
                loadingPartnerMessage = current.loadingPartnerMessage,
                partnerMessageError = current.partnerMessageError,
                loading = current.loading,
                refreshing = current.refreshing,
                manualBlockLoading = current.manualBlock.loading,
                manualBlockError = current.manualBlock.error,
                error = current.error,
                onRefresh = viewModel::refreshPartnerProfile,
                onRetryPartnerMessage = viewModel::retryPartnerProfileMessage,
                onManualBlock = viewModel::blockCurrentMatchParticipant,
                onClearManualBlockError = viewModel::clearManualBlockError,
                onBackHome = viewModel::closePartnerProfile,
            )

            is RealsRootUiState.PendingEngagement -> FullScreenMessage(
                title = current.title,
                body = current.body,
                primaryActionLabel = "Volver a Home",
                onPrimaryAction = viewModel::returnToHomeFromPendingEngagement,
                secondaryActionLabel = "Cerrar sesión",
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
private fun PublishForegroundDestination(
    appContainer: AppContainer,
    state: RealsRootUiState,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val registration = remember(appContainer) {
        appContainer.foregroundDestinationTracker.register()
    }
    val publisher = remember(registration) {
        ForegroundDestinationLifecyclePublisher(registration)
    }

    LaunchedEffect(state) {
        publisher.onDestinationChanged(state.foregroundDestination())
    }

    DisposableEffect(lifecycleOwner, publisher) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> publisher.onResume()
                Lifecycle.Event.ON_PAUSE -> publisher.onPause()
                Lifecycle.Event.ON_STOP -> publisher.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            publisher.onDispose()
        }
    }
}

internal fun RealsRootUiState.Ready.shouldRenderHomeSurface(): Boolean {
    val profile = (session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return false
    return when (profile.status) {
        ProfileStatus.Active -> true
        ProfileStatus.Draft -> home.hasRenderableDraftHomeSurface()
        else -> false
    }
}

private fun HomeUiState.hasRenderableDraftHomeSurface(): Boolean {
    if (homeLoading && allowDraftHomeWithoutInteractions) return true
    return homeState?.profileStatus == ProfileStatus.Draft &&
        (
            homeState?.canRemainInHomeForProfileStatus() == true ||
                allowDraftHomeWithoutInteractions
            )
}

internal fun RealsRootUiState.Ready.shouldRenderAffinityQuestionnaireSurface(): Boolean =
    affinityQuestionnaire.open && session.profileSnapshot is ProfileSnapshot.Found

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

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
