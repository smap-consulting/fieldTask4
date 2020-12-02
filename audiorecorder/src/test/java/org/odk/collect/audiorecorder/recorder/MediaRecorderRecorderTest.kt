package org.odk.collect.audiorecorder.recorder

import android.media.MediaRecorder
import com.google.common.io.Files
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import java.io.File

class MediaRecorderRecorderTest {

    private val cacheDir = Files.createTempDir()
    private val mediaRecorder = FakeMediaRecorderWrapper()
    private val recorder = MediaRecorderRecorder(cacheDir) { mediaRecorder }

    @Test
    fun start_startsMediaRecorder() {
        recorder.start(Output.AAC)
        assertThat(mediaRecorder.hasStarted(), equalTo(true))
    }

    @Test
    fun start_withAAC_setsUpAACRecordingFromMic() {
        recorder.start(Output.AAC)

        assertThat(mediaRecorder.getAudioEncoder(), equalTo(MediaRecorder.AudioEncoder.AAC))
        assertThat(mediaRecorder.getAudioEncodingSampleRate(), equalTo(32000))
        assertThat(mediaRecorder.getAudioEncodingBitRate(), equalTo(64000))
        assertThat(mediaRecorder.getOutputFormat(), equalTo(MediaRecorder.OutputFormat.MPEG_4))

        assertThat(mediaRecorder.getAudioSource(), equalTo(MediaRecorder.AudioSource.MIC))
    }

    @Test
    fun start_withAMR_setsUpAMRRecordingFromMic() {
        recorder.start(Output.AMR)

        assertThat(mediaRecorder.getAudioEncoder(), equalTo(MediaRecorder.AudioEncoder.AMR_NB))
        assertThat(mediaRecorder.getAudioEncodingSampleRate(), equalTo(8000))
        assertThat(mediaRecorder.getAudioEncodingBitRate(), equalTo(12200))
        assertThat(mediaRecorder.getOutputFormat(), equalTo(MediaRecorder.OutputFormat.AMR_NB))

        assertThat(mediaRecorder.getAudioSource(), equalTo(MediaRecorder.AudioSource.MIC))
    }

    @Test
    fun start_createsAndRecordsToM4AFileInCacheDir() {
        recorder.start(Output.AAC)
        assertThat(mediaRecorder.getOutputFile()!!.parent, equalTo(cacheDir.absolutePath))
        assertThat(mediaRecorder.getOutputFile()!!.absolutePath, endsWith(".m4a"))
    }

    @Test
    fun start_createsAndRecordsToAMRFileInCacheDir() {
        recorder.start(Output.AMR)
        assertThat(mediaRecorder.getOutputFile()!!.parent, equalTo(cacheDir.absolutePath))
        assertThat(mediaRecorder.getOutputFile()!!.absolutePath, endsWith(".amr"))
    }

    @Test
    fun start_setsIsRecording_toTrue() {
        recorder.start(Output.AAC)
        assertThat(recorder.isRecording(), equalTo(true))
    }

    @Test
    fun recordingTwice_doesntUseSameOutputFile() {
        var mediaRecorder = FakeMediaRecorderWrapper()
        var recorder = MediaRecorderRecorder(cacheDir) { mediaRecorder }
        recorder.start(Output.AAC)
        val outputFile1 = mediaRecorder.getOutputFile()

        mediaRecorder = FakeMediaRecorderWrapper()
        recorder = MediaRecorderRecorder(cacheDir) { mediaRecorder }
        recorder.start(Output.AAC)
        val outputFile2 = mediaRecorder.getOutputFile()

        assertThat(outputFile1!!.absolutePath, not(equalTo(outputFile2!!.absolutePath)))
    }

    @Test
    fun stop_releasesMediaRecorder() {
        recorder.start(Output.AAC)
        recorder.stop()
        assertThat(mediaRecorder.isReleased(), equalTo(true))
    }

    @Test
    fun stop_returnsOutputFile() {
        recorder.start(Output.AAC)
        val file = recorder.stop()
        assertThat(file.absolutePath, equalTo(mediaRecorder.getOutputFile()!!.absolutePath))
    }

    @Test
    fun stop_setsIsRecording_toFalse() {
        recorder.start(Output.AAC)
        recorder.stop()
        assertThat(recorder.isRecording(), equalTo(false))
    }

    @Test
    fun cancel_releasesMediaRecorder() {
        recorder.start(Output.AAC)
        recorder.cancel()
        assertThat(mediaRecorder.isReleased(), equalTo(true))
    }

    @Test
    fun cancel_deletesOutputFile() {
        recorder.start(Output.AAC)
        recorder.cancel()
        assertThat(mediaRecorder.getOutputFile()!!.exists(), equalTo(false))
    }

    @Test
    fun cancel_setsIsRecording_toFalse() {
        recorder.start(Output.AAC)
        recorder.cancel()
        assertThat(recorder.isRecording(), equalTo(false))
    }

    @Test
    fun cancel_beforeStart_works() {
        recorder.cancel()
    }
}

private class FakeMediaRecorderWrapper : MediaRecorderWrapper {

    private var bitRate: Int? = null
    private var sampleRate: Int? = null
    private var file: File? = null
    private var audioSource: Int? = null
    private var outputFormat: Int? = null
    private var audioEncoder: Int? = null

    private var started: Boolean = false
    private var prepared: Boolean = false
    private var released: Boolean = false

    override fun setAudioSource(audioSource: Int) {
        if (prepared) {
            throw IllegalStateException("MediaRecorder already prepared!")
        }

        if (outputFormat != null) {
            throw IllegalStateException("Can't setup audio source after setting output form on MediaRecorder")
        }

        this.audioSource = audioSource
    }

    override fun setOutputFormat(outputFormat: Int) {
        if (prepared) {
            throw IllegalStateException("MediaRecorder already prepared!")
        }

        if (audioSource == null) {
            throw IllegalStateException("Need to set audio source before setting output format")
        }

        this.outputFormat = outputFormat
    }

    override fun setOutputFile(path: String) {
        if (prepared) {
            throw IllegalStateException("MediaRecorder already prepared!")
        }

        if (!File(path).exists()) {
            throw IllegalArgumentException("Path doesn't refer to created file!")
        }

        file = File(path)
    }

    override fun setAudioEncoder(audioEncoder: Int) {
        if (prepared) {
            throw IllegalStateException("MediaRecorder already prepared!")
        }

        if (outputFormat == null) {
            throw IllegalStateException("MediaRecorder needs an output format before an encoding can be set")
        }

        this.audioEncoder = audioEncoder
    }

    override fun setAudioEncodingSampleRate(sampleRate: Int) {
        if (prepared) {
            throw IllegalStateException("MediaRecorder already prepared!")
        }

        this.sampleRate = sampleRate
    }

    override fun setAudioEncodingBitRate(bitRate: Int) {
        if (prepared) {
            throw IllegalStateException("MediaRecorder already prepared!")
        }

        this.bitRate = bitRate
    }

    override fun prepare() {
        prepared = true
    }

    override fun start() {
        if (!prepared) {
            throw IllegalStateException("MediaRecorder not prepared!")
        }

        started = true
    }

    override fun stop() {
        started = false
    }

    override fun release() {
        if (started) {
            throw IllegalStateException("Cannot release while recording! Call stop() first")
        }

        released = true
    }

    override fun getMaxAmplitude(): Int {
        return 0
    }

    fun hasStarted(): Boolean {
        return started
    }

    fun getAudioEncoder(): Int? {
        return audioEncoder
    }

    fun getOutputFormat(): Int? {
        return outputFormat
    }

    fun getAudioSource(): Int? {
        return audioSource
    }

    fun getOutputFile(): File? {
        return file
    }

    fun isReleased(): Boolean {
        return released
    }

    fun getAudioEncodingSampleRate(): Int? {
        return sampleRate
    }

    fun getAudioEncodingBitRate(): Int? {
        return bitRate
    }
}
