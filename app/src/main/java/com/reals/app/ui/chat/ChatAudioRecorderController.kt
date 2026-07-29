package com.reals.app.ui.chat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val CHAT_AUDIO_MIME_TYPE = "audio/mp4"
internal const val CHAT_AUDIO_FILE_EXTENSION = "m4a"
internal const val DEFAULT_CHAT_AUDIO_MAX_DURATION_MILLIS = 60_000L
internal const val DEFAULT_CHAT_AUDIO_MAX_FILE_SIZE_BYTES = 2_097_152L
internal const val MIN_CHAT_AUDIO_DURATION_MILLIS = 1_000L

internal data class LocalChatAudioDraft(
    val filePath: String,
    val clientMessageId: String,
    val durationMillis: Long,
    val sizeBytes: Long,
)

internal enum class ChatAudioStopSource {
    Manual,
    MaxDuration,
    MaxFileSize,
    Lifecycle,
    Cancel,
}

internal sealed interface ChatAudioRecorderResult {
    data object Started : ChatAudioRecorderResult
    data class Ready(
        val draft: LocalChatAudioDraft,
        val stopSource: ChatAudioStopSource,
    ) : ChatAudioRecorderResult
    data class Failed(val message: String) : ChatAudioRecorderResult
    data object Cancelled : ChatAudioRecorderResult
}

internal class ChatAudioRecorderController(
    private val context: Context,
    private val recorderFactory: ChatAudioRecorderEngineFactory = AndroidChatAudioRecorderEngineFactory,
    private val durationReader: (File) -> Long? = ::readableAudioDurationMillis,
) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var state: RecorderState = RecorderState.Idle
    private val sessionGeneration = AtomicLong(0L)
    private var recorder: ChatAudioRecorderEngine? = null
    private var outputFile: File? = null
    private var startedAtElapsedMillis: Long = 0L

    suspend fun start(
        maxDurationMillis: Long,
        maxFileSizeBytes: Long,
        onLimitReached: (ChatAudioStopSource) -> Unit,
    ): ChatAudioRecorderResult = mutex.withLock {
        releaseLocked(deleteOutput = true, stopSource = ChatAudioStopSource.Cancel)
        state = RecorderState.Starting
        val generation = sessionGeneration.incrementAndGet()
        val file = newOutputFile()
        outputFile = file
        val createdRecorder = recorderFactory.create(context)
        recorder = createdRecorder
        return@withLock try {
            withContext(Dispatchers.IO) {
                createdRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                createdRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                createdRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                createdRecorder.setAudioChannels(1)
                createdRecorder.setAudioSamplingRate(44_100)
                createdRecorder.setAudioEncodingBitRate(64_000)
                createdRecorder.setMaxDuration(maxDurationMillis.toInt())
                createdRecorder.setMaxFileSize(maxFileSizeBytes)
                createdRecorder.setOutputFile(file.absolutePath)
                createdRecorder.setOnInfoListener { what ->
                    if (generation != sessionGeneration.get()) return@setOnInfoListener
                    when (what) {
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ->
                            onLimitReached(ChatAudioStopSource.MaxDuration)
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ->
                            onLimitReached(ChatAudioStopSource.MaxFileSize)
                    }
                }
                createdRecorder.prepare()
                createdRecorder.start()
            }
            if (generation != sessionGeneration.get()) {
                runCatching {
                    createdRecorder.reset()
                    createdRecorder.release()
                }
                if (recorder === createdRecorder) recorder = null
                if (outputFile == file) outputFile = null
                file.delete()
                state = RecorderState.Idle
                startedAtElapsedMillis = 0L
                debugRecorder(
                    generation = generation,
                    maxDurationMillis = maxDurationMillis,
                    maxFileSizeBytes = maxFileSizeBytes,
                    message = "stale start ignored",
                )
                return@withLock ChatAudioRecorderResult.Cancelled
            }
            startedAtElapsedMillis = SystemClock.elapsedRealtime()
            state = RecorderState.Recording
            debugRecorder(
                generation = generation,
                maxDurationMillis = maxDurationMillis,
                maxFileSizeBytes = maxFileSizeBytes,
                message = "start success",
            )
            ChatAudioRecorderResult.Started
        } catch (exception: CancellationException) {
            releaseLocked(deleteOutput = true, stopSource = ChatAudioStopSource.Cancel)
            throw exception
        } catch (exception: Exception) {
            debugRecorder(
                generation = generation,
                maxDurationMillis = maxDurationMillis,
                maxFileSizeBytes = maxFileSizeBytes,
                message = "start failure ${exception::class.simpleName}",
            )
            releaseLocked(deleteOutput = true, stopSource = ChatAudioStopSource.Cancel)
            ChatAudioRecorderResult.Failed("No pudimos iniciar la grabación.")
        }
    }

    suspend fun stop(
        maxDurationMillis: Long,
        maxFileSizeBytes: Long,
        source: ChatAudioStopSource = ChatAudioStopSource.Manual,
    ): ChatAudioRecorderResult = mutex.withLock {
        val file = outputFile
        val activeRecorder = recorder
        if (file == null || activeRecorder == null || state != RecorderState.Recording) {
            return@withLock ChatAudioRecorderResult.Cancelled
        }
        state = RecorderState.Stopping
        val generation = sessionGeneration.get()
        val uiElapsedMillis = (SystemClock.elapsedRealtime() - startedAtElapsedMillis).coerceAtLeast(0L)
        try {
            withContext(Dispatchers.IO) {
                activeRecorder.stop()
                activeRecorder.release()
            }
            recorder = null
            outputFile = null
            startedAtElapsedMillis = 0L
            state = RecorderState.Idle
            validateDraft(
                file = file,
                maxDurationMillis = maxDurationMillis,
                maxFileSizeBytes = maxFileSizeBytes,
                generation = generation,
                stopSource = source,
                uiElapsedMillis = uiElapsedMillis,
            )
        } catch (exception: CancellationException) {
            releaseLocked(deleteOutput = true, stopSource = source)
            throw exception
        } catch (exception: RuntimeException) {
            debugRecorder(
                generation = generation,
                maxDurationMillis = maxDurationMillis,
                maxFileSizeBytes = maxFileSizeBytes,
                stopSource = source,
                uiElapsedMillis = uiElapsedMillis,
                message = "stop failure ${exception::class.simpleName}",
            )
            releaseLocked(deleteOutput = true, stopSource = source)
            ChatAudioRecorderResult.Failed("La grabación fue demasiado corta o no se pudo guardar.")
        }
    }

    suspend fun cancel(source: ChatAudioStopSource = ChatAudioStopSource.Cancel) {
        mutex.withLock {
            releaseLocked(deleteOutput = true, stopSource = source)
        }
    }

    fun invalidateAndReleaseAsync(
        deleteOutput: Boolean = true,
        source: ChatAudioStopSource = ChatAudioStopSource.Lifecycle,
    ) {
        val generation = sessionGeneration.incrementAndGet()
        cleanupScope.launch {
            mutex.withLock {
                releaseLocked(
                    deleteOutput = deleteOutput,
                    stopSource = source,
                    incrementGeneration = false,
                    loggedGeneration = generation,
                )
            }
        }
    }

    suspend fun release(deleteOutput: Boolean = true) {
        mutex.withLock {
            releaseLocked(deleteOutput = deleteOutput, stopSource = ChatAudioStopSource.Lifecycle)
        }
    }

    suspend fun cleanStaleDraftFiles(activeFilePaths: Set<String> = emptySet()) {
        withContext(Dispatchers.IO) {
            val cutoffMillis = System.currentTimeMillis() - STALE_DRAFT_MAX_AGE_MILLIS
            draftsDirectory().listFiles()
                ?.filter { file ->
                    file.isFile &&
                        file.extension == CHAT_AUDIO_FILE_EXTENSION &&
                        file.absolutePath !in activeFilePaths &&
                        file.lastModified() < cutoffMillis
                }
                ?.forEach { file -> runCatching { file.delete() } }
        }
    }

    private suspend fun validateDraft(
        file: File,
        maxDurationMillis: Long,
        maxFileSizeBytes: Long,
        generation: Long,
        stopSource: ChatAudioStopSource,
        uiElapsedMillis: Long,
    ): ChatAudioRecorderResult = withContext(Dispatchers.IO) {
        val fileSize = file.length()
        val metadataDurationMillis = durationReader(file)
        val failure = when {
            !file.isFile || fileSize <= 0L -> "La grabación quedó vacía."
            fileSize > maxFileSizeBytes -> "La grabación supera el tamaño permitido."
            metadataDurationMillis == null || metadataDurationMillis <= 0L ->
                "La grabación no tiene audio válido."
            metadataDurationMillis < MIN_CHAT_AUDIO_DURATION_MILLIS ->
                "La grabación quedó demasiado corta. Intentá nuevamente."
            metadataDurationMillis > maxDurationMillis ->
                "La grabación supera la duración permitida."
            else -> null
        }
        debugRecorder(
            generation = generation,
            maxDurationMillis = maxDurationMillis,
            maxFileSizeBytes = maxFileSizeBytes,
            stopSource = stopSource,
            uiElapsedMillis = uiElapsedMillis,
            finalFileSize = fileSize,
            metadataDurationMillis = metadataDurationMillis,
            validationResult = failure ?: "accepted",
            message = "validation",
        )
        if (failure != null) {
            file.delete()
            ChatAudioRecorderResult.Failed(failure)
        } else {
            ChatAudioRecorderResult.Ready(
                LocalChatAudioDraft(
                    filePath = file.absolutePath,
                    clientMessageId = UUID.randomUUID().toString(),
                    durationMillis = metadataDurationMillis ?: 0L,
                    sizeBytes = fileSize,
                ),
                stopSource = stopSource,
            )
        }
    }

    private fun releaseLocked(
        deleteOutput: Boolean,
        stopSource: ChatAudioStopSource,
        incrementGeneration: Boolean = true,
        loggedGeneration: Long? = null,
    ) {
        val generation = loggedGeneration ?: if (incrementGeneration) {
            sessionGeneration.incrementAndGet()
        } else {
            sessionGeneration.get()
        }
        val file = outputFile
        runCatching {
            recorder?.reset()
            recorder?.release()
        }
        recorder = null
        outputFile = null
        startedAtElapsedMillis = 0L
        state = RecorderState.Idle
        if (deleteOutput) {
            runCatching { file?.delete() }
        }
        debugRecorder(
            generation = generation,
            maxDurationMillis = null,
            maxFileSizeBytes = null,
            stopSource = stopSource,
            message = "release",
        )
    }

    private fun newOutputFile(): File {
        val directory = draftsDirectory().apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.$CHAT_AUDIO_FILE_EXTENSION")
    }

    private fun draftsDirectory(): File = File(context.noBackupFilesDir, DRAFT_DIRECTORY_NAME)

    private fun debugRecorder(
        generation: Long,
        maxDurationMillis: Long?,
        maxFileSizeBytes: Long?,
        stopSource: ChatAudioStopSource? = null,
        uiElapsedMillis: Long? = null,
        finalFileSize: Long? = null,
        metadataDurationMillis: Long? = null,
        validationResult: String? = null,
        message: String,
    ) {
        if (!isDebugLoggable()) return
        Log.d(
            TAG,
            "recording generation=$generation maxDurationMillis=$maxDurationMillis " +
                "maxFileSizeBytes=$maxFileSizeBytes stopSource=$stopSource " +
                "uiElapsedMillis=$uiElapsedMillis finalFileSize=$finalFileSize " +
                "metadataDurationMillis=$metadataDurationMillis validationResult=$validationResult " +
                "message=$message"
        )
    }

    private enum class RecorderState {
        Idle,
        Starting,
        Recording,
        Stopping,
    }

    private companion object {
        private const val TAG = "ChatAudioRecorder"
        private const val DRAFT_DIRECTORY_NAME = "chat-audio-drafts"
        private const val STALE_DRAFT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L

        private fun isDebugLoggable(): Boolean =
            runCatching { Log.isLoggable(TAG, Log.DEBUG) }.getOrDefault(false)
    }
}

internal interface ChatAudioRecorderEngine {
    fun setAudioSource(source: Int)
    fun setOutputFormat(format: Int)
    fun setAudioEncoder(encoder: Int)
    fun setAudioChannels(channels: Int)
    fun setAudioSamplingRate(samplingRate: Int)
    fun setAudioEncodingBitRate(bitRate: Int)
    fun setMaxDuration(maxDurationMillis: Int)
    fun setMaxFileSize(maxFileSizeBytes: Long)
    fun setOutputFile(path: String)
    fun setOnInfoListener(listener: (what: Int) -> Unit)
    fun prepare()
    fun start()
    fun stop()
    fun reset()
    fun release()
}

internal fun interface ChatAudioRecorderEngineFactory {
    fun create(context: Context): ChatAudioRecorderEngine
}

private object AndroidChatAudioRecorderEngineFactory : ChatAudioRecorderEngineFactory {
    override fun create(context: Context): ChatAudioRecorderEngine =
        AndroidChatAudioRecorderEngine(createMediaRecorder(context))

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}

private class AndroidChatAudioRecorderEngine(
    private val recorder: MediaRecorder,
) : ChatAudioRecorderEngine {
    override fun setAudioSource(source: Int) = recorder.setAudioSource(source)
    override fun setOutputFormat(format: Int) = recorder.setOutputFormat(format)
    override fun setAudioEncoder(encoder: Int) = recorder.setAudioEncoder(encoder)
    override fun setAudioChannels(channels: Int) = recorder.setAudioChannels(channels)
    override fun setAudioSamplingRate(samplingRate: Int) = recorder.setAudioSamplingRate(samplingRate)
    override fun setAudioEncodingBitRate(bitRate: Int) = recorder.setAudioEncodingBitRate(bitRate)
    override fun setMaxDuration(maxDurationMillis: Int) = recorder.setMaxDuration(maxDurationMillis)
    override fun setMaxFileSize(maxFileSizeBytes: Long) = recorder.setMaxFileSize(maxFileSizeBytes)
    override fun setOutputFile(path: String) = recorder.setOutputFile(path)
    override fun setOnInfoListener(listener: (what: Int) -> Unit) {
        recorder.setOnInfoListener { _, what, _ -> listener(what) }
    }
    override fun prepare() = recorder.prepare()
    override fun start() = recorder.start()
    override fun stop() = recorder.stop()
    override fun reset() = recorder.reset()
    override fun release() = recorder.release()
}

private fun readableAudioDurationMillis(file: File): Long? {
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
