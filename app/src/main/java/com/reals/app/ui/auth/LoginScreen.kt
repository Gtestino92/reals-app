package com.reals.app.ui.auth

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    passwordResetLoading: Boolean,
    passwordResetMessage: String?,
    passwordResetAvailableAtMillis: Long?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onPasswordReset: (email: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nowMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val cooldownRemainingSeconds = passwordResetCooldownRemainingSeconds(
        availableAtMillis = passwordResetAvailableAtMillis,
        nowMillis = nowMillis,
    )

    LaunchedEffect(passwordResetAvailableAtMillis) {
        while (passwordResetCooldownRemainingSeconds(passwordResetAvailableAtMillis, System.currentTimeMillis()) > 0) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
        nowMillis = System.currentTimeMillis()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Reals",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Login inicial con Firebase Email/Password.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (passwordResetMessage != null) {
                    Text(
                        text = passwordResetMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = { onSignIn(email, password) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loading) "Ingresando..." else "Ingresar")
                }
                OutlinedButton(
                    onClick = { onSignUp(email, password) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear cuenta")
                }
                OutlinedButton(
                    onClick = { onPasswordReset(email) },
                    enabled = passwordResetButtonEnabled(
                        loginLoading = loading,
                        passwordResetLoading = passwordResetLoading,
                        cooldownRemainingSeconds = cooldownRemainingSeconds,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        passwordResetButtonText(
                            loading = passwordResetLoading,
                            cooldownRemainingSeconds = cooldownRemainingSeconds,
                        )
                    )
                }
            }
        }
    }
}

internal fun passwordResetCooldownRemainingSeconds(
    availableAtMillis: Long?,
    nowMillis: Long,
): Long {
    val remainingMillis = ((availableAtMillis ?: 0L) - nowMillis).coerceAtLeast(0L)
    return (remainingMillis + 999L) / 1_000L
}

internal fun passwordResetButtonText(
    loading: Boolean,
    cooldownRemainingSeconds: Long,
): String = when {
    loading -> "Enviando..."
    cooldownRemainingSeconds > 0L -> "Reenviar en ${cooldownRemainingSeconds}s"
    else -> "Olvidé mi contraseña"
}

internal fun passwordResetButtonEnabled(
    loginLoading: Boolean,
    passwordResetLoading: Boolean,
    cooldownRemainingSeconds: Long,
): Boolean = !loginLoading && !passwordResetLoading && cooldownRemainingSeconds <= 0L
