package com.reals.app.ui.matchmaking

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.SearchingDotsIndicator
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val SEARCHING_CHAT_TITLE = "Buscando chat"

private const val SEARCHING_CHAT_BODY = "Estamos buscando alguien compatible. Cuando encontremos una persona, vas a entrar al chat automaticamente."
@Composable
internal fun LoadingHomeStateScreen() {
    val pulse = rememberInfiniteTransition(label = "home-loading-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home-loading-dot-scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Text(
            text = "Cargando estado actual",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Estamos preparando tu estado actual.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SearchingChatScreen(
    title: String = SEARCHING_CHAT_TITLE,
    body: String = SEARCHING_CHAT_BODY,
    canCancelSearch: Boolean = true,
    homeError: ApiError?,
    accountDeleteLoading: Boolean,
    onPollHome: () -> Unit,
    onLeaveQueue: () -> Unit,
) {
    var dots by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var elapsedMillis = 0L

        while (true) {
            delay(500.milliseconds)
            elapsedMillis += 500
            dots = (dots + 1) % 4

            if (elapsedMillis >= 2_000) {
                elapsedMillis = 0
                onPollHome()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchingDotsIndicator()
        Row(
            modifier = Modifier.padding(top = 28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .height(48.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .height(72.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        homeError?.let {
            ApiErrorFeedbackCard(
                error = it,
                context = ErrorContext.Home,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(
            onClick = onLeaveQueue,
            enabled = canCancelSearch && !accountDeleteLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancelar busqueda")
        }
    }
}
