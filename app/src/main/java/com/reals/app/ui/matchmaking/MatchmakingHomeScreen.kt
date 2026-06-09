package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.toDisplayMessage
import com.reals.app.domain.model.HomeState
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.SearchLocationInput
import kotlinx.coroutines.delay

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
    onRefreshSession: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var latitude by rememberSaveable(profile.id) { mutableStateOf("-34.6037") }
    var longitude by rememberSaveable(profile.id) { mutableStateOf("-58.3816") }
    var accuracy by rememberSaveable(profile.id) { mutableStateOf("50") }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    val busy = homeLoading || accountDeleteLoading
    val hasActiveEngagements = homeState?.activeMatches?.isNotEmpty() == true ||
        homeState?.activeConnections?.isNotEmpty() == true
    val inQueue = homeState?.queue?.inQueue == true

    LaunchedEffect(inQueue, hasActiveEngagements) {
        while (inQueue && !hasActiveEngagements) {
            delay(5000)
            onRefreshHome()
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
            text = "${profile.displayName} esta ACTIVE. Las acciones visibles salen del estado backend.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Matchmaking", style = MaterialTheme.typography.titleLarge)
                Text("Estado Home: ${if (homeLoading) "consultando" else "actualizado"}")
                Text("Profile status discovery: ${homeState?.profileStatus?.rawValue ?: "sin consultar"}")
                Text("Cola: ${if (inQueue) "BUSCANDO" else "FUERA DE COLA"}")
                if ((homeState?.activeMatches?.size ?: 0) > 1 || (homeState?.activeConnections?.size ?: 0) > 1) {
                    Text(
                        text = "Hay multiples engagements activos. Esta UX inicial elige el primero; queda pendiente una bandeja/lista.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                homeState?.activeMatches?.forEach { match ->
                    Text(
                        text = "Match ${match.matchId}: ${match.matchState.rawValue}, firstChat=${match.firstChat?.chatId ?: "null"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                homeState?.activeConnections?.forEach { connection ->
                    Text(
                        text = "Connection ${connection.connectionId}: ${connection.connectionState.rawValue}, secondChat=${connection.secondChat?.chatId ?: "null"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "El enqueue requiere ubicacion de busqueda actual. Por ahora se ingresa manualmente para evitar permisos/location SDK en este corte.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberTextField(latitude, { latitude = signedDecimalInput(it) }, "Latitud", !busy, Modifier.weight(1f))
                    NumberTextField(longitude, { longitude = signedDecimalInput(it) }, "Longitud", !busy, Modifier.weight(1f))
                }
                NumberTextField(accuracy, { accuracy = it.filter { char -> char.isDigit() } }, "Precision metros", !busy, Modifier.fillMaxWidth())
                localError?.let { ErrorText(it) }
                homeError?.let { ErrorText(it.toDisplayMessage()) }
                homeMessage?.let { SuccessText(it) }
                Button(
                    onClick = {
                        val location = validateLocation(latitude, longitude, accuracy)
                        if (location == null) {
                            localError = "Ubicacion invalida. Latitud -90..90, longitud -180..180, precision 0..100000."
                        } else {
                            localError = null
                            onEnqueue(location)
                        }
                    },
                    enabled = !busy && !inQueue && !hasActiveEngagements,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (homeLoading) "Entrando..." else "Entrar a sala de chat")
                }
                if (inQueue && !hasActiveEngagements) {
                    Text(
                        text = "Buscando match. Esta pantalla consulta GET /api/me/home cada 5 segundos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onLeaveQueue, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (homeLoading) "Saliendo..." else "Salir de cola")
                    }
                }
                OutlinedButton(onClick = onRefreshHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (homeLoading) "Consultando..." else "Refrescar Home")
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRefreshSession, enabled = !busy) {
                Text("Refrescar sesion")
            }
            OutlinedButton(onClick = onSignOut, enabled = !busy) {
                Text("Cerrar sesion")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onDeleteAccount, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (accountDeleteLoading) "Eliminando..." else "Eliminar cuenta")
        }
        accountDeleteError?.let { ErrorText(it.toDisplayMessage()) }
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
private fun ErrorText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun SuccessText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.primary)
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
