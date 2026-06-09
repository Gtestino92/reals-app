package com.reals.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.network.toUserMessage
import com.reals.app.core.network.toUserTitle

enum class FeedbackTone {
    Error,
    Success,
    Info,
    Warning,
}

@Composable
fun ApiErrorFeedbackCard(
    error: ApiError,
    context: ErrorContext,
    modifier: Modifier = Modifier,
) {
    FeedbackCard(
        title = error.toUserTitle(context),
        message = error.toUserMessage(context),
        tone = FeedbackTone.Error,
        modifier = modifier,
    )
}

@Composable
fun FeedbackCard(
    title: String,
    message: String,
    tone: FeedbackTone,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor: Color
    val contentColor: Color
    when (tone) {
        FeedbackTone.Error -> {
            containerColor = colors.errorContainer
            contentColor = colors.onErrorContainer
        }
        FeedbackTone.Success -> {
            containerColor = colors.primaryContainer
            contentColor = colors.onPrimaryContainer
        }
        FeedbackTone.Info -> {
            containerColor = colors.secondaryContainer
            contentColor = colors.onSecondaryContainer
        }
        FeedbackTone.Warning -> {
            containerColor = colors.tertiaryContainer
            contentColor = colors.onTertiaryContainer
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}
