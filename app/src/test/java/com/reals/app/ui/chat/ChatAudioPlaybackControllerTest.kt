package com.reals.app.ui.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatAudioPlaybackControllerTest {
    @Test
    fun `stale prepared callback cannot start or mutate newer playback`() = runTest {
        val factory = FakePlayerFactory()
        val controller = ChatAudioPlaybackController(factory)

        controller.playRemote("A", "https://old.test/a", 7_000, this) { null }
        val playerA = factory.players.single()
        controller.playRemote("B", "https://old.test/b", 8_000, this) { null }
        val playerB = factory.players.last()

        playerA.prepared()

        assertFalse(playerA.started)
        assertTrue(playerA.released)
        assertEquals("B", controller.state.key)
        assertEquals(ChatAudioPlaybackPhase.Preparing, controller.state.phase)

        playerB.prepared()

        assertTrue(playerB.started)
        assertEquals("B", controller.state.key)
        assertEquals(ChatAudioPlaybackPhase.Playing, controller.state.phase)
    }

    @Test
    fun `stale error callback cannot fail active playback`() = runTest {
        val factory = FakePlayerFactory()
        val controller = ChatAudioPlaybackController(factory)

        controller.playRemote("A", "https://old.test/a", 7_000, this) { null }
        val playerA = factory.players.single()
        controller.playRemote("B", "https://old.test/b", 8_000, this) { null }
        val playerB = factory.players.last()

        playerA.error()
        playerB.prepared()

        assertEquals("B", controller.state.key)
        assertEquals(ChatAudioPlaybackPhase.Playing, controller.state.phase)
        assertTrue(playerB.started)
    }

    @Test
    fun `release while preparing prevents delayed start`() = runTest {
        val factory = FakePlayerFactory()
        val controller = ChatAudioPlaybackController(factory)

        controller.playRemote("A", "https://old.test/a", 7_000, this) { null }
        val playerA = factory.players.single()
        controller.release()
        playerA.prepared()

        assertFalse(playerA.started)
        assertEquals(ChatAudioPlaybackPhase.Idle, controller.state.phase)
    }

    @Test
    fun `pending refresh cannot replace newer playback`() = runTest {
        val factory = FakePlayerFactory()
        val controller = ChatAudioPlaybackController(factory)
        val refresh = CompletableDeferred<String?>()

        controller.playRemote("A", "https://old.test/a", 7_000, this) { refresh.await() }
        val playerA = factory.players.single()
        playerA.error()

        controller.playRemote("B", "https://old.test/b", 8_000, this) { null }
        refresh.complete("https://new.test/a")
        advanceUntilIdle()

        assertEquals("B", controller.state.key)
        assertEquals(ChatAudioPlaybackPhase.Preparing, controller.state.phase)
        assertEquals(2, factory.players.size)
    }

    @Test
    fun `pause and resume retain position for same audio`() = runTest {
        val factory = FakePlayerFactory()
        val controller = ChatAudioPlaybackController(factory)

        controller.playLocal("draft-1", "/tmp/audio.m4a", 7_000, this)
        val player = factory.players.single()
        player.currentPositionValue = 1_750
        player.prepared()
        controller.pause()
        controller.playLocal("draft-1", "/tmp/audio.m4a", 7_000, this)

        assertEquals(1_750, player.seekPosition)
        assertEquals(ChatAudioPlaybackPhase.Playing, controller.state.phase)
    }
}

private class FakePlayerFactory : ChatAudioPlayerFactory {
    val players = mutableListOf<FakePlayer>()

    override fun create(): ChatAudioPlayer =
        FakePlayer().also(players::add)
}

private class FakePlayer : ChatAudioPlayer {
    var preparedListener: ((ChatAudioPlayer) -> Unit)? = null
    var completionListener: ((ChatAudioPlayer) -> Unit)? = null
    var errorListener: ((ChatAudioPlayer, Int, Int) -> Boolean)? = null
    var started = false
    var released = false
    var seekPosition: Int? = null
    var currentPositionValue: Int = 0

    override val currentPosition: Int get() = currentPositionValue
    override fun configureForSpeech() = Unit
    override fun setDataSource(source: String) = Unit
    override fun setOnPreparedListener(listener: (ChatAudioPlayer) -> Unit) {
        preparedListener = listener
    }
    override fun setOnCompletionListener(listener: (ChatAudioPlayer) -> Unit) {
        completionListener = listener
    }
    override fun setOnErrorListener(listener: (ChatAudioPlayer, Int, Int) -> Boolean) {
        errorListener = listener
    }
    override fun prepareAsync() = Unit
    override fun start() {
        started = true
    }
    override fun pause() {
        started = false
    }
    override fun seekTo(positionMillis: Int) {
        seekPosition = positionMillis
    }
    override fun release() {
        released = true
    }
    fun prepared() {
        preparedListener?.invoke(this)
    }
    fun error() {
        errorListener?.invoke(this, 1, -1)
    }
}
