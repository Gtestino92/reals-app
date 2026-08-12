package com.reals.app.ui.matchmaking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.SearchingDotsIndicator
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val SEARCHING_CHAT_TITLE = "Buscando chat"

const val SEARCHING_CHAT_BODY = "Estamos buscando alguien compatible. Cuando encontremos una persona, vas a entrar al chat automaticamente."
@Composable
internal fun LoadingHomeStateScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchingDotsIndicator()
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
    if (canCancelSearch) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(HOME_POLL_INTERVAL_MILLIS.milliseconds)
                onPollHome()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchingDotsIndicator()
        Text(
            text = title,
            modifier = Modifier
                .padding(top = 56.dp)
                .fillMaxWidth(),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Cancelar búsqueda")
        }
    }
}
