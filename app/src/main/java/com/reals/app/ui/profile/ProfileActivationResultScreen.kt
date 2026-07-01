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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.ui.common.userLabel

@Composable
fun ProfileActivationResultScreen(
    session: ProvisionedSession,
    result: ProfileActivationResult,
    onContinueHome: () -> Unit,
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
            text = "Tu perfil está activo",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Ya podes entrar a Home y buscar un chat cuando quieras.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Estado: ${result.profile.status.userLabel()}")
                Text("Fotos totales: ${result.totalPhotoCount}")
                Text("Tus fotos quedaron guardadas. Si mas adelante las cambias, puede que revisemos tu perfil otra vez.")
            }
        }
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onContinueHome) {
                Text("Ir a Home")
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Cerrar sesion")
            }
        }
    }
}
