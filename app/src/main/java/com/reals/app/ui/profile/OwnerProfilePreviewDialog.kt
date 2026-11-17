package com.reals.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reals.app.ui.chat.ProfilePresentationMode
import com.reals.app.ui.chat.VisualProfileCard
import com.reals.app.ui.root.OwnerProfilePreviewState

@Composable
internal fun OwnerProfilePreviewDialog(state: OwnerProfilePreviewState, onClose: () -> Unit, onRefresh: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val heading = remember { FocusRequester() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LaunchedEffect(Unit) { heading.requestFocus() }
                Text("Vista previa de mi perfil", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.focusRequester(heading).focusable())
                TextButton(onClick = onClose) { Text("Volver") }
                if (state.loading) Text("Cargando perfil…")
                if (state.error != null) {
                    Text("No pudimos actualizar la vista previa. Reintentá la consulta.")
                    OutlinedButton(onClick = onRefresh) { Text("Reintentar") }
                }
                if (state.draft) Text("Tu perfil está en borrador. Esta vista previa no lo activa.")
                state.profile?.let {
                    if (it.photos.isEmpty()) Text("No hay fotos aprobadas para mostrar.")
                    VisualProfileCard(it, presentationMode = ProfilePresentationMode.Browse)
                }
            }
        }
    }
}
