package com.reals.app.ui.matchmaking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
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
import kotlin.coroutines.resume
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MatchmakingHomeScreen(
    profile: Profile,
    homeState: HomeState?,
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
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val hasActiveEngagements = homeState.hasBlockingEngagements()
    val inQueue = homeState?.queue?.inQueue == true

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

    if (homeState.shouldPollHome()) {
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
        accountDeleteLoading = accountDeleteLoading,
        accountDeleteError = accountDeleteError,
        hasActiveEngagements = hasActiveEngagements,
        onEnqueue = onEnqueue,
        onRefreshHome = onRefreshHome,
        onOpenFirstChat = onOpenFirstChat,
        onOpenVisualApproval = onOpenVisualApproval,
        onEditProfile = onEditProfile,
        onSignOut = onSignOut,
        onDeleteAccount = onDeleteAccount,
    )
}

@Composable
private fun LoadingHomeStateScreen() {
    val pulse = rememberInfiniteTransition(label = "home-loading-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home-loading-dot-scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Text(
            text = "Cargando estado actual",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Estamos preparando tu estado actual.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchingChatScreen(
    homeError: ApiError?,
    accountDeleteLoading: Boolean,
    onPollHome: () -> Unit,
    onLeaveQueue: () -> Unit,
    onSignOut: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "searching-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "searching-dot-scale",
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000.milliseconds)
            onPollHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Text(
            text = "Buscando chat",
            modifier = Modifier.padding(top = 28.dp),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Estamos buscando alguien compatible. Cuando encontremos una persona, vas a entrar al chat automaticamente.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        homeError?.let {
            ApiErrorFeedbackCard(
                error = it,
                context = ErrorContext.Home,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedButton(onClick = onLeaveQueue, enabled = !accountDeleteLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar busqueda")
        }
        OutlinedButton(onClick = onSignOut, enabled = !accountDeleteLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesion")
        }
    }
}

@Composable
private fun MatchmakingIdleScreen(
    profile: Profile,
    homeState: HomeState?,
    homeLoading: Boolean,
    homeError: ApiError?,
    homeMessage: String?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    hasActiveEngagements: Boolean,
    onEnqueue: (SearchLocationInput) -> Unit,
    onRefreshHome: () -> Unit,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
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
        ActiveEngagementCard(
            homeState = homeState,
            busy = busy,
            onOpenFirstChat = onOpenFirstChat,
            onOpenVisualApproval = onOpenVisualApproval,
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
                homeError?.let { ApiErrorFeedbackCard(it, ErrorContext.Matchmaking) }
                homeMessage?.let { SuccessFeedback(it) }
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
                    enabled = !busy && !hasActiveEngagements,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (locating || homeLoading) "Preparando busqueda..." else "Buscar chat")
                }
                OutlinedButton(onClick = onEditProfile, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar perfil y filtros")
                }
                OutlinedButton(
                    onClick = { manualExpanded = !manualExpanded },
                    enabled = !busy && !hasActiveEngagements,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (manualExpanded) "Ocultar fallback manual" else "Fallback manual dev")
                }
                if (manualExpanded) {
                    ManualLocationFallback(
                        latitude = latitude,
                        longitude = longitude,
                        accuracy = accuracy,
                        enabled = !busy && !hasActiveEngagements,
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
private fun ActiveEngagementCard(
    homeState: HomeState?,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
    onOpenVisualApproval: (matchId: String) -> Unit,
) {
    val firstChatMatches = homeState?.activeMatches
        ?.filter { it.matchState == MatchState.ChatActive && it.firstChat != null }
        .orEmpty()
    val visualApprovals = homeState?.activeMatches
        ?.filter { it.matchState == MatchState.VisualPhase }
        .orEmpty()
    val connections = homeState?.activeConnections
        ?.filter { it.connectionState != ConnectionState.Closed }
        .orEmpty()
    if (firstChatMatches.isEmpty() && visualApprovals.isEmpty() && connections.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Experiencias activas",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            firstChatMatches.forEach { match ->
                FirstChatItem(
                    match = match,
                    busy = busy,
                    onOpenFirstChat = onOpenFirstChat,
                )
            }
            visualApprovals.forEach { match ->
                VisualApprovalItem(
                    match = match,
                    busy = busy,
                    onOpenVisualApproval = onOpenVisualApproval,
                )
            }
            connections.forEach { connection ->
                ConnectionPlaceholderItem(connection)
            }
        }
    }
}

@Composable
private fun FirstChatItem(
    match: HomeMatch,
    busy: Boolean,
    onOpenFirstChat: (matchId: String, chatId: String) -> Unit,
) {
    val firstChat = match.firstChat ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chat inicial", style = MaterialTheme.typography.titleMedium)
            val partnerName = firstChat.partner?.displayName
                ?.takeIf { it.isNotBlank() }
                ?.let(TextSafety::safeDisplay)

            Text(
                text = partnerName?.let { "Con $it" } ?: "Chat inicial activo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Valido hasta ${formatBackendDateTime(firstChat.expiresAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Estado: ${firstChat.chatStatus.userLabel()}. Podes entrar cuando quieras.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenFirstChat(match.matchId, firstChat.chatId) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Entrar al chat")
            }
        }
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
            Text("Aprobacion visual pendiente", style = MaterialTheme.typography.titleMedium)
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
private fun ConnectionPlaceholderItem(connection: HomeConnection) {
    val partnerName = connection.secondChat?.partner?.displayName?.takeIf { it.isNotBlank() }
        ?: connection.partner?.displayName?.takeIf { it.isNotBlank() }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Siguiente etapa", style = MaterialTheme.typography.titleMedium)
            partnerName?.let {
                Text(
                    text = "Con $it",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Estado: ${connection.connectionState.userLabel()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "La pantalla de coordinacion/segundo chat se implementa en la siguiente etapa.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSection(
    busy: Boolean,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = {
                if (!accountDeleteLoading) confirmingDelete = false
            },
            title = { Text("Eliminar cuenta") },
            text = { Text("Tu cuenta quedara pendiente de eliminacion y podras recuperarla durante 30 dias.") },
            confirmButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = {
                        confirmingDelete = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Programar eliminacion")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = { confirmingDelete = false },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Cuenta", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Sesion y acciones sensibles.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { expanded = !expanded }, enabled = !busy) {
                    Text(if (expanded) "Ocultar" else "Abrir")
                }
            }
            if (expanded) {
                OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar sesion")
                }
                Text(
                    text = "Eliminar la cuenta programa una eliminacion recuperable durante 30 dias y cierra la sesion.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                accountDeleteError?.let { ApiErrorFeedbackCard(it, ErrorContext.Account) }
                OutlinedButton(onClick = { confirmingDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (accountDeleteLoading) "Eliminando..." else "Eliminar cuenta")
                }
            }
        }
    }
}

@Composable
private fun EngagementSummary(homeState: HomeState?) {
    val matchCount = homeState?.activeMatches
        ?.count { it.matchState == MatchState.ChatActive || it.matchState == MatchState.VisualPhase }
        ?: 0
    val connectionCount = homeState?.activeConnections
        ?.count { it.connectionState != ConnectionState.Closed }
        ?: 0
    if (matchCount > 1 || connectionCount > 1) {
        Text(
            text = "Tenes mas de una experiencia activa. Por ahora vamos a abrir la primera disponible.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (matchCount > 0 || connectionCount > 0) {
        Text(
            text = "Experiencias activas: $matchCount chats, $connectionCount conexiones.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManualLocationFallback(
    latitude: String,
    longitude: String,
    accuracy: String,
    enabled: Boolean,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onAccuracyChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        text = "Solo para desarrollo/emulador cuando no hay ubicacion disponible.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberTextField(latitude, onLatitudeChange, "Latitud", enabled, Modifier.weight(1f))
        NumberTextField(longitude, onLongitudeChange, "Longitud", enabled, Modifier.weight(1f))
    }
    NumberTextField(accuracy, onAccuracyChange, "Precision metros", enabled, Modifier.fillMaxWidth())
    OutlinedButton(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("Buscar con fallback manual")
    }
}

@Composable
private fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ErrorFeedback(title: String, message: String) {
    FeedbackCard(title = title, message = message, tone = FeedbackTone.Error)
}

@Composable
private fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

private suspend fun currentSearchLocation(context: Context): SearchLocationInput {
    if (!hasLocationPermission(context)) {
        error("Falta permiso de ubicacion.")
    }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = preferredProvider(locationManager)
        ?: error("Activa la ubicacion del dispositivo para buscar chat.")
    val location = requestCurrentLocation(context, locationManager, provider)
        ?: newestLastKnownLocation(locationManager)
        ?: error("No hay ubicacion disponible todavia. Intenta nuevamente en unos segundos.")
    return SearchLocationInput(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }?.toInt()?.coerceIn(0, 100000),
    )
}

private fun preferredProvider(locationManager: LocationManager): String? {
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
}

private suspend fun requestCurrentLocation(
    context: Context,
    locationManager: LocationManager,
    provider: String,
): Location? = suspendCancellableCoroutine { continuation ->
    val cancellationSignal = CancellationSignal()
    continuation.invokeOnCancellation { cancellationSignal.cancel() }
    try {
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    } catch (exception: SecurityException) {
        if (continuation.isActive) continuation.resume(null)
    } catch (exception: IllegalArgumentException) {
        if (continuation.isActive) continuation.resume(null)
    }
}

private fun newestLastKnownLocation(locationManager: LocationManager): Location? {
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}

private fun signedDecimalInput(value: String): String =
    value.filterIndexed { index, char ->
        char.isDigit() || char == '.' || (char == '-' && index == 0)
    }

private fun validateLocation(
    latitude: String,
    longitude: String,
    accuracy: String,
): SearchLocationInput? {
    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val parsedAccuracy = accuracy.toIntOrNull()
    if (parsedLatitude == null || parsedLongitude == null) return null
    if (parsedLatitude !in -90.0..90.0 || parsedLongitude !in -180.0..180.0) return null
    if (parsedAccuracy != null && parsedAccuracy !in 0..100000) return null
    return SearchLocationInput(
        latitude = parsedLatitude,
        longitude = parsedLongitude,
        accuracyMeters = parsedAccuracy,
    )
}

private fun HomeState?.hasBlockingEngagements(): Boolean {
    if (this == null) return false
    return activeMatches.any {
        it.matchState == MatchState.ChatActive || it.matchState == MatchState.VisualPhase
    }
}

private fun HomeState?.shouldPollHome(): Boolean {
    if (this == null) return false
    return activeMatches.any {
        it.matchState == MatchState.ChatActive || it.matchState == MatchState.VisualPhase
    } || activeConnections.any { it.connectionState != ConnectionState.Closed }
}

private fun formatBackendDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault()))
    }.getOrElse {
        value.replace("T", " ").substringBeforeLast(":")
    }
}
