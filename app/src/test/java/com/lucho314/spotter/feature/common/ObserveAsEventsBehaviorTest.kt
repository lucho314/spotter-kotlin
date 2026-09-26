package com.lucho314.spotter.feature.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ObserveAsEvents] itself is a `@Composable`, and this project has no Compose UI test
 * infrastructure yet (only Robolectric/JVM unit tests elsewhere). These tests instead exercise the
 * exact mechanism it wraps - [Lifecycle.repeatOnLifecycle] collecting a `Channel`-backed flow -
 * directly against a real [LifecycleRegistry]. That mechanism is precisely what fixes the review
 * bug: a plain `LaunchedEffect(Unit) { channel.collect {...} }` would *receive* (and thus lose,
 * once its resulting action is itself guarded by `dropUnlessResumed`) an event sent while the
 * screen is stopped; `repeatOnLifecycle(STARTED)` must not - it should leave the event sitting in
 * the channel's buffer until collection resumes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObserveAsEventsBehaviorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `an event sent while stopped is buffered and delivered once collection resumes`() = runTest {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.CREATED // below STARTED: "backgrounded"
        val channel = Channel<String>(Channel.BUFFERED)
        val received = mutableListOf<String>()

        val collectorJob = launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                channel.receiveAsFlow().collect { received += it }
            }
        }
        runCurrent()
        assertThat(received).isEmpty() // below STARTED: repeatOnLifecycle hasn't launched the block yet

        channel.send("routines-created") // e.g. TemplateDetailViewModel emitting mid-adoption
        runCurrent()
        assertThat(received).isEmpty() // not yet collected - but sitting untouched in the channel, not lost

        owner.registry.currentState = Lifecycle.State.STARTED // "user returns to the screen"
        runCurrent()
        assertThat(received).containsExactly("routines-created") // delivered, not dropped

        collectorJob.cancel()
    }

    @Test
    fun `an event sent while already started is delivered immediately`() = runTest {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.STARTED
        val channel = Channel<String>(Channel.BUFFERED)
        val received = mutableListOf<String>()

        val collectorJob = launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                channel.receiveAsFlow().collect { received += it }
            }
        }
        runCurrent()

        channel.send("saved")
        runCurrent()
        assertThat(received).containsExactly("saved")

        collectorJob.cancel()
    }
}
