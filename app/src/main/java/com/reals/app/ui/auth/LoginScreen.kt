package com.reals.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.RealsBrandSeal
import com.reals.app.ui.common.RealsPrimaryButton
import com.reals.app.ui.common.RealsSecondaryButton
import com.reals.app.ui.common.realsOutlinedTextFieldColors
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    loading: Boolean,
    googleLoading: Boolean,
    error: String?,
    passwordResetLoading: Boolean,
    passwordResetMessage: String?,
    passwordResetAvailableAtMillis: Long?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onPasswordReset: (email: String) -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nowMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val cooldownRemainingSeconds = passwordResetCooldownRemainingSeconds(
        availableAtMillis = passwordResetAvailableAtMillis,
        nowMillis = nowMillis,
    )
    val authBusy = loading || googleLoading || passwordResetLoading

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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RealsBrandSeal(modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Reals",
            style = RealsType.Identity,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RealsBrandDivider(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
        )
        Text(
            text = "Ingresá o creá tu cuenta para empezar.",
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !authBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(RealsRadii.Button),
                colors = realsOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !authBusy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(RealsRadii.Button),
                colors = realsOutlinedTextFieldColors(),
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
            RealsPrimaryButton(
                text = if (loading) "Ingresando..." else "Ingresar",
                onClick = { onSignIn(email, password) },
                enabled = !authBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            RealsSecondaryButton(
                text = "Crear cuenta",
                onClick = { onSignUp(email, password) },
                enabled = !authBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { onPasswordReset(email) },
                enabled = passwordResetButtonEnabled(
                    loginLoading = loading,
                    googleLoading = googleLoading,
                    passwordResetLoading = passwordResetLoading,
                    cooldownRemainingSeconds = cooldownRemainingSeconds,
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RealsRadii.Button),
            ) {
                Text(
                    passwordResetButtonText(
                        loading = passwordResetLoading,
                        cooldownRemainingSeconds = cooldownRemainingSeconds,
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "o",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !authBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RealsRadii.Button),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(googleSignInButtonText(googleLoading))
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
    googleLoading: Boolean,
    passwordResetLoading: Boolean,
    cooldownRemainingSeconds: Long,
): Boolean = !loginLoading && !googleLoading && !passwordResetLoading && cooldownRemainingSeconds <= 0L

internal fun googleSignInButtonText(googleLoading: Boolean): String =
    if (googleLoading) "Conectando con Google..." else "Continuar con Google"
