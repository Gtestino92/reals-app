package com.reals.app.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.toUserMessage
import com.reals.app.core.network.toUserTitle

/**
 * Full-screen error display that uses [ApiError.toUserTitle] and [ApiError.toUserMessage]
 * with proper [ErrorContext] for context-specific titles and descriptions.
 *
 * Use this instead of raw `FullScreenMessage` when you have an [ApiError] to display.
 */
@Composable
fun ApiErrorScreen(
    error: ApiError,
    context: ErrorContext = ErrorContext.General,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    FullScreenMessage(
        title = error.toUserTitle(context),
        body = error.toUserMessage(context),
        primaryActionLabel = if (onRetry != null) "Reintentar" else null,
        onPrimaryAction = onRetry,
        secondaryActionLabel = if (onDismiss != null) "Cerrar sesion" else null,
        onSecondaryAction = onDismiss,
    )
}
