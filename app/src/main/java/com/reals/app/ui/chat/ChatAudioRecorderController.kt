package com.reals.app.ui.chat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.UUID

internal const val CHAT_AUDIO_MIME_TYPE = "audio/mp4"
internal const val CHAT_AUDIO_FILE_EXTENSION = "m4a"
internal const val DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS = 60_000L
internal const val DEFAULT_CHAT_AUDIO_MAX_FILE_SIZE_BYTES = 2_097_152L

internal data class LocalChatAudioDraft(
    val filePath: String,
    val clientMessageId: String,
    val durationMillis: Long,
    val sizeBytes: Long,
)

internal sealed interface ChatAudioRecorderResult {
    data object Started : ChatAudioRecorderResult
    data class Ready(val draft: LocalChatAudioDraft) : ChatAudioRecorderResult
    data class Failed(val message: String) : ChatAudioRecorderResult
    data object Cancelled : ChatAudioRecorderResult
}

internal class ChatAudioRecorderController(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    fun start(
        maxDurationMillis: Long,
        maxFileSizeBytes: Long,
        onLimitReached: () -> Unit,
    ): ChatAudioRecorderResult {
        release(deleteOutput = true)
        val directory = File(context.cacheDir, "chat-audio").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.$CHAT_AUDIO_FILE_EXTENSION")
        outputFile = file
        val createdRecorder = createMediaRecorder()
        recorder = createdRecorder
        return runCatching {
            createdRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            createdRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            createdRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            createdRecorder.setAudioChannels(1)
            createdRecorder.setAudioSamplingRate(44_100)
            createdRecorder.setAudioEncodingBitRate(64_000)
            createdRecorder.setMaxDuration(maxDurationMillis.toInt())
            createdRecorder.setMaxFileSize(maxFileSizeBytes)
            createdRecorder.setOutputFile(file.absolutePath)
            createdRecorder.setOnInfoListener { _, what, _ ->
                if (
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    onLimitReached()
                }
            }
            createdRecorder.prepare()
            createdRecorder.start()
            startedAtMillis = SystemClock.elapsedRealtime()
            ChatAudioRecorderResult.Started
        }.getOrElse {
            release(deleteOutput = true)
            ChatAudioRecorderResult.Failed("No pudimos iniciar la grabación.")
        }
    }

    fun stop(maxDurationMillis: Long, maxFileSizeBytes: Long): ChatAudioRecorderResult {
        val file = outputFile ?: return ChatAudioRecorderResult.Failed("No hay una grabación activa.")
        val activeRecorder = recorder ?: return ChatAudioRecorderResult.Failed("No hay una grabación activa.")
        val elapsedMillis = (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)
        return try {
            activeRecorder.stop()
            activeRecorder.release()
            recorder = null
            outputFile = null
            validateDraft(file, maxDurationMillis, maxFileSizeBytes, elapsedMillis)
        } catch (exception: RuntimeException) {
            release(deleteOutput = true)
            ChatAudioRecorderResult.Failed("La grabación fue demasiado corta o no se pudo guardar.")
        }
    }

    fun cancel() {
        release(deleteOutput = true)
    }

    fun release(deleteOutput: Boolean = true) {
        runCatching {
            recorder?.reset()
            recorder?.release()
        }
        recorder = null
        if (deleteOutput) {
            outputFile?.delete()
        }
        outputFile = null
        startedAtMillis = 0L
    }

    private fun validateDraft(
        file: File,
        maxDurationMillis: Long,
        maxFileSizeBytes: Long,
        elapsedMillis: Long,
    ): ChatAudioRecorderResult {
        if (!file.isFile || file.length() <= 0L) {
            file.delete()
            return ChatAudioRecorderResult.Failed("La grabación quedó vacía.")
        }
        if (file.length() > maxFileSizeBytes) {
            file.delete()
            return ChatAudioRecorderResult.Failed("La grabación supera el tamaño permitido.")
        }
        val durationMillis = readableDurationMillis(file) ?: elapsedMillis
        if (durationMillis <= 0L) {
            file.delete()
            return ChatAudioRecorderResult.Failed("La grabación no tiene audio válido.")
        }
        if (durationMillis > maxDurationMillis + 1_000L) {
            file.delete()
            return ChatAudioRecorderResult.Failed("La grabación supera la duración permitida.")
        }
        return ChatAudioRecorderResult.Ready(
            LocalChatAudioDraft(
                filePath = file.absolutePath,
                clientMessageId = UUID.randomUUID().toString(),
                durationMillis = durationMillis,
                sizeBytes = file.length(),
            )
        )
    }

    private fun readableDurationMillis(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (exception: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
