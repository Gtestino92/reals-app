package com.reals.app.ui.profile

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.toDisplayMessage
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession

@Composable
fun ProfileStatusScreen(
    session: ProvisionedSession,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    onAddMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Estado de Reals",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Backend user: ${session.user.id}",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        when (val snapshot = session.profileSnapshot) {
            ProfileSnapshot.Missing -> MissingProfileCard()
            is ProfileSnapshot.Found -> ProfileCard(
                profile = snapshot.profile,
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
                activationLoading = activationLoading,
                activationError = activationError,
                onAddMockPhoto = onAddMockPhoto,
                onActivateProfile = onActivateProfile,
            )
        }
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onRefresh, enabled = !photoActionLoading && !activationLoading) {
                Text("Refrescar")
            }
            OutlinedButton(onClick = onSignOut, enabled = !photoActionLoading && !activationLoading) {
                Text("Cerrar sesion")
            }
        }
    }
}

@Composable
private fun MissingProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Todavia no tenes perfil",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Tu cuenta ya esta autenticada y provisionada. El proximo paso es crear el perfil para completar el onboarding.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    onAddMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onActivateProfile: (Profile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Status backend: ${profile.status.rawValue} (${profile.status.label})")
            Text("Edad: ${profile.age}. Ubicacion: ${profile.city}, ${profile.country}")
            Text("Fotos: ${profile.photoCount}. Identidad verificada: ${yesNo(profile.identityVerified)}")
            Text("Filtros: ${profile.preferredMinAge}-${profile.preferredMaxAge} anos, ${profile.maxDistanceKm} km")
            Text(
                text = profileNextStep(profile.status),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (profile.status == ProfileStatus.Draft) {
                DraftPhotoActions(
                    profile = profile,
                    photoActionLoading = photoActionLoading,
                    photoActionError = photoActionError,
                    photoActionMessage = photoActionMessage,
                    activationLoading = activationLoading,
                    activationError = activationError,
                    onAddMockPhoto = onAddMockPhoto,
                    onActivateProfile = onActivateProfile,
                )
            }
        }
    }
}

@Composable
private fun DraftPhotoActions(
    profile: Profile,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    onAddMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onActivateProfile: (Profile) -> Unit,
) {
    var positionText by rememberSaveable(profile.id) {
        mutableStateOf(((profile.photoCount + 1).coerceIn(1, 9)).toString())
    }
    var isPersonPhoto by rememberSaveable(profile.id) { mutableStateOf(false) }
    var isFullBody by rememberSaveable(profile.id) { mutableStateOf(false) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Fotos mock para pruebas",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Agrega fotos una por una y luego intenta activar para probar errores como fotos insuficientes, falta de person photo o falta de full body.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = positionText,
            onValueChange = { next -> positionText = next.filter { it.isDigit() } },
            label = { Text("Posicion de foto (1-9)") },
            enabled = !photoActionLoading && !activationLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = isPersonPhoto,
                onCheckedChange = { isPersonPhoto = it },
                enabled = !photoActionLoading && !activationLoading,
            )
            Text("isPersonPhoto")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = isFullBody,
                onCheckedChange = { isFullBody = it },
                enabled = !photoActionLoading && !activationLoading,
            )
            Text("isFullBody")
        }
        Text(
            text = "URL generada: ${previewGeneratedPhotoUrl(profile, positionText.toIntOrNull())}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        localError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        photoActionMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        photoActionError?.let { error ->
            Text(
                text = error.toDisplayMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        activationError?.let { error ->
            Text(
                text = error.toDisplayMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = {
                val position = positionText.toIntOrNull()
                if (position == null || position !in 1..9) {
                    localError = "La posicion debe estar entre 1 y 9."
                } else {
                    localError = null
                    onAddMockPhoto(profile, position, isPersonPhoto, isFullBody)
                }
            },
            enabled = !photoActionLoading && !activationLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (photoActionLoading) "Agregando foto..." else "Agregar foto mock")
        }
        OutlinedButton(
            onClick = { onActivateProfile(profile) },
            enabled = !photoActionLoading && !activationLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (activationLoading) "Activando..." else "Intentar activar perfil")
        }
    }
}

private fun profileNextStep(status: ProfileStatus): String = when (status) {
    ProfileStatus.Active -> "Perfil activo. La entrada a matchmaking se implementa despues."
    ProfileStatus.Draft -> "Perfil en borrador. Agrega fotos mock o intenta activar para ver la validacion del backend."
    ProfileStatus.Inactive -> "Perfil inactivo segun backend. Acciones bloqueadas hasta definir reactivacion."
    is ProfileStatus.Unknown -> "Estado no reconocido: ${status.rawValue}. Acciones sensibles bloqueadas."
}

private fun previewGeneratedPhotoUrl(profile: Profile, position: Int?): String {
    if (position == null) return "elige una posicion"
    val userId = profile.userId.replace("-", "")
    val profileId = profile.id.replace("-", "")
    return "https://static.reals.local/mock-profiles/$userId/$profileId/photo-$position.jpg"
}

private fun yesNo(value: Boolean): String = if (value) "si" else "no"
