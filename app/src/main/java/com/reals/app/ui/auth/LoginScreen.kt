package com.reals.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.RealsBrandSeal
import com.reals.app.ui.common.RealsPrimaryButton
import com.reals.app.ui.common.realsOutlinedTextFieldColors
import com.reals.app.ui.root.LoginErrorOwner
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    loading: Boolean,
    googleLoading: Boolean,
    error: String?,
    errorOwner: LoginErrorOwner?,
    passwordResetLoading: Boolean,
    passwordResetMessage: String?,
    passwordResetAvailableAtMillis: Long?,
    onSignIn: (email: String, password: String, rememberCredentials: Boolean) -> Unit,
    onSignUp: (email: String, password: String, rememberCredentials: Boolean) -> Unit,
    onPasswordReset: (email: String) -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    var authMode by rememberSaveable { mutableStateOf(initialAuthMode()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberCredentials by remember { mutableStateOf(defaultRememberCredentials()) }
    var nowMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val cooldownRemainingSeconds = passwordResetCooldownRemainingSeconds(
        availableAtMillis = passwordResetAvailableAtMillis,
        nowMillis = nowMillis,
    )
    val authBusy = loading || googleLoading || passwordResetLoading
    val visibleError = visibleAuthError(
        error = error,
        authMode = authMode,
        errorOwner = errorOwner,
    )
    val compactForIme = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(enabled = authModeBackHandlerEnabled(authMode, authBusy)) {
        authMode = authModeAfterBack(authMode, authBusy)
    }

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
        verticalArrangement = if (compactForIme) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loginHeaderVisible(compactForIme)) {
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
                text = authModeHeading(authMode),
                modifier = Modifier
                    .padding(top = 22.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = authModeBody(authMode),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(26.dp))
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginEmailFieldTag)
                    .contentType(loginEmailContentType()),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginPasswordFieldTag)
                    .contentType(loginPasswordContentType()),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = rememberCredentials,
                        enabled = !authBusy,
                        role = Role.Checkbox,
                        onValueChange = { rememberCredentials = it },
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = rememberCredentials,
                    onCheckedChange = null,
                    enabled = !authBusy,
                )
                Text(
                    text = rememberCredentialsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (visibleError != null) {
                Text(
                    text = visibleError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (authModeShowsPasswordReset(authMode) && passwordResetMessage != null) {
                Text(
                    text = passwordResetMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RealsPrimaryButton(
                text = authPrimaryButtonText(authMode, loading),
                onClick = {
                    submitAuthMode(
                        authMode = authMode,
                        email = email,
                        password = password,
                        rememberCredentials = rememberCredentials,
                        onSignIn = onSignIn,
                        onSignUp = onSignUp,
                    )
                },
                enabled = !authBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    authMode = switchedAuthMode(
                        currentMode = authMode,
                        authBusy = authBusy,
                    )
                },
                enabled = authModeSwitchEnabled(authBusy),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(authModeSwitchActionText(authMode))
            }
            if (authModeShowsPasswordReset(authMode)) {
                OutlinedButton(
                    onClick = {
                        onPasswordReset(email)
                    },
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
                onClick = {
                    onGoogleSignIn()
                },
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

internal enum class AuthMode {
    SignIn,
    SignUp,
}

internal fun initialAuthMode(): AuthMode = AuthMode.SignIn

internal fun authModeHeading(authMode: AuthMode): String = when (authMode) {
    AuthMode.SignIn -> "Iniciar sesión"
    AuthMode.SignUp -> "Creá tu cuenta"
}

internal fun authModeBody(authMode: AuthMode): String = when (authMode) {
    AuthMode.SignIn -> "Ingresá con tu email y contraseña para continuar."
    AuthMode.SignUp -> "Registrate con tu email y contraseña para empezar."
}

internal fun authPrimaryButtonText(authMode: AuthMode, loading: Boolean): String = when (authMode) {
    AuthMode.SignIn -> if (loading) "Ingresando..." else "Ingresar"
    AuthMode.SignUp -> if (loading) "Creando cuenta..." else "Crear cuenta"
}

internal fun authModeSwitchActionText(authMode: AuthMode): String = when (authMode) {
    AuthMode.SignIn -> "¿No tenés cuenta? Crear cuenta"
    AuthMode.SignUp -> "¿Ya tenés cuenta? Ingresar"
}

internal fun authModeShowsPasswordReset(authMode: AuthMode): Boolean =
    authMode == AuthMode.SignIn

internal fun authModeSwitchEnabled(authBusy: Boolean): Boolean = !authBusy

internal fun visibleAuthError(
    error: String?,
    authMode: AuthMode,
    errorOwner: LoginErrorOwner?,
): String? = when {
    error == null -> null
    errorOwner == LoginErrorOwner.Shared -> error
    errorOwner == LoginErrorOwner.SignIn && authMode == AuthMode.SignIn -> error
    errorOwner == LoginErrorOwner.SignUp && authMode == AuthMode.SignUp -> error
    errorOwner == null && authMode == AuthMode.SignIn -> error
    else -> null
}

internal fun authModeBackHandlerEnabled(authMode: AuthMode, authBusy: Boolean): Boolean =
    authMode == AuthMode.SignUp && !authBusy

internal fun authModeAfterBack(currentMode: AuthMode, authBusy: Boolean): AuthMode = when {
    authModeBackHandlerEnabled(currentMode, authBusy) -> AuthMode.SignIn
    else -> currentMode
}

internal fun switchedAuthMode(currentMode: AuthMode, authBusy: Boolean): AuthMode = when {
    authBusy -> currentMode
    currentMode == AuthMode.SignIn -> AuthMode.SignUp
    else -> AuthMode.SignIn
}

internal fun submitAuthMode(
    authMode: AuthMode,
    email: String,
    password: String,
    rememberCredentials: Boolean,
    onSignIn: (email: String, password: String, rememberCredentials: Boolean) -> Unit,
    onSignUp: (email: String, password: String, rememberCredentials: Boolean) -> Unit,
) {
    when (authMode) {
        AuthMode.SignIn -> onSignIn(email, password, rememberCredentials)
        AuthMode.SignUp -> onSignUp(email, password, rememberCredentials)
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
): Boolean =
    !loginLoading &&
        !googleLoading &&
        !passwordResetLoading &&
        cooldownRemainingSeconds <= 0L

internal fun googleSignInButtonText(googleLoading: Boolean): String =
    if (googleLoading) "Conectando con Google..." else "Continuar con Google"

internal fun loginHeaderVisible(compactForIme: Boolean): Boolean = !compactForIme

internal const val rememberCredentialsLabel = "Recordar credenciales"

internal fun defaultRememberCredentials(): Boolean = false

internal const val LoginEmailFieldTag = "login_email_field"
internal const val LoginPasswordFieldTag = "login_password_field"

internal val LoginEmailContentType: ContentType =
    ContentType.Username + ContentType.EmailAddress

internal val LoginPasswordContentType: ContentType =
    ContentType.Password

internal fun loginEmailContentType(): ContentType = LoginEmailContentType

internal fun loginPasswordContentType(): ContentType = LoginPasswordContentType
