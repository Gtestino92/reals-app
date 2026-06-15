package com.reals.app.ui.matchmaking

import androidx.compose.runtime.Composable
import com.reals.app.ui.common.FeedbackCard
import com.reals.app.ui.common.FeedbackTone

@Composable
internal fun ErrorFeedback(title: String, message: String) {
    FeedbackCard(title = title, message = message, tone = FeedbackTone.Error)
}

@Composable
internal fun SuccessFeedback(message: String) {
    FeedbackCard(title = "Listo", message = message, tone = FeedbackTone.Success)
}
