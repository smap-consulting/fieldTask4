package org.odk.collect.audiorecorder.recording.internal

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.odk.collect.async.Scheduler
import org.odk.collect.audiorecorder.AudioRecorderDependencyModule
import org.odk.collect.audiorecorder.R
import org.odk.collect.audiorecorder.recorder.Output
import org.odk.collect.audiorecorder.recorder.Recorder
import org.odk.collect.audiorecorder.setupDependencies
import org.odk.collect.audiorecorder.support.FakeRecorder
import org.odk.collect.testshared.FakeScheduler
import org.robolectric.Robolectric.buildService
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import java.io.File
import java.lang.Exception

@RunWith(RobolectricTestRunner::class)
class AudioRecorderServiceTest {

    private val application: RobolectricApplication by lazy { ApplicationProvider.getApplicationContext() }
    private val recorder = FakeRecorder()
    private val scheduler = FakeScheduler()
    private val recordingRepository = RecordingRepository()

    private var serviceInstance: ServiceController<AudioRecorderService>? = null

    @Before
    fun setup() {
        application.setupDependencies(
            object : AudioRecorderDependencyModule() {
                override fun providesRecorder(cacheDir: File): Recorder {
                    return recorder
                }

                override fun providesScheduler(application: Application): Scheduler {
                    return scheduler
                }

                override fun providesRecordingRepository(): RecordingRepository {
                    return recordingRepository
                }
            }
        )
    }

    @Test
    fun startAction_startsInForegroundWithNotification() {
        val service = buildService(AudioRecorderService::class.java, createStartIntent("123"))
            .create()
            .startCommand(0, 0)

        val notification = shadowOf(service.get()).lastForegroundNotification
        assertThat(notification, not(nullValue()))
        assertThat(shadowOf(notification).contentTitle, equalTo(application.getString(R.string.recording)))
        assertThat(shadowOf(notification.contentIntent).savedIntent.component?.className, equalTo(ReturnToAppActivity::class.qualifiedName))
    }

    @Test
    fun startAction_startsRecorder() {
        startAction("123")
        assertThat(recorder.isRecording(), equalTo(true))
    }

    @Test
    fun startAction_whenRecordingInProgress_doesNothing() {
        val intent = createStartIntent("456")

        startService(intent)
        assertThat(recorder.isRecording(), equalTo(true))

        startService(intent)
        assertThat(recorder.isRecording(), equalTo(true))
        assertThat(recorder.recordings.size, equalTo(1))
    }

    @Test
    fun startAction_updatesDurationOnNotificationEverySecond() {
        val service = startAction("456")

        val notificationId = shadowOf(service.get()).lastForegroundNotificationId
        val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)

        scheduler.runForeground(0)
        assertThat(shadowOf(shadowNotificationManager.getNotification(notificationId)).contentText, equalTo("00:00"))

        scheduler.runForeground(500)
        assertThat(shadowOf(shadowNotificationManager.getNotification(notificationId)).contentText, equalTo("00:00"))

        scheduler.runForeground(1000)
        assertThat(shadowOf(shadowNotificationManager.getNotification(notificationId)).contentText, equalTo("00:01"))

        scheduler.runForeground(2000)
        assertThat(shadowOf(shadowNotificationManager.getNotification(notificationId)).contentText, equalTo("00:02"))
    }

    @Test
    fun startAction_whenRecorderStartThrowsException_stopsSelf() {
        recorder.failOnStart(Exception())

        val service = startService(createStartIntent("123"))
        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }

    @Test
    fun stopAction_stopsSelf() {
        startAction("123")
        val service = stopAction()

        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }

    @Test
    fun stopAction_stopsUpdates() {
        startAction("123")
        stopAction()

        assertThat(scheduler.isRepeatRunning(), equalTo(false))
    }

    @Test
    fun stopAction_stopsRecorder() {
        startAction("123")
        stopAction()

        assertThat(recorder.isRecording(), equalTo(false))
    }

    @Test
    fun stopAction_beforeStart_doesNothing() {
        val service = stopAction()

        val notification = shadowOf(service.get()).lastForegroundNotification
        assertThat(notification, nullValue())
    }

    @Test
    fun cleanUpAction_whileRecording_cancelsRecorder() {
        startAction("123")

        val cancelIntent = Intent(application, AudioRecorderService::class.java)
        cancelIntent.action = AudioRecorderService.ACTION_CLEAN_UP
        startService(cancelIntent)

        assertThat(recorder.isRecording(), equalTo(false))
        assertThat(recorder.wasCancelled(), equalTo(true))
    }

    @Test
    fun cleanUpAction_whileRecording_stopsUpdates() {
        startAction("123")

        val cancelIntent = Intent(application, AudioRecorderService::class.java)
        cancelIntent.action = AudioRecorderService.ACTION_CLEAN_UP
        startService(cancelIntent)

        assertThat(scheduler.isRepeatRunning(), equalTo(false))
    }

    @Test
    fun cleanUpAction_whileRecording_stopsSelf() {
        startAction("123")

        val cancelIntent = Intent(application, AudioRecorderService::class.java)
        cancelIntent.action = AudioRecorderService.ACTION_CLEAN_UP
        val service = startService(cancelIntent)

        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }

    @Test
    fun cleanUpAction_afterRecording_stopsSelf() {
        startAction("123")
        stopAction()

        val cancelIntent = Intent(application, AudioRecorderService::class.java)
        cancelIntent.action = AudioRecorderService.ACTION_CLEAN_UP
        val service = startService(cancelIntent)

        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }

    @Test
    fun whenUserKillsAppFromTaskManager_cancelsRecorder_stopsSelf() {
        val service = startService(createStartIntent("123"))
        service.get().onTaskRemoved(Intent())

        assertThat(recorder.isRecording(), equalTo(false))
        assertThat(recorder.wasCancelled(), equalTo(true))
        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }

    @Test
    fun pauseAction_pausesRecorder() {
        startAction("123")
        pauseAction()

        assertThat(recorder.paused, equalTo(true))
    }

    @Test
    fun pauseAction_stopsUpdates() {
        startAction("123")
        pauseAction()

        assertThat(scheduler.isRepeatRunning(), equalTo(false))
    }

    @Test
    fun pauseAction_andThenResumeAction_resumesRecorder() {
        startAction("123")
        pauseAction()
        resumeAction()

        assertThat(recorder.paused, equalTo(false))
    }

    @Test
    fun pauseAction_andThenResumeAction_startsUpdates() {
        startAction("123")
        pauseAction()
        resumeAction()

        assertThat(scheduler.isRepeatRunning(), equalTo(true))
    }

    @Test
    fun pauseAction_afterStop_doesNothing() {
        startAction("123")
        stopAction()
        pauseAction()

        assertThat(recordingRepository.currentSession.value!!.paused, equalTo(false))
    }

    @Test
    fun resumeAction_afterStop_doesNothing() {
        startAction("123")
        stopAction()
        resumeAction()

        assertThat(scheduler.isRepeatRunning(), equalTo(false))
    }

    private fun pauseAction() {
        val pauseIntent = Intent(application, AudioRecorderService::class.java)
        pauseIntent.action = AudioRecorderService.ACTION_PAUSE
        startService(pauseIntent)
    }

    private fun stopAction(): ServiceController<AudioRecorderService> {
        val stopIntent = Intent(application, AudioRecorderService::class.java)
        stopIntent.action = AudioRecorderService.ACTION_STOP
        return startService(stopIntent)
    }

    private fun startAction(sessionId: String): ServiceController<AudioRecorderService> {
        return startService(createStartIntent(sessionId))
    }

    private fun resumeAction() {
        val resumeIntent = Intent(application, AudioRecorderService::class.java)
        resumeIntent.action = AudioRecorderService.ACTION_RESUME
        startService(resumeIntent)
    }

    private fun createStartIntent(sessionId: String): Intent {
        val intent = Intent(application, AudioRecorderService::class.java)
        intent.action = AudioRecorderService.ACTION_START
        intent.putExtra(AudioRecorderService.EXTRA_SESSION_ID, sessionId)
        intent.putExtra(AudioRecorderService.EXTRA_OUTPUT, Output.AAC)
        return intent
    }

    private fun startService(intent: Intent): ServiceController<AudioRecorderService> {
        return serviceInstance.let {
            if (it == null) {
                val serviceController = buildService(AudioRecorderService::class.java, intent)
                    .create()
                    .startCommand(0, 0)
                serviceInstance = serviceController
                serviceController
            } else {
                it.withIntent(intent)
                    .startCommand(0, 0)
            }
        }
    }
}
