package com.reals.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.time.backendInstantOrNull
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.AccountSuspension
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AccountSuspendedScreen(
    suspension: AccountSuspension,
    retrying: Boolean,
    retryError: ApiError?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    val canRetry = suspension is AccountSuspension.Temporary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = accountSuspensionTitle(suspension),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = accountSuspensionBody(suspension),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        retryError?.let {
            ApiErrorFeedbackCard(
                error = it,
                context = ErrorContext.Account,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canRetry) {
                Button(
                    enabled = !retrying,
                    onClick = onRetry,
                ) {
                    Text(if (retrying) "Reintentando..." else "Reintentar")
                }
                OutlinedButton(
                    enabled = !retrying,
                    onClick = onSignOut,
                ) {
                    Text("Cerrar sesión")
                }
            } else {
                Button(onClick = onSignOut) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}

internal fun accountSuspensionTitle(suspension: AccountSuspension): String =
    when (suspension) {
        is AccountSuspension.Temporary -> "Cuenta suspendida temporalmente"
        AccountSuspension.Permanent -> "Cuenta suspendida"
    }

internal fun accountSuspensionBody(
    suspension: AccountSuspension,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String = when (suspension) {
    is AccountSuspension.Temporary -> {
        val formattedExpiry = formatAccountSuspensionExpiresAt(suspension.expiresAt, zoneId, locale)
        if (formattedExpiry != null) {
            "Tu cuenta está suspendida hasta el $formattedExpiry. " +
                "Podrás volver a usar Reals cuando termine la suspensión."
        } else {
            "Tu cuenta está suspendida temporalmente. " +
                "Podrás volver a entrar cuando termine la suspensión."
        }
    }

    AccountSuspension.Permanent -> "Tu cuenta fue suspendida permanentemente."
}

internal fun formatAccountSuspensionExpiresAt(
    expiresAt: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.forLanguageTag("es-AR"),
): String? {
    val instant = backendInstantOrNull(expiresAt) ?: return null
    val dateTime = instant.atZone(zoneId)
    return dateTime.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' HH:mm", locale))
}
