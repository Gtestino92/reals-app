package com.reals.app.ui.matchmaking

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ConnectionState
import com.reals.app.domain.model.HomeConnection
import com.reals.app.domain.model.HomeMatch
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.MatchState
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.SearchLocationInput
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone
import com.reals.app.ui.common.userLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MatchmakingHomeScreen(
    profile: Profile,
    homeState: HomeState?,
    homeLoading: Boolean,
    homeError: ApiError?,
    homeMessage: String?,
    matchmakingBlockedReason: ApiError?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onEnqueue: (SearchLocationInput) -> Unit,
    onLeaveQueue: () -> Unit,
    onRefreshHome: () -> Unit,
    onPollHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
    onOpenConnectionPartnerProfile: (matchId: String) -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    hasLocallyHiddenInteractions: Boolean,
) {
    val hasActiveEngagements = homeState.hasBlockingEngagements()
    val inQueue = homeState?.queue?.inQueue == true
    val matchmakingBlockedByLimit = matchmakingBlockedReason.isActiveMatchLimitReached()

    if (homeState == null && homeLoading) {
        LoadingHomeStateScreen()
        return
    }

    if (inQueue && !hasActiveEngagements) {
        SearchingChatScreen(
            homeError = homeError,
            accountDeleteLoading = accountDeleteLoading,
            onPollHome = onPollHome,
            onLeaveQueue = onLeaveQueue,
            onSignOut = onSignOut,
        )
        return
    }

    if (homeState.shouldPollHome(hasLocallyHiddenInteractions)) {
        LaunchedEffect(
            homeState?.queue?.inQueue,
            homeState?.activeMatches?.size,
            homeState?.activeConnections?.size,
        ) {
            while (true) {
                delay(10_000.milliseconds)
                onPollHome()
            }
        }
    }

    MatchmakingIdleScreen(
        profile = profile,
        homeState = homeState,
        homeLoading = homeLoading,
        homeError = homeError,
        homeMessage = homeMessage,
        matchmakingBlockedByLimit = matchmakingBlockedByLimit,
        accountDeleteLoading = accountDeleteLoading,
        accountDeleteError = accountDeleteError,
        hasActiveEngagements = hasActiveEngagements,
        onEnqueue = onEnqueue,
        onRefreshHome = onRefreshHome,
        onOpenFirstChat = onOpenFirstChat,
        onOpenVisualApproval = onOpenVisualApproval,
        onOpenConnectionPartnerProfile = onOpenConnectionPartnerProfile,
        onEditProfile = onEditProfile,
        onSignOut = onSignOut,
        onDeleteAccount = onDeleteAccount,
    )
}

@Composable
private fun MatchmakingIdleScreen(
    profile: Profile,
    homeState: HomeState?,
    homeLoading: Boolean,
    homeError: ApiError?,
    homeMessage: String?,
    matchmakingBlockedByLimit: Boolean,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    hasActiveEngagements: Boolean,
    onEnqueue: (SearchLocationInput) -> Unit,
    onRefreshHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
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

    fun enqueueWithDeviceLocation() {
        scope.launch {
            locating = true
            localError = null
            val result = runCatching { currentSearchLocation(context) }
            locating = false
            result
                .onSuccess(onEnqueue)
                .onFailure { localError = it.message ?: "No se pudo obtener la ubicacion del dispositivo." }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            enqueueWithDeviceLocation()
        } else {
            localError = "Necesitamos ubicacion para buscar personas cerca. Podes habilitar permisos o usar el fallback manual de desarrollo."
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
        PendingInteractionsCard(
            homeState = homeState,
            busy = busy,
            onOpenFirstChat = onOpenFirstChat,
            onOpenVisualApproval = onOpenVisualApproval,
        )
        NextStepCard(
            homeState = homeState,
            busy = busy,
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
                EngagementSummary(homeState)
                localError?.let { ErrorFeedback("No pudimos usar tu ubicacion", it) }
                homeError?.let { ApiErrorFeedbackCard(it, ErrorContext.Home) }
                homeMessage?.let { SuccessFeedback(it) }
                if (matchmakingBlockedByLimit) {
                    Text(
                        text = "Ya tenés el máximo de interacciones activas. Resolvé alguna pendiente antes de buscar otro chat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
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
                    enabled = !busy && !matchmakingBlockedByLimit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (locating || homeLoading) "Preparando busqueda..." else "Buscar chat")
                }
                OutlinedButton(onClick = onEditProfile, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar perfil y filtros")
                }
                OutlinedButton(
                    onClick = { manualExpanded = !manualExpanded },
                    enabled = !busy && !matchmakingBlockedByLimit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (manualExpanded) "Ocultar fallback manual" else "Fallback manual dev")
                }
                if (manualExpanded) {
                    ManualLocationFallback(
                        latitude = latitude,
                        longitude = longitude,
                        accuracy = accuracy,
                        enabled = !busy && !hasActiveEngagements && !matchmakingBlockedByLimit,
                        onLatitudeChange = { latitude = signedDecimalInput(it) },
                        onLongitudeChange = { longitude = signedDecimalInput(it) },
                        onAccuracyChange = { accuracy = it.filter { char -> char.isDigit() } },
                        onSubmit = {
                            val location = validateLocation(latitude, longitude, accuracy)
                            if (location == null) {
                                localError = "Ubicacion invalida. Latitud -90..90, longitud -180..180, precision 0..100000."
                            } else {
                                localError = null
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
private fun VisualApprovalItem(
    match: HomeMatch,
    busy: Boolean,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val partnerName = match.partnerDisplayName?.takeIf { it.isNotBlank() }

            Text(
                text = partnerName?.let { "Aprobación visual con $it" }
                    ?: "Aprobación visual pendiente"
            )
            Text(
                text = "Revisa el perfil visual y decidi si queres continuar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenVisualApproval(match.matchId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Abrir aprobacion visual")
            }
        }
    }
}

@Composable
private fun ConnectionPlaceholderItem(
    connection: HomeConnection,
    busy: Boolean,
    onOpenPartnerProfile: (matchId: String) -> Unit,
) {
    val partnerName = connection.partnerDisplayName()
        ?.let(TextSafety::safeDisplay)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Coordinación pendiente", style = MaterialTheme.typography.titleMedium)

            Text(
                text = partnerName?.let { "Con $it" } ?: "Con la otra persona",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Estado: ${connection.connectionState.userLabel()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenPartnerProfile(connection.matchId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ver perfil")
            }

            Text(
                text = "Ya hubo aprobación visual mutua. Falta implementar la coordinación del próximo chat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EngagementSummary(homeState: HomeState?) {
    val counts = homeState.engagementCounts()
    if (counts.total == 0) return

    val parts = buildList {
        if (counts.firstChats > 0) {
            add(
                "${counts.firstChats} " +
                        if (counts.firstChats == 1) "chat inicial" else "chats iniciales"
            )
        }

        if (counts.visualReviews > 0) {
            add(
                "${counts.visualReviews} " +
                        if (counts.visualReviews == 1) "revisión visual" else "revisiones visuales"
            )
        }

        if (counts.connections > 0) {
            add(
                "${counts.connections} " +
                        if (counts.connections == 1) "conexión" else "conexiones"
            )
        }
    }

    if (counts.total > 1) {
        Text(
            text = "Tenés más de una interacción pendiente. Podés continuar una de ellas o buscar un nuevo chat si todavía tenés cupo disponible.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        text = "Interacciones pendientes: ${parts.joinToString(", ")}.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

