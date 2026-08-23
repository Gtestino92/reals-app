package com.reals.app.ui.matchmaking

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.reals.app.BuildConfig
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.NotificationPreferenceGroup
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.ui.account.NotificationSettingsScreen
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.RealsArchitecturalLines
import com.reals.app.ui.common.RealsBrandSeal
import com.reals.app.ui.common.RealsScreenHeader
import com.reals.app.ui.theme.RealsColors
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import com.reals.app.ui.root.AffinityHomeSummaryUiState
import com.reals.app.ui.root.HomeSurface
import com.reals.app.ui.root.MatchmakingSearchUiPhase
import com.reals.app.ui.root.NotificationPreferencesUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val showManualLocationFallback = BuildConfig.SHOW_MANUAL_LOCATION_FALLBACK
private val showCafecitoSupport = BuildConfig.SHOW_CAFECITO_SUPPORT

private enum class LocationPermissionRequestMode {
    None,
    AutoPrewarm,
    Search,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchmakingHomeScreen(
    profile: Profile,
    screenModel: HomeScreenModel?,
    homeLoading: Boolean,
    homeError: ApiError?,
    homeMessage: String?,
    matchmakingSearchPhase: MatchmakingSearchUiPhase,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    changePasswordLoading: Boolean,
    changePasswordError: String?,
    changePasswordMessage: String?,
    canChangePassword: Boolean,
    homeSurface: HomeSurface,
    notificationPreferences: NotificationPreferencesUiState,
    onHomeSurfaceChange: (HomeSurface) -> Unit,
    onEnqueue: (SearchLocationInput) -> Unit,
    onDeviceLocationResolved: (SearchLocationInput) -> Unit,
    onCancelSearch: () -> Unit,
    onBeginLocationResolution: () -> Unit,
    onFailSearchPreparation: () -> Unit,
    onRefreshHome: () -> Unit,
    onPollHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenConnectionPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
    affinityHomeSummary: AffinityHomeSummaryUiState,
    onLoadAffinitySummary: () -> Unit,
    onOpenAffinityQuestions: () -> Unit,
    onEditProfile: () -> Unit,
    onEditSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRetryNotifications: () -> Unit,
    onCloseNotifications: () -> Unit,
    onNotificationPreferenceChange: (NotificationPreferenceGroup, Boolean) -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onSupportReals: () -> Unit,
) {
    if (screenModel == null && homeLoading) {
        LoadingHomeStateScreen()
        return
    }

    val context = LocalContext.current
    val searchScope = rememberCoroutineScope()
    val model = screenModel ?: emptyHomeScreenModel()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var manualExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var locationAttemptId by rememberSaveable(profile.id) { mutableLongStateOf(0L) }
    var pendingPermissionAttemptId by rememberSaveable(profile.id) { mutableLongStateOf(0L) }
    var pendingPermissionRequestMode by rememberSaveable(profile.id) {
        mutableStateOf(LocationPermissionRequestMode.None)
    }
    var autoLocationPermissionRequested by rememberSaveable(profile.id) { mutableStateOf(false) }
    var prewarmedProfileId by rememberSaveable { mutableStateOf<String?>(null) }

    fun prewarmLocationSilently() {
        searchScope.launch {
            DeviceSearchLocationResolver.prewarmIfPermitted(context)
        }
    }

    fun locationPermissionDeniedMessage(): String {
        val activity = context.findActivity()
        val permanentlyDenied = activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )

        if (permanentlyDenied) {
            return "Necesitamos tu ubicación para buscar chats cerca. " +
                "Habilitá el permiso de ubicación desde los ajustes de la app."
        }

        return "Necesitamos tu ubicación para buscar chats cerca."
    }

    fun enqueueWithDeviceLocation(attemptId: Long) {
        searchScope.launch {
            localError = null
            val result = runCatching {
                DeviceSearchLocationResolver.resolveForSearch(context)
            }
            if (attemptId != locationAttemptId) return@launch
            result
                .onSuccess { location ->
                    if (attemptId != locationAttemptId) return@launch
                    onDeviceLocationResolved(location)
                }
                .onFailure {
                    if (attemptId != locationAttemptId) return@launch
                    onFailSearchPreparation()
                    localError = it.message
                        ?: SEARCH_LOCATION_UNAVAILABLE_MESSAGE
                    manualExpanded = showManualLocationFallback
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val requestMode = pendingPermissionRequestMode
        val attemptId = pendingPermissionAttemptId
        pendingPermissionAttemptId = 0L
        pendingPermissionRequestMode = LocationPermissionRequestMode.None

        if (grants.values.any { it }) {
            localError = null
            when (requestMode) {
                LocationPermissionRequestMode.AutoPrewarm -> prewarmLocationSilently()
                LocationPermissionRequestMode.Search -> {
                    if (attemptId == 0L || attemptId != locationAttemptId) {
                        return@rememberLauncherForActivityResult
                    }
                    enqueueWithDeviceLocation(attemptId)
                }
                LocationPermissionRequestMode.None -> Unit
            }
        } else {
            localError = locationPermissionDeniedMessage()
            if (requestMode == LocationPermissionRequestMode.Search) {
                onFailSearchPreparation()
                manualExpanded = showManualLocationFallback
            }
        }
    }

    when {
        matchmakingSearchPhase == MatchmakingSearchUiPhase.ResolvingLocation ||
            matchmakingSearchPhase == MatchmakingSearchUiPhase.JoiningQueue -> {
            SearchingChatScreen(
                body = SEARCHING_CHAT_BODY,
                canCancelSearch = true,
                homeError = null,
                accountDeleteLoading = accountDeleteLoading,
                onPollHome = {},
                onLeaveQueue = {
                    locationAttemptId += 1
                    pendingPermissionAttemptId = 0L
                    onCancelSearch()
                },
            )
            return
        }

        matchmakingSearchPhase == MatchmakingSearchUiPhase.Searching ||
            model.matchmaking.inQueue -> {
            SearchingChatScreen(
                canCancelSearch = true,
                homeError = homeError,
                accountDeleteLoading = accountDeleteLoading,
                onPollHome = onPollHome,
                onLeaveQueue = {
                    locationAttemptId += 1
                    pendingPermissionAttemptId = 0L
                    onCancelSearch()
                },
            )
            return
        }
    }

    if (model.shouldPollHome()) {
        LaunchedEffect(
            model.matchmaking.inQueue,
            model.pendingActions.size,
            model.nextSteps.size,
            model.passiveNotices.size,
            model.activeInteractionsSummary?.activeInitialCount,
            model.activeInteractionsSummary?.activeConnectionCount,
            model.activeInteractionsSummary?.hasPendingSchedulingConnection,
            model.activeInteractionsSummary?.actionableConnectionCount,
        ) {
            while (true) {
                delay(HOME_POLL_INTERVAL_MILLIS.milliseconds)
                nowMillis = System.currentTimeMillis()
                onPollHome()
            }
        }
    }

    if (model.shouldPollSecondChatAvailability(nowMillis)) {
        LaunchedEffect(model.nextSteps) {
            while (true) {
                delay(model.nextSecondChatPollDelayMillis(System.currentTimeMillis()).milliseconds)
                val currentNowMillis = System.currentTimeMillis()
                nowMillis = currentNowMillis
                onPollHome()
                if (!model.shouldPollSecondChatAvailability(currentNowMillis)) break
            }
        }
    }

    val canPrewarmSearchLocation = screenModel != null &&
        matchmakingSearchPhase == MatchmakingSearchUiPhase.Idle &&
        !homeLoading &&
        profile.status == ProfileStatus.Active &&
        model.matchmaking.canSearch &&
        !model.matchmaking.inQueue &&
        hasLocationPermission(context)

    val shouldAutoRequestLocationPermission = shouldAutoRequestHomeLocationPermission(
        profileStatus = profile.status,
        screenModel = screenModel,
        homeLoading = homeLoading,
        matchmakingSearchPhase = matchmakingSearchPhase,
        hasLocationPermission = hasLocationPermission(context),
        autoLocationPermissionRequested = autoLocationPermissionRequested,
    )

    LaunchedEffect(profile.id, shouldAutoRequestLocationPermission) {
        if (!shouldAutoRequestLocationPermission) return@LaunchedEffect
        autoLocationPermissionRequested = true
        pendingPermissionRequestMode = LocationPermissionRequestMode.AutoPrewarm
        pendingPermissionAttemptId = 0L
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    LaunchedEffect(profile.id, canPrewarmSearchLocation) {
        if (!canPrewarmSearchLocation || prewarmedProfileId == profile.id) return@LaunchedEffect
        prewarmedProfileId = profile.id
        prewarmLocationSilently()
    }

    LaunchedEffect(profile.id) {
        onLoadAffinitySummary()
    }

    PullToRefreshBox(
        isRefreshing = homeLoading,
        onRefresh = onRefreshHome,
    ) {
        MatchmakingIdleScreen(
            profile = profile,
            screenModel = model,
            homeLoading = homeLoading,
            homeError = homeError,
            homeMessage = homeMessage,
            nowMillis = nowMillis,
            accountDeleteLoading = accountDeleteLoading,
            accountDeleteError = accountDeleteError,
            changePasswordLoading = changePasswordLoading,
            changePasswordError = changePasswordError,
            changePasswordMessage = changePasswordMessage,
            canChangePassword = canChangePassword,
            localError = localError,
            manualExpanded = manualExpanded,
            showManualLocationFallback = showManualLocationFallback,
            onEnqueue = onEnqueue,
            onLocalErrorChange = { localError = it },
            onManualExpandedChange = { manualExpanded = it },
            onSearchWithDeviceLocation = {
                localError = null
                locationAttemptId += 1
                val attemptId = locationAttemptId
                onBeginLocationResolution()
                if (hasLocationPermission(context)) {
                    enqueueWithDeviceLocation(attemptId)
                } else {
                    pendingPermissionRequestMode = LocationPermissionRequestMode.Search
                    pendingPermissionAttemptId = attemptId
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            },
            onRefreshHome = onRefreshHome,
            onOpenFirstChat = onOpenFirstChat,
            onOpenVisualApproval = onOpenVisualApproval,
            onOpenScheduling = onOpenScheduling,
            onOpenSecondChat = onOpenSecondChat,
            onOpenConnectionPartnerProfile = onOpenConnectionPartnerProfile,
            onDismissSecondChat = onDismissSecondChat,
            affinityHomeSummary = affinityHomeSummary,
            onOpenAffinityQuestions = onOpenAffinityQuestions,
            onEditProfile = onEditProfile,
            onEditSearch = onEditSearch,
            onOpenNotifications = onOpenNotifications,
            notificationPreferences = notificationPreferences,
            onRetryNotifications = onRetryNotifications,
            onCloseNotifications = onCloseNotifications,
            onNotificationPreferenceChange = onNotificationPreferenceChange,
            onSignOut = onSignOut,
            onChangePassword = onChangePassword,
            onDeleteAccount = onDeleteAccount,
            onSupportReals = onSupportReals,
            homeSurface = homeSurface,
            onOpenPendingSurface = { onHomeSurfaceChange(HomeSurface.Pending) },
            onBackHome = { onHomeSurfaceChange(HomeSurface.Overview) },
        )
    }
}

internal fun shouldAutoRequestHomeLocationPermission(
    profileStatus: ProfileStatus,
    screenModel: HomeScreenModel?,
    homeLoading: Boolean,
    matchmakingSearchPhase: MatchmakingSearchUiPhase,
    hasLocationPermission: Boolean,
    autoLocationPermissionRequested: Boolean,
): Boolean {
    val matchmaking = screenModel?.matchmaking ?: return false
    return profileStatus == ProfileStatus.Active &&
        !homeLoading &&
        matchmakingSearchPhase == MatchmakingSearchUiPhase.Idle &&
        matchmaking.canSearch &&
        !matchmaking.inQueue &&
        matchmaking.blockedReason == null &&
        !hasLocationPermission &&
        !autoLocationPermissionRequested
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun MatchmakingIdleScreen(
    profile: Profile,
    screenModel: HomeScreenModel,
    homeLoading: Boolean,
    homeError: ApiError?,
    homeMessage: String?,
    nowMillis: Long,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    changePasswordLoading: Boolean,
    changePasswordError: String?,
    changePasswordMessage: String?,
    canChangePassword: Boolean,
    localError: String?,
    manualExpanded: Boolean,
    showManualLocationFallback: Boolean,
    onEnqueue: (SearchLocationInput) -> Unit,
    onLocalErrorChange: (String?) -> Unit,
    onManualExpandedChange: (Boolean) -> Unit,
    onSearchWithDeviceLocation: () -> Unit,
    onRefreshHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenConnectionPartnerProfile: (matchId: String) -> Unit,
    onDismissSecondChat: (connectionId: String) -> Unit,
    affinityHomeSummary: AffinityHomeSummaryUiState,
    onOpenAffinityQuestions: () -> Unit,
    onEditProfile: () -> Unit,
    onEditSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    notificationPreferences: NotificationPreferencesUiState,
    onRetryNotifications: () -> Unit,
    onCloseNotifications: () -> Unit,
    onNotificationPreferenceChange: (NotificationPreferenceGroup, Boolean) -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onSupportReals: () -> Unit,
    homeSurface: HomeSurface,
    onOpenPendingSurface: () -> Unit,
    onBackHome: () -> Unit,
) {
    var latitude by rememberSaveable(profile.id) { mutableStateOf("-34.6037") }
    var longitude by rememberSaveable(profile.id) { mutableStateOf("-58.3816") }
    var accuracy by rememberSaveable(profile.id) { mutableStateOf("50") }
    var accountExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val busy = homeLoading || accountDeleteLoading || changePasswordLoading
    val canSearch = screenModel.matchmaking.canSearch
    val blockedReason = screenModel.matchmaking.blockedReason
    val pendingPresentation = homePendingPresentation(screenModel, nowMillis)
    val matchmakingUnavailable = matchmakingUnavailablePresentation(screenModel.matchmaking, nowMillis)
    val visualAdvancementWait = matchmakingUnavailable
        ?.takeIf { it.kind == MatchmakingUnavailableKind.VisualAdvancementWait }
    var refreshedVisualAdvancementAt by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    if (notificationPreferences.open) {
        NotificationSettingsScreen(
            loading = notificationPreferences.loading,
            preferences = notificationPreferences.preferences,
            saving = notificationPreferences.saving,
            loadError = notificationPreferences.loadError,
            saveError = notificationPreferences.saveError,
            onRetry = onRetryNotifications,
            onBack = onCloseNotifications,
            onPreferenceChange = onNotificationPreferenceChange,
        )
        return
    }

    if (homeSurface == HomeSurface.Pending) {
        PendingInteractionsScreen(
            presentation = pendingPresentation,
            busy = busy,
            nowMillis = nowMillis,
            onBackHome = onBackHome,
            onOpenVisualApproval = onOpenVisualApproval,
            onOpenScheduling = onOpenScheduling,
            onOpenSecondChat = onOpenSecondChat,
            onOpenPartnerProfile = onOpenConnectionPartnerProfile,
            onDismissSecondChat = onDismissSecondChat,
        )
        return
    }

    LaunchedEffect(accountExpanded) {
        if (!accountExpanded) return@LaunchedEffect
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(visualAdvancementWait?.nextAvailableAt, nowMillis) {
        if (
            shouldRequestVisualAdvancementReconciliation(
                presentation = visualAdvancementWait,
                nowMillis = nowMillis,
                refreshedNextAvailableAt = refreshedVisualAdvancementAt,
            )
        ) {
            refreshedVisualAdvancementAt = visualAdvancementWait?.nextAvailableAt
            onRefreshHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        RealsScreenHeader(
            title = "Inicio",
            subtitle = if (screenModel.draftProfileWarning == null) {
                "${profile.displayName}, tu perfil está activo."
            } else {
                "${profile.displayName}, Inicio sigue disponible."
            },
            showSeal = true,
            centered = true,
        )
        Spacer(modifier = Modifier.height(24.dp))
        screenModel.draftProfileWarning?.let { warning ->
            FeedbackCard(
                title = warning.title,
                message = warning.message,
                tone = FeedbackTone.Warning,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEditProfile,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(warning.actionLabel)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        HomePriorityBlock(
            presentation = pendingPresentation,
            busy = busy,
            nowMillis = nowMillis,
            onOpenPending = onOpenPendingSurface,
            onOpenVisualApproval = onOpenVisualApproval,
            onOpenSecondChat = onOpenSecondChat,
        )
        HomeFirstChatsBlock(
            firstChats = pendingPresentation.firstChats,
            busy = busy,
            onOpenFirstChat = onOpenFirstChat,
        )
        HomePendingSummaryCard(
            presentation = pendingPresentation,
            busy = busy,
            onOpenPending = onOpenPendingSurface,
        )
        if (matchmakingUnavailable != null) {
            MatchmakingUnavailableCard(matchmakingUnavailable)
        } else {
            Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RealsRadii.Hero),
            border = BorderStroke(1.dp, RealsColors.SoftGold.copy(alpha = 0.42f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                RealsArchitecturalLines(
                    modifier = Modifier.matchParentSize(),
                    lightOnInk = true,
                )
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RealsBrandSeal(modifier = Modifier.size(38.dp))
                        Text(
                            "Buscar chat",
                            style = RealsType.SectionTitle,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    if (screenModel.shouldShowMatchmakingLocationCopy()) {
                        Text(
                            text = "Vamos a usar tu ubicación actual para encontrar personas compatibles cerca.",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                        )
                    }
                    ActiveInteractionsSummary(
                        summary = screenModel.activeInteractionsSummary,
                        passiveNotices = screenModel.passiveNotices,
                        textColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    )
                    localError?.let { ErrorFeedback("No pudimos usar tu ubicación", it) }
                    homeError?.let { ApiErrorFeedbackCard(it, ErrorContext.Home) }
                    homeMessage?.let { SuccessFeedback(it) }
                    if (!canSearch && blockedReason != null) {
                        Text(
                            text = blockedReason.matchmakingBlockedMessage()
                                ?: "No pudimos iniciar la búsqueda. Revisá tu perfil e intentá nuevamente.",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                        )
                    }
                    Button(
                        onClick = onSearchWithDeviceLocation,
                        enabled = !busy && canSearch,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(if (homeLoading) "Preparando búsqueda..." else "Buscar chat")
                    }
                    if (showManualLocationFallback) {
                        OutlinedButton(
                            onClick = { onManualExpandedChange(!manualExpanded) },
                            enabled = !busy && canSearch,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (manualExpanded) "Ocultar fallback manual" else "Fallback manual dev")
                        }
                    }
                    if (showManualLocationFallback && manualExpanded) {
                        ManualLocationFallback(
                            latitude = latitude,
                            longitude = longitude,
                            accuracy = accuracy,
                            enabled = !busy && canSearch,
                            onLatitudeChange = { latitude = signedDecimalInput(it) },
                            onLongitudeChange = { longitude = signedDecimalInput(it) },
                            onAccuracyChange = { accuracy = it.filter { char -> char.isDigit() } },
                            onSubmit = {
                                val location = validateLocation(latitude, longitude, accuracy)
                                if (location == null) {
                                    onLocalErrorChange(
                                        "Ubicación inválida. Latitud -90..90, " +
                                            "longitud -180..180, precision 0..100000."
                                    )
                                } else {
                                    onLocalErrorChange(null)
                                    onEnqueue(location)
                                }
                            },
                        )
                    }
                    if (homeError != null) {
                        OutlinedButton(onClick = onRefreshHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (homeLoading) "Actualizando..." else "Reintentar estado")
                        }
                    }
                }
            }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HomeAffinityCard(
            summary = affinityHomeSummary,
            busy = busy,
            onOpenAffinityQuestions = onOpenAffinityQuestions,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HomeManagementEntryCard(
            title = "Tu perfil",
            body = "Gestioná cómo te presentás: datos, bio, fotos y preguntas públicas.",
            actionLabel = "Editar perfil",
            enabled = !busy,
            onClick = onEditProfile,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HomeManagementEntryCard(
            title = "Preferencias",
            body = "Definí qué personas querés que Reals tenga en cuenta al buscar un chat.",
            actionLabel = "Editar preferencias",
            enabled = !busy,
            onClick = onEditSearch,
        )
        Spacer(modifier = Modifier.height(24.dp))
        AccountSection(
            busy = busy,
            accountDeleteLoading = accountDeleteLoading,
            accountDeleteError = accountDeleteError,
            changePasswordLoading = changePasswordLoading,
            changePasswordError = changePasswordError,
            changePasswordMessage = changePasswordMessage,
            canChangePassword = canChangePassword,
            showSupportReals = shouldShowSupportReals(showCafecitoSupport),
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = it },
            onSignOut = onSignOut,
            onOpenNotifications = onOpenNotifications,
            onChangePassword = onChangePassword,
            onDeleteAccount = onDeleteAccount,
            onSupportReals = onSupportReals,
        )
    }
}

@Composable
private fun MatchmakingUnavailableCard(
    presentation: MatchmakingUnavailablePresentation,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Hero),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = presentation.title,
                style = RealsType.SectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = presentation.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            presentation.supportingText?.let { text ->
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ActiveInteractionsSummary(
    summary: HomeActiveInteractionsSummary?,
    passiveNotices: List<HomePassiveNoticeItem>,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (summary == null) return

    activeExperiencesSummaryText(summary)?.let { text ->
        Text(
            text = text,
            color = textColor,
        )
    }

    passiveNotices.forEach { notice ->
        passiveNoticeText(notice)?.let { text ->
            Text(
                text = text,
                color = textColor,
            )
        }
    }
}
