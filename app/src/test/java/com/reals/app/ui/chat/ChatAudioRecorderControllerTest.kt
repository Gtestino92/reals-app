package com.reals.app.ui.chat

import android.content.ContextWrapper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatAudioRecorderControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stale startup after invalidation returns cancelled and releases file once`() = runTest {
        val prepareStarted = CountDownLatch(1)
        val releasePrepare = CountDownLatch(1)
        val engine = FakeRecorderEngine(
            onPrepare = {
                prepareStarted.countDown()
                releasePrepare.await(5, TimeUnit.SECONDS)
            },
        )
        val controller = controller(engine)

        val start = async(Dispatchers.Default) {
            controller.start(
                maxDurationMillis = 60_000L,
                maxFileSizeBytes = 2_097_152L,
                onLimitReached = {},
            )
        }
        assertTrue(prepareStarted.await(5, TimeUnit.SECONDS))

        controller.invalidateAndReleaseAsync()
        releasePrepare.countDown()

        assertEquals(ChatAudioRecorderResult.Cancelled, start.await())
        assertEquals(1, engine.releaseCount)
        assertFalse(engine.outputFile?.exists() == true)
    }

    @Test
    fun `duplicate cancel releases active recorder once`() = runTest {
        val engine = FakeRecorderEngine()
        val controller = controller(engine)

        assertEquals(
            ChatAudioRecorderResult.Started,
            controller.start(
                maxDurationMillis = 60_000L,
                maxFileSizeBytes = 2_097_152L,
                onLimitReached = {},
            ),
        )

        controller.cancel()
        controller.cancel()

        assertEquals(1, engine.releaseCount)
        assertFalse(engine.outputFile?.exists() == true)
    }

    @Test
    fun `duplicate stop produces one ready result and one release`() = runTest {
        val engine = FakeRecorderEngine()
        val controller = controller(engine)
        controller.start(
            maxDurationMillis = 60_000L,
            maxFileSizeBytes = 2_097_152L,
            onLimitReached = {},
        )

        val first = controller.stop(
            maxDurationMillis = 60_000L,
            maxFileSizeBytes = 2_097_152L,
        )
        val second = controller.stop(
            maxDurationMillis = 60_000L,
            maxFileSizeBytes = 2_097_152L,
        )

        assertTrue(first is ChatAudioRecorderResult.Ready)
        assertEquals(ChatAudioRecorderResult.Cancelled, second)
        assertEquals(1, engine.releaseCount)
    }

    private fun controller(engine: FakeRecorderEngine): ChatAudioRecorderController =
        ChatAudioRecorderController(
            context = object : ContextWrapper(null) {
                override fun getNoBackupFilesDir(): File = temporaryFolder.root
            },
            recorderFactory = ChatAudioRecorderEngineFactory { engine },
            durationReader = { 1_000L },
        )
}

private class FakeRecorderEngine(
    private val onPrepare: () -> Unit = {},
) : ChatAudioRecorderEngine {
    var releaseCount = 0
        private set
    var outputFile: File? = null
        private set

    override fun setAudioSource(source: Int) = Unit
    override fun setOutputFormat(format: Int) = Unit
    override fun setAudioEncoder(encoder: Int) = Unit
    override fun setAudioChannels(channels: Int) = Unit
    override fun setAudioSamplingRate(samplingRate: Int) = Unit
    override fun setAudioEncodingBitRate(bitRate: Int) = Unit
    override fun setMaxDuration(maxDurationMillis: Int) = Unit
    override fun setMaxFileSize(maxFileSizeBytes: Long) = Unit
    override fun setOutputFile(path: String) {
        outputFile = File(path).apply { writeBytes(byteArrayOf(1)) }
    }
    override fun setOnInfoListener(listener: (what: Int) -> Unit) = Unit
    override fun prepare() = onPrepare()
    override fun start() = Unit
    override fun stop() = Unit
    override fun reset() = Unit
    override fun release() {
        releaseCount += 1
    }
}
