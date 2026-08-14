package com.reals.app.ui.chat

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reals.app.R
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.Chat
import com.reals.app.domain.model.ChatAudioPolicy
import com.reals.app.domain.model.ChatAudioUnavailableReason
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.ChatAudioDraftUiState
import com.reals.app.ui.root.ChatAudioUploadUiState
import com.reals.app.ui.theme.LocalRealsDarkTheme
import com.reals.app.ui.theme.RealsColors
import com.reals.app.ui.theme.RealsRadii
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal data class ChatAudioComposerPresentation(
    val draft: String,
    val textState: MessageComposerUiState,
    val audioState: ChatAudioComposerUiState,
    val audioDraft: ChatAudioDraftUiState?,
    val uploadState: ChatAudioUploadUiState,
    val localAudioError: String?,
    val localAudioInfo: String?,
    val recordingStartedAtMillis: Long?,
    val recordingOperationInFlight: Boolean,
    val playbackState: ChatAudioPlaybackUiState,
)

internal data class ChatAudioComposerCallbacks(
    val onDraftChange: (String) -> Unit,
    val onSendText: () -> Unit,
    val onStartRecording: () -> Unit,
    val onStopRecording: () -> Unit,
    val onSendRecording: () -> Unit,
    val onMaxDurationReached: () -> Unit,
    val onCancelRecording: () -> Unit,
    val onPlayDraft: (ChatAudioDraftUiState) -> Unit,
    val onPauseAudio: () -> Unit,
    val onDeleteDraft: () -> Unit,
    val onSendAudio: (ChatAudioDraftUiState) -> Boolean,
)

@Composable
internal fun MessageComposer(
    presentation: ChatAudioComposerPresentation,
    callbacks: ChatAudioComposerCallbacks,
) {
    val trayAppearance = chatComposerTrayAppearance()
    var recordingNowMillis by remember(presentation.recordingStartedAtMillis) {
        mutableStateOf(SystemClock.elapsedRealtime())
    }
    LaunchedEffect(presentation.recordingStartedAtMillis) {
        while (presentation.recordingStartedAtMillis != null) {
            delay(250.milliseconds)
            recordingNowMillis = SystemClock.elapsedRealtime()
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = trayAppearance.border,
        colors = CardDefaults.cardColors(
            containerColor = trayAppearance.containerColor,
            contentColor = trayAppearance.contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presentation.localAudioError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            presentation.localAudioInfo?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            presentation.uploadState.error?.let {
                ApiErrorFeedbackCard(it, ErrorContext.Chat)
            }
            presentation.textState.explanatoryCopy?.let { copy ->
                Text(
                    text = copy,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (presentation.recordingStartedAtMillis != null) {
                val elapsedMillis = (recordingNowMillis - presentation.recordingStartedAtMillis).coerceAtLeast(0L)
                LaunchedEffect(
                    elapsedMillis,
                    presentation.audioState.maxDurationMillis,
                    presentation.recordingOperationInFlight,
                ) {
                    if (
                        !presentation.recordingOperationInFlight &&
                        elapsedMillis >= presentation.audioState.maxDurationMillis
                    ) {
                        callbacks.onMaxDurationReached()
                    }
                }
                RecordingComposer(
                    elapsedMillis = elapsedMillis,
                    maxDurationMillis = presentation.audioState.maxDurationMillis,
                    controlsEnabled = !presentation.recordingOperationInFlight,
                    appearance = trayAppearance,
                    onStop = callbacks.onStopRecording,
                    onSend = callbacks.onSendRecording,
                    onCancel = callbacks.onCancelRecording,
                )
            } else {
                val audioDraft = presentation.audioDraft.takeUnless { presentation.uploadState.uploading }
                if (audioDraft == null) {
                    TextComposerRow(
                        presentation = presentation,
                        appearance = trayAppearance,
                        callbacks = callbacks,
                    )
                } else {
                    AudioDraftComposer(
                        draft = audioDraft,
                        canSendMessages = presentation.textState.canSendMessages,
                        uploadState = presentation.uploadState,
                        playbackState = presentation.playbackState,
                        appearance = trayAppearance,
                        onPlay = { callbacks.onPlayDraft(audioDraft) },
                        onPause = callbacks.onPauseAudio,
                        onDelete = callbacks.onDeleteDraft,
                        onSend = { callbacks.onSendAudio(audioDraft) },
                    )
                }
            }
        }
    }
}

private data class ChatComposerTrayAppearance(
    val containerColor: Color,
    val border: BorderStroke,
    val contentColor: Color,
    val metadataColor: Color,
    val placeholderColor: Color,
    val primaryActionContainerColor: Color,
    val primaryActionContentColor: Color,
    val secondaryActionContentColor: Color,
)

@Composable
private fun chatComposerTrayAppearance(): ChatComposerTrayAppearance {
    val darkTheme = LocalRealsDarkTheme.current
    return ChatComposerTrayAppearance(
        containerColor = if (darkTheme) {
            RealsColors.DarkSurface.copy(alpha = 0.96f)
        } else {
            RealsColors.Paper.copy(alpha = 0.96f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (darkTheme) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.66f)
            } else {
                RealsColors.SoftGold.copy(alpha = 0.52f)
            },
        ),
        contentColor = MaterialTheme.colorScheme.onSurface,
        metadataColor = MaterialTheme.colorScheme.onSurfaceVariant,
        placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
        primaryActionContainerColor = MaterialTheme.colorScheme.primary,
        primaryActionContentColor = MaterialTheme.colorScheme.onPrimary,
        secondaryActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextComposerRow(
    presentation: ChatAudioComposerPresentation,
    appearance: ChatComposerTrayAppearance,
    callbacks: ChatAudioComposerCallbacks,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = presentation.draft,
            onValueChange = callbacks.onDraftChange,
            placeholder = { Text("Mensaje") },
            enabled = presentation.textState.canEditDraft,
            minLines = 1,
            maxLines = 4,
            shape = RoundedCornerShape(RealsRadii.Row),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = appearance.contentColor,
                unfocusedTextColor = appearance.contentColor,
                disabledTextColor = appearance.metadataColor,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedPlaceholderColor = appearance.placeholderColor,
                unfocusedPlaceholderColor = appearance.placeholderColor,
                disabledPlaceholderColor = appearance.placeholderColor.copy(alpha = 0.56f),
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            trailingIcon = if (presentation.audioState.visible) {
                {
                    IconButton(
                        onClick = callbacks.onStartRecording,
                        enabled = presentation.audioState.startEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = appearance.secondaryActionContentColor,
                            disabledContentColor = appearance.secondaryActionContentColor.copy(alpha = 0.42f),
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = "Grabar audio",
                        )
                    }
                }
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )

        FilledIconButton(
            onClick = callbacks.onSendText,
            enabled = presentation.textState.sendButtonEnabled,
            shape = RoundedCornerShape(RealsRadii.Row),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = appearance.primaryActionContainerColor,
                contentColor = appearance.primaryActionContentColor,
                disabledContainerColor = appearance.primaryActionContainerColor.copy(alpha = 0.18f),
                disabledContentColor = appearance.metadataColor.copy(alpha = 0.48f),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Enviar",
            )
        }
    }

    if (
        presentation.audioState.visible &&
        !presentation.audioState.startEnabled &&
        presentation.audioState.disabledCopy != null
    ) {
        Text(
            text = presentation.audioState.disabledCopy,
            color = appearance.metadataColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecordingComposer(
    elapsedMillis: Long,
    maxDurationMillis: Long,
    controlsEnabled: Boolean,
    appearance: ChatComposerTrayAppearance,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Grabando ${formatRecordingElapsedDuration(elapsedMillis)} / ${formatAudioDuration(maxDurationMillis)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { (elapsedMillis.toFloat() / maxDurationMillis.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = appearance.primaryActionContainerColor,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCancel,
                enabled = controlsEnabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = appearance.secondaryActionContentColor,
                    disabledContentColor = appearance.secondaryActionContentColor.copy(alpha = 0.42f),
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Cancelar grabación",
                )
            }
            IconButton(
                onClick = onStop,
                enabled = controlsEnabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = appearance.secondaryActionContentColor,
                    disabledContentColor = appearance.secondaryActionContentColor.copy(alpha = 0.42f),
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = "Detener y revisar",
                )
            }
            FilledIconButton(
                onClick = onSend,
                enabled = controlsEnabled,
                shape = RoundedCornerShape(RealsRadii.Row),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = appearance.primaryActionContainerColor,
                    contentColor = appearance.primaryActionContentColor,
                    disabledContainerColor = appearance.primaryActionContainerColor.copy(alpha = 0.18f),
                    disabledContentColor = appearance.metadataColor.copy(alpha = 0.48f),
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = "Enviar audio",
                )
            }
        }
    }
}

@Composable
private fun AudioDraftComposer(
    draft: ChatAudioDraftUiState,
    canSendMessages: Boolean,
    uploadState: ChatAudioUploadUiState,
    playbackState: ChatAudioPlaybackUiState,
    appearance: ChatComposerTrayAppearance,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Boolean,
) {
    val actionState = audioDraftComposerActionState(
        canSendMessages = canSendMessages,
        uploadState = uploadState,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Audio listo para enviar",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        AudioPlaybackRow(
            key = "draft-${draft.clientMessageId}",
            durationMillis = draft.durationMillis,
            playbackState = playbackState,
            onPlay = onPlay,
            onPause = onPause,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDelete,
                enabled = actionState.deleteAvailable,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(RealsRadii.Row),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = appearance.secondaryActionContentColor,
                    disabledContentColor = appearance.secondaryActionContentColor.copy(alpha = 0.42f),
                ),
            ) {
                Text(if (uploadState.nonRetryable) "Borrar" else "Cancelar")
            }
            Button(
                onClick = { onSend() },
                enabled = actionState.sendAvailable,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(RealsRadii.Row),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appearance.primaryActionContainerColor,
                    contentColor = appearance.primaryActionContentColor,
                    disabledContainerColor = appearance.primaryActionContainerColor.copy(alpha = 0.18f),
                    disabledContentColor = appearance.metadataColor.copy(alpha = 0.48f),
                ),
            ) {
                Text(
                    if (uploadState.uploading) {
                        "Enviando..."
                    } else if (uploadState.error != null) {
                        "Reintentar"
                    } else {
                        "Enviar"
                    }
                )
            }
        }
    }
}

internal data class AudioDraftComposerActionState(
    val visible: Boolean,
    val playbackAvailable: Boolean,
    val deleteAvailable: Boolean,
    val sendAvailable: Boolean,
)

internal fun audioDraftComposerActionState(
    canSendMessages: Boolean,
    uploadState: ChatAudioUploadUiState,
): AudioDraftComposerActionState =
    AudioDraftComposerActionState(
        visible = true,
        playbackAvailable = true,
        deleteAvailable = !uploadState.uploading,
        sendAvailable = canSendMessages && !uploadState.uploading && !uploadState.nonRetryable,
    )

internal fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = when {
        durationMillis <= 0L -> 0L
        durationMillis < 1_000L -> 1L
        else -> durationMillis / 1_000L
    }
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal fun formatRecordingElapsedDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal data class MessageComposerUiState(
    val canSendMessages: Boolean,
    val canEditDraft: Boolean,
    val sendButtonEnabled: Boolean,
    val sendingMessage: Boolean,
    val explanatoryCopy: String?,
)

internal fun messageComposerUiState(
    canChat: Boolean,
    canSendMessages: Boolean,
    sendingMessage: Boolean,
    loadingChatAction: Boolean,
    draft: String,
    pausedCopy: String? = null,
): MessageComposerUiState {
    val canEditDraft = canSendMessages && !loadingChatAction
    return MessageComposerUiState(
        canSendMessages = canSendMessages,
        canEditDraft = canEditDraft,
        sendButtonEnabled = canSendMessages &&
            !sendingMessage &&
            !loadingChatAction &&
            draft.isNotBlank(),
        sendingMessage = sendingMessage,
        explanatoryCopy = when {
            !canChat -> "Este chat no está disponible para enviar mensajes."
            !canSendMessages -> pausedCopy ?: MUTUAL_EXIT_CONVERSATION_PAUSED_COPY
            else -> null
        },
    )
}

internal data class ChatAudioComposerUiState(
    val visible: Boolean,
    val startEnabled: Boolean,
    val disabledCopy: String?,
    val maxDurationMillis: Long,
)

internal fun chatAudioComposerUiState(
    chat: Chat?,
    audioPolicy: ChatAudioPolicy? = chat?.audioPolicy,
    canSendMessages: Boolean,
    sendingMessage: Boolean,
    audioUploading: Boolean,
    recordingActive: Boolean,
    loadingChatAction: Boolean,
): ChatAudioComposerUiState {
    val policy = audioPolicy
    val visible = policy != null &&
        policy.unavailableReason != ChatAudioUnavailableReason.FeatureDisabled
    val startEnabled = visible &&
        canSendMessages &&
        policy?.enabled == true &&
        !sendingMessage &&
        !audioUploading &&
        !recordingActive &&
        !loadingChatAction
    val policyUnavailable = visible && policy?.enabled != true
    return ChatAudioComposerUiState(
        visible = visible,
        startEnabled = startEnabled,
        disabledCopy = if (policyUnavailable) {
            audioUnavailableCopy(policy?.unavailableReason)
        } else {
            null
        },
        maxDurationMillis = policy?.maxDurationMillis ?: DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS,
    )
}

private fun audioUnavailableCopy(reason: ChatAudioUnavailableReason?): String = when (reason) {
    ChatAudioUnavailableReason.GuidanceRequired ->
        "Respondan la pregunta actual para habilitar audios."
    ChatAudioUnavailableReason.GuidanceNotAvailable ->
        "Los audios se habilitarán al avanzar en las preguntas."
    ChatAudioUnavailableReason.LimitReached ->
        "Ya enviaste el audio disponible en este chat."
    ChatAudioUnavailableReason.WaitingForBoth ->
        "El audio se habilita cuando ambas personas hayan ingresado."
    ChatAudioUnavailableReason.WaitingDelay ->
        "El audio todavía no está disponible."
    ChatAudioUnavailableReason.ChatNotWritable ->
        "Este chat no admite nuevos mensajes."
    ChatAudioUnavailableReason.FeatureDisabled ->
        "Los audios no están disponibles."
    is ChatAudioUnavailableReason.Unknown,
    null -> "El audio no está disponible en este momento."
}
