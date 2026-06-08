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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.UpdateMatchFiltersInput
import com.reals.app.domain.model.UpdateProfileInput

@Composable
fun ProfileStatusScreen(
    session: ProvisionedSession,
    profileUpdateLoading: Boolean,
    profileUpdateError: ApiError?,
    profileUpdateMessage: String?,
    matchFiltersLoading: Boolean,
    matchFiltersError: ApiError?,
    matchFiltersMessage: String?,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    accountDeleteLoading: Boolean,
    accountDeleteError: ApiError?,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
    onLoadPhotos: () -> Unit,
    onAddMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onReplaceMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onDeletePhoto: (position: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var confirmingDeleteAccount by rememberSaveable { mutableStateOf(false) }
    val busy = profileUpdateLoading ||
        matchFiltersLoading ||
        photoActionLoading ||
        activationLoading ||
        accountDeleteLoading

    if (confirmingDeleteAccount) {
        AlertDialog(
            onDismissRequest = {
                if (!accountDeleteLoading) confirmingDeleteAccount = false
            },
            title = { Text("Eliminar cuenta") },
            text = {
                Text("Tu cuenta quedara pendiente de eliminacion y podras recuperarla durante la ventana configurada.")
            },
            confirmButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = {
                        confirmingDeleteAccount = false
                        onDeleteAccount()
                    },
                ) {
                    Text("Programar eliminacion")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !accountDeleteLoading,
                    onClick = { confirmingDeleteAccount = false },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

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
                profileUpdateLoading = profileUpdateLoading,
                profileUpdateError = profileUpdateError,
                profileUpdateMessage = profileUpdateMessage,
                matchFiltersLoading = matchFiltersLoading,
                matchFiltersError = matchFiltersError,
                matchFiltersMessage = matchFiltersMessage,
                photosLoading = photosLoading,
                photos = photos,
                photosError = photosError,
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
                activationLoading = activationLoading,
                activationError = activationError,
                onUpdateProfile = onUpdateProfile,
                onUpdateMatchFilters = onUpdateMatchFilters,
                onLoadPhotos = onLoadPhotos,
                onAddMockPhoto = onAddMockPhoto,
                onReplaceMockPhoto = onReplaceMockPhoto,
                onDeletePhoto = onDeletePhoto,
                onActivateProfile = onActivateProfile,
            )
        }
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onRefresh, enabled = !busy) {
                Text("Refrescar")
            }
            OutlinedButton(onClick = onSignOut, enabled = !busy) {
                Text("Cerrar sesion")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )

                Text(
                    text = "Eliminar la cuenta programa una eliminacion recuperable y cierra la sesion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )

                accountDeleteError?.let {
                    Text(
                        text = it.toDisplayMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                OutlinedButton(
                    onClick = { confirmingDeleteAccount = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (accountDeleteLoading) "Programando eliminacion..." else "Eliminar cuenta")
                }
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
    profileUpdateLoading: Boolean,
    profileUpdateError: ApiError?,
    profileUpdateMessage: String?,
    matchFiltersLoading: Boolean,
    matchFiltersError: ApiError?,
    matchFiltersMessage: String?,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
    onLoadPhotos: () -> Unit,
    onAddMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onReplaceMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onDeletePhoto: (position: Int) -> Unit,
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
            Text(text = profile.displayName, style = MaterialTheme.typography.titleLarge)
            Text("Status backend: ${profile.status.rawValue} (${profile.status.label})")
            Text("Edad: ${profile.age}. Ubicacion: ${profile.city}, ${profile.country}")
            Text("Fotos: ${profile.photoCount}. Identidad verificada: ${yesNo(profile.identityVerified)}")
            Text("Filtros: ${profile.preferredMinAge}-${profile.preferredMaxAge} anos, ${profile.maxDistanceKm} km")
            Text(
                text = profileNextStep(profile.status),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            ProfileEditActions(
                profile = profile,
                loading = profileUpdateLoading,
                error = profileUpdateError,
                message = profileUpdateMessage,
                onUpdateProfile = onUpdateProfile,
            )
            MatchFiltersActions(
                profile = profile,
                loading = matchFiltersLoading,
                error = matchFiltersError,
                message = matchFiltersMessage,
                onUpdateMatchFilters = onUpdateMatchFilters,
            )
            PhotoManagerActions(
                profile = profile,
                photosLoading = photosLoading,
                photos = photos,
                photosError = photosError,
                photoActionLoading = photoActionLoading,
                photoActionError = photoActionError,
                photoActionMessage = photoActionMessage,
                activationLoading = activationLoading,
                activationError = activationError,
                onLoadPhotos = onLoadPhotos,
                onAddMockPhoto = onAddMockPhoto,
                onReplaceMockPhoto = onReplaceMockPhoto,
                onDeletePhoto = onDeletePhoto,
                onActivateProfile = onActivateProfile,
            )
        }
    }
}

@Composable
private fun ProfileEditActions(
    profile: Profile,
    loading: Boolean,
    error: ApiError?,
    message: String?,
    onUpdateProfile: (UpdateProfileInput) -> Unit,
) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var displayName by rememberSaveable(profile.id) { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable(profile.id) { mutableStateOf(profile.bio.orEmpty()) }
    var city by rememberSaveable(profile.id) { mutableStateOf(profile.city) }
    var country by rememberSaveable(profile.id) { mutableStateOf(profile.country) }
    var intention by rememberSaveable(profile.id) { mutableStateOf(profile.intention) }
    var lookingForGender by rememberSaveable(profile.id) { mutableStateOf(profile.lookingForGender) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar edicion de perfil" else "Editar perfil")
        }
        if (expanded) {
            Text(
                text = "Campos editables: nombre, bio, ciudad, pais, intencion y busqueda.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Nombre visible") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, enabled = !loading, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it }, label = { Text("Ciudad") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(country, { country = it }, label = { Text("Pais") }, enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth())
            EnumDropdown("Intencion", intention, listOf("DATE", "FRIENDSHIP", "CASUAL"), !loading) { intention = it }
            EnumDropdown("Busco", lookingForGender, listOf("MEN", "WOMEN", "EVERYONE", "OTHER"), !loading) { lookingForGender = it }
            localError?.let { ErrorText(it) }
            error?.let { ErrorText(it.toDisplayMessage()) }
            message?.let { SuccessText(it) }
            Button(
                onClick = {
                    val input = validateUpdateProfileInput(displayName, bio, city, country, intention, lookingForGender)
                    if (input == null) {
                        localError = "Revisa nombre, ciudad, pais y bio. Nombre minimo 2 caracteres; bio maximo 1000."
                    } else {
                        localError = null
                        onUpdateProfile(input)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Guardando..." else "Guardar cambios")
            }
        }
    }
}

@Composable
private fun MatchFiltersActions(
    profile: Profile,
    loading: Boolean,
    error: ApiError?,
    message: String?,
    onUpdateMatchFilters: (UpdateMatchFiltersInput) -> Unit,
) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var minAge by rememberSaveable(profile.id) { mutableStateOf(profile.preferredMinAge.toString()) }
    var maxAge by rememberSaveable(profile.id) { mutableStateOf(profile.preferredMaxAge.toString()) }
    var distance by rememberSaveable(profile.id) { mutableStateOf(profile.maxDistanceKm.toString()) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar filtros" else "Editar filtros de match")
        }
        if (expanded) {
            Text(
                text = "Estos filtros se usan para matchmaking. El backend valida rangos y consistencia.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(minAge, { minAge = it }, "Edad min", !loading, Modifier.weight(1f))
                NumberField(maxAge, { maxAge = it }, "Edad max", !loading, Modifier.weight(1f))
            }
            NumberField(distance, { distance = it }, "Distancia maxima km", !loading, Modifier.fillMaxWidth())
            localError?.let { ErrorText(it) }
            error?.let { ErrorText(it.toDisplayMessage()) }
            message?.let { SuccessText(it) }
            Button(
                onClick = {
                    val input = validateMatchFiltersInput(minAge, maxAge, distance)
                    if (input == null) {
                        localError = "Edades deben estar entre 18 y 99, min <= max, distancia entre 1 y 1000."
                    } else {
                        localError = null
                        onUpdateMatchFilters(input)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Guardando filtros..." else "Guardar filtros")
            }
        }
    }
}

@Composable
private fun PhotoManagerActions(
    profile: Profile,
    photosLoading: Boolean,
    photos: List<ProfilePhoto>,
    photosError: ApiError?,
    photoActionLoading: Boolean,
    photoActionError: ApiError?,
    photoActionMessage: String?,
    activationLoading: Boolean,
    activationError: ApiError?,
    onLoadPhotos: () -> Unit,
    onAddMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onReplaceMockPhoto: (profile: Profile, position: Int, isPersonPhoto: Boolean, isFullBody: Boolean) -> Unit,
    onDeletePhoto: (position: Int) -> Unit,
    onActivateProfile: (Profile) -> Unit,
) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(profile.status == ProfileStatus.Draft) }
    var positionText by rememberSaveable(profile.id) { mutableStateOf(((profile.photoCount + 1).coerceIn(1, 9)).toString()) }
    var isPersonPhoto by rememberSaveable(profile.id) { mutableStateOf(false) }
    var isFullBody by rememberSaveable(profile.id) { mutableStateOf(false) }
    var localError by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    val busy = photosLoading || photoActionLoading || activationLoading

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar fotos" else "Administrar fotos")
        }
        if (expanded) {
            Text(
                text = "Lista, agrega, reemplaza o borra fotos mock. Borrar fotos requeridas puede volver un perfil activo a DRAFT.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onLoadPhotos, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (photosLoading) "Cargando fotos..." else "Refrescar fotos")
            }
            photosError?.let { ErrorText(it.toDisplayMessage()) }
            if (photos.isEmpty()) {
                Text("No hay fotos cargadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                photos.forEach { photo ->
                    PhotoRow(photo = photo, busy = busy, onDeletePhoto = onDeletePhoto)
                }
            }
            OutlinedTextField(
                value = positionText,
                onValueChange = { next -> positionText = next.filter { it.isDigit() } },
                label = { Text("Posicion de foto (1-9)") },
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            CheckboxRow("isPersonPhoto", isPersonPhoto, !busy) { isPersonPhoto = it }
            CheckboxRow("isFullBody", isFullBody, !busy) { isFullBody = it }
            Text(
                text = "URL add: ${previewGeneratedPhotoUrl(profile, positionText.toIntOrNull())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            localError?.let { ErrorText(it) }
            photoActionMessage?.let { SuccessText(it) }
            photoActionError?.let { ErrorText(it.toDisplayMessage()) }
            activationError?.let { ErrorText(it.toDisplayMessage()) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (photoActionLoading) "Guardando..." else "Agregar")
                }
                OutlinedButton(
                    onClick = {
                        val position = positionText.toIntOrNull()
                        if (position == null || position !in 1..9) {
                            localError = "La posicion debe estar entre 1 y 9."
                        } else {
                            localError = null
                            onReplaceMockPhoto(profile, position, isPersonPhoto, isFullBody)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reemplazar")
                }
            }
            if (profile.status == ProfileStatus.Draft) {
                OutlinedButton(onClick = { onActivateProfile(profile) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (activationLoading) "Activando..." else "Intentar activar perfil")
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    photo: ProfilePhoto,
    busy: Boolean,
    onDeletePhoto: (position: Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Posicion ${photo.position} - person=${photo.isPersonPhoto}, fullBody=${photo.isFullBody}")
            Text(
                text = photo.url,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { onDeletePhoto(photo.position) }, enabled = !busy) {
                Text("Borrar posicion ${photo.position}")
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() }) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ErrorText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SuccessText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
}

private fun profileNextStep(status: ProfileStatus): String = when (status) {
    ProfileStatus.Active -> "Perfil activo. Ya estas listo para matchmaking; antes podes ajustar perfil, filtros y fotos."
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

@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable(label, value) { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun validateUpdateProfileInput(
    displayName: String,
    bio: String,
    city: String,
    country: String,
    intention: String,
    lookingForGender: String,
): UpdateProfileInput? {
    val cleanDisplayName = displayName.trim()
    val cleanBio = bio.trim()
    val cleanCity = city.trim()
    val cleanCountry = country.trim()

    if (cleanDisplayName.length !in 2..100) return null
    if (cleanBio.length > 1000) return null
    if (cleanCity.isBlank() || cleanCity.length > 100) return null
    if (cleanCountry.isBlank() || cleanCountry.length > 100) return null
    if (intention !in listOf("DATE", "FRIENDSHIP", "CASUAL")) return null
    if (lookingForGender !in listOf("MEN", "WOMEN", "EVERYONE", "OTHER")) return null

    return UpdateProfileInput(
        displayName = cleanDisplayName,
        bio = cleanBio.ifBlank { null },
        city = cleanCity,
        country = cleanCountry,
        intention = intention,
        lookingForGender = lookingForGender,
    )
}

private fun validateMatchFiltersInput(
    minAge: String,
    maxAge: String,
    distance: String,
): UpdateMatchFiltersInput? {
    val parsedMin = minAge.toIntOrNull()
    val parsedMax = maxAge.toIntOrNull()
    val parsedDistance = distance.toIntOrNull()
    if (parsedMin == null || parsedMax == null || parsedDistance == null) return null
    if (parsedMin !in 18..99 || parsedMax !in 18..99 || parsedMin > parsedMax) return null
    if (parsedDistance !in 1..1000) return null
    return UpdateMatchFiltersInput(
        preferredMinAge = parsedMin,
        preferredMaxAge = parsedMax,
        maxDistanceKm = parsedDistance,
    )
}

private fun yesNo(value: Boolean): String = if (value) "si" else "no"
