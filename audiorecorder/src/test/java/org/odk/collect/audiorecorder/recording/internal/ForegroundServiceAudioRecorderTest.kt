package org.odk.collect.audiorecorder.recording.internal

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.odk.collect.async.Scheduler
import org.odk.collect.audiorecorder.AudioRecorderDependencyModule
import org.odk.collect.audiorecorder.recorder.Output
import org.odk.collect.audiorecorder.recorder.Recorder
import org.odk.collect.audiorecorder.recording.AudioRecorder
import org.odk.collect.audiorecorder.recording.AudioRecorderFactory
import org.odk.collect.audiorecorder.recording.AudioRecorderTest
import org.odk.collect.audiorecorder.recording.MicInUseException
import org.odk.collect.audiorecorder.setupDependencies
import org.odk.collect.audiorecorder.support.FakeRecorder
import org.odk.collect.testshared.FakeScheduler
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class ForegroundServiceAudioRecorderTest : AudioRecorderTest() {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()
    private val application by lazy { getApplicationContext<RobolectricApplication>() }

    private val fakeRecorder = FakeRecorder()
    private val scheduler = FakeScheduler()

    override val viewModel: AudioRecorder by lazy {
        AudioRecorderFactory(application).create()
    }

    override fun runBackground() {
        while (shadowOf(application).peekNextStartedService() != null) {
            val serviceIntent = shadowOf(application).nextStartedService
            assertThat(serviceIntent.component?.className, equalTo(AudioRecorderService::class.qualifiedName))
            Robolectric.buildService(AudioRecorderService::class.java, serviceIntent)
                .create()
                .startCommand(0, 0)
        }
    }

    override fun getLastRecordedFile(): File? {
        return fakeRecorder.file
    }

    @Before
    fun setup() {
        application.setupDependencies(
            object : AudioRecorderDependencyModule() {
                override fun providesRecorder(cacheDir: File): Recorder {
                    return fakeRecorder
                }

                override fun providesScheduler(application: Application): Scheduler {
                    return scheduler
                }
            }
        )
    }

    @Test
    fun start_passesOutputToRecorder() {
        Output.values().forEach {
            viewModel.start("blah", it)
            viewModel.stop()
            runBackground()
            assertThat(fakeRecorder.output, equalTo(it))
        }
    }

    @Test
    fun start_incrementsDurationEverySecond() {
        viewModel.start("blah", Output.AAC)
        runBackground()

        val currentSession = viewModel.getCurrentSession()
        scheduler.runForeground(0)
        assertThat(currentSession.value?.duration, equalTo(0))

        scheduler.runForeground(500)
        assertThat(currentSession.value?.duration, equalTo(0))

        scheduler.runForeground(1000)
        assertThat(currentSession.value?.duration, equalTo(1000))
    }

    @Test
    fun start_updatesAmplitude() {
        viewModel.start("blah", Output.AAC)
        runBackground()

        val currentSession = viewModel.getCurrentSession()

        fakeRecorder.amplitude = 12
        scheduler.runForeground()
        assertThat(currentSession.value?.amplitude, equalTo(12))

        fakeRecorder.amplitude = 45
        scheduler.runForeground()
        assertThat(currentSession.value?.amplitude, equalTo(45))
    }

    @Test
    fun start_whenRecorderStartThrowsException_setsFailedToStartToException() {
        val exception = MicInUseException()
        fakeRecorder.failOnStart(exception)

        viewModel.start("blah", Output.AAC)
        runBackground()
        assertThat(viewModel.getCurrentSession().value?.failedToStart, equalTo(exception))
    }
}
