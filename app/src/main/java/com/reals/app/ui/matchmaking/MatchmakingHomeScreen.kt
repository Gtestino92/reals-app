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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MatchmakingHomeScreen(
    profile: Profile,
    screenModel: HomeScreenModel?,
    homeLoading: Boolean,
    homeError: ApiError?,
    homeMessage: String?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onEnqueue: (SearchLocationInput) -> Unit,
    onLeaveQueue: () -> Unit,
    onRefreshHome: () -> Unit,
    onPollHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenConnectionPartnerProfile: (matchId: String) -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    if (screenModel == null && homeLoading) {
        LoadingHomeStateScreen()
        return
    }

    val model = screenModel ?: emptyHomeScreenModel()

    if (model.matchmaking.inQueue) {
        SearchingChatScreen(
            homeError = homeError,
            accountDeleteLoading = accountDeleteLoading,
            onPollHome = onPollHome,
            onLeaveQueue = onLeaveQueue,
            onSignOut = onSignOut,
        )
        return
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
                delay(10_000.milliseconds)
                onPollHome()
            }
        }
    }

    MatchmakingIdleScreen(
        profile = profile,
        screenModel = model,
        homeLoading = homeLoading,
        homeError = homeError,
        homeMessage = homeMessage,
        accountDeleteLoading = accountDeleteLoading,
        accountDeleteError = accountDeleteError,
        onEnqueue = onEnqueue,
        onRefreshHome = onRefreshHome,
        onOpenFirstChat = onOpenFirstChat,
        onOpenVisualApproval = onOpenVisualApproval,
        onOpenScheduling = onOpenScheduling,
        onOpenSecondChat = onOpenSecondChat,
        onOpenConnectionPartnerProfile = onOpenConnectionPartnerProfile,
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
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onEnqueue: (SearchLocationInput) -> Unit,
    onRefreshHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenScheduling: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenSecondChat: (connectionId: String, matchId: String, partnerName: String?) -> Unit,
    onOpenConnectionPartnerProfile: (matchId: String) -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var locating by rememberSaveable(profile.id) { mutableStateOf(false) }
    var manualExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var latitude by rememberSaveable(profile.id) { mutableStateOf("-34.6037") }
    var longitude by rememberSaveable(profile.id) { mutableStateOf("-58.3816") }
    var accuracy by rememberSaveable(profile.id) { mutableStateOf("50") }
    val busy = homeLoading || accountDeleteLoading || locating
    val canSearch = screenModel.matchmaking.canSearch
    val blockedReason = screenModel.matchmaking.blockedReason

    fun enqueueWithDeviceLocation() {
        scope.launch {
            locating = true
            localError = null
            val result = runCatching { currentSearchLocation(context) }
            locating = false
            result
                .onSuccess(onEnqueue)
                .onFailure {
                    localError = it.message
                        ?: "No se pudo obtener la ubicacion del dispositivo."
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            localError = null
            enqueueWithDeviceLocation()
        } else {
            localError = "Necesitamos ubicacion para buscar personas cerca. " +
                "Podes habilitar permisos o usar el fallback manual de desarrollo."
            manualExpanded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            onOpenScheduling = onOpenScheduling,
            onOpenSecondChat = onOpenSecondChat,
            onOpenPartnerProfile = onOpenConnectionPartnerProfile,
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
                if (!locating) {
                    localError?.let { ErrorFeedback("No pudimos usar tu ubicacion", it) }
                }
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
                    onClick = {
                        localError = null
                        if (hasLocationPermission(context)) {
                            enqueueWithDeviceLocation()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    },
                    enabled = !busy && canSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (locating || homeLoading) "Preparando busqueda..." else "Buscar chat")
                }
                OutlinedButton(
                    onClick = { manualExpanded = !manualExpanded },
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
                                localError = "Ubicacion invalida. Latitud -90..90, " +
                                    "longitud -180..180, precision 0..100000."
                            } else {
                                localError = null
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
    if (summary.activeInitialCount == 0 &&
        summary.activeConnectionCount == 0 &&
        passiveNotices.isEmpty()
    ) return

    Text(
        text = "Experiencias activas: " +
            "${summary.activeInitialCount} ${if (summary.activeInitialCount == 1) "inicial" else "iniciales"}, " +
            "${summary.activeConnectionCount} ${if (summary.activeConnectionCount == 1) "conexion" else "conexiones"}.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    passiveNotices.forEach { notice ->
        when (notice) {
            is HomePassiveNoticeItem.SchedulingPreparing -> Text(
                text = if (notice.count == 1) {
                    "Tenes una coordinacion en preparacion. Se habilitara mas adelante."
                } else {
                    "Tenes ${notice.count} coordinaciones en preparacion. Se habilitaran mas adelante."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is HomePassiveNoticeItem.Unknown -> Unit
        }
    }
}
