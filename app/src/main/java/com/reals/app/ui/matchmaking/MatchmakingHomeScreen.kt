package com.reals.app.ui.matchmaking

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.HomeActiveInteractionsSummary
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.MatchmakingSearchUiPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private const val LOCATION_RESOLUTION_TIMEOUT_MILLIS = 20_000L

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
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
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

    fun enqueueWithDeviceLocation(attemptId: Long) {
        searchScope.launch {
            localError = null
            val result = runCatching {
                withTimeoutOrNull(LOCATION_RESOLUTION_TIMEOUT_MILLIS.milliseconds) {
                    currentSearchLocation(context)
                } ?: error(
                    "No hay ubicacion disponible todavia. Verifica que la ubicacion del telefono " +
                        "este activada e intenta nuevamente."
                )
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
                        ?: "No se pudo obtener la ubicacion del dispositivo."
                    manualExpanded = true
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val attemptId = pendingPermissionAttemptId
        pendingPermissionAttemptId = 0L
        if (attemptId == 0L || attemptId != locationAttemptId) return@rememberLauncherForActivityResult

        if (grants.values.any { it }) {
            localError = null
            enqueueWithDeviceLocation(attemptId)
        } else {
            onFailSearchPreparation()
            localError = "Necesitamos ubicacion para buscar personas cerca. " +
                "Podes habilitar permisos o usar el fallback manual de desarrollo."
            manualExpanded = true
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
            model.activeInteractionsSummary?.pendingSchedulingConnectionCount,
            model.activeInteractionsSummary?.actionableConnectionCount,
        ) {
            while (true) {
                delay(60_000.milliseconds)
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

    MatchmakingIdleScreen(
        profile = profile,
        screenModel = model,
        homeLoading = homeLoading,
        homeError = homeError,
        homeMessage = homeMessage,
        nowMillis = nowMillis,
        accountDeleteLoading = accountDeleteLoading,
        accountDeleteError = accountDeleteError,
        localError = localError,
        manualExpanded = manualExpanded,
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
        onEditProfile = onEditProfile,
        onSignOut = onSignOut,
        onDeleteAccount = onDeleteAccount,
    )
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
    localError: String?,
    manualExpanded: Boolean,
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
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var latitude by rememberSaveable(profile.id) { mutableStateOf("-34.6037") }
    var longitude by rememberSaveable(profile.id) { mutableStateOf("-58.3816") }
    var accuracy by rememberSaveable(profile.id) { mutableStateOf("50") }
    val busy = homeLoading || accountDeleteLoading
    val canSearch = screenModel.matchmaking.canSearch
    val blockedReason = screenModel.matchmaking.blockedReason

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${profile.displayName}, tu perfil esta activo.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PendingActionsCard(
            actions = screenModel.pendingActions,
            busy = busy,
            onOpenFirstChat = onOpenFirstChat,
            onOpenVisualApproval = onOpenVisualApproval,
        )
        NextStepCard(
            nextSteps = screenModel.nextSteps,
            busy = busy,
            nowMillis = nowMillis,
            onOpenScheduling = onOpenScheduling,
            onOpenSecondChat = onOpenSecondChat,
            onOpenPartnerProfile = onOpenConnectionPartnerProfile,
            onDismissSecondChat = onDismissSecondChat,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Encontrar chat", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Vamos a usar tu ubicacion actual para encontrar personas compatibles cerca.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActiveInteractionsSummary(
                    summary = screenModel.activeInteractionsSummary,
                    passiveNotices = screenModel.passiveNotices,
                )
                localError?.let { ErrorFeedback("No pudimos usar tu ubicacion", it) }
                homeError?.let { ApiErrorFeedbackCard(it, ErrorContext.Home) }
                homeMessage?.let { SuccessFeedback(it) }
                if (!canSearch && blockedReason != null) {
                    Text(
                        text = blockedReason.matchmakingBlockedMessage()
                            ?: "No pudimos iniciar la busqueda. Revisa tu perfil e intenta nuevamente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onSearchWithDeviceLocation,
                    enabled = !busy && canSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (homeLoading) "Preparando busqueda..." else "Buscar chat")
                }
                OutlinedButton(
                    onClick = { onManualExpandedChange(!manualExpanded) },
                    enabled = !busy && canSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (manualExpanded) "Ocultar fallback manual" else "Fallback manual dev")
                }
                if (manualExpanded) {
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
                                    "Ubicacion invalida. Latitud -90..90, " +
                                        "longitud -180..180, precision 0..100000."
                                )
                            } else {
                                onLocalErrorChange(null)
                                onEnqueue(location)
                            }
                        },
                    )
                }
                OutlinedButton(onClick = onEditProfile, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar perfil y filtros")
                }
                if (homeError != null) {
                    OutlinedButton(onClick = onRefreshHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (homeLoading) "Actualizando..." else "Reintentar estado")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        AccountSection(
            busy = busy,
            accountDeleteLoading = accountDeleteLoading,
            accountDeleteError = accountDeleteError,
            onSignOut = onSignOut,
            onDeleteAccount = onDeleteAccount,
        )
    }
}
@Composable
private fun ActiveInteractionsSummary(
    summary: HomeActiveInteractionsSummary?,
    passiveNotices: List<HomePassiveNoticeItem>,
) {
    if (summary == null) return

    activeExperiencesSummaryText(summary)?.let { text ->
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    passiveNotices.forEach { notice ->
        passiveNoticeText(notice)?.let { text ->
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
