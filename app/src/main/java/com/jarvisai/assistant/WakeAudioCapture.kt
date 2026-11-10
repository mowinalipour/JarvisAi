package com.jarvisai.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Continuous 16 kHz mono PCM source for the upcoming sherpa-onnx keyword spotter.
 *
 * Only one component may own the microphone at a time. Call stop() and wait
 * for the capture thread to exit before starting Android SpeechRecognizer.
 * This class does not detect a wake word on its own.
 */
class WakeAudioCapture(private val context: Context) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var recorder: AudioRecord? = null

    @SuppressLint("MissingPermission")
    @Synchronized fun start(onSamples: (FloatArray) -> Unit, onError: (Throwable) -> Unit): Boolean {
        if (running.get()) return false
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError(SecurityException("RECORD_AUDIO permission is missing"))
            return false
        }
        val sampleRate = 16000
        val minimum = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimum <= 0) {
            onError(IllegalStateException("16 kHz microphone input unavailable"))
            return false
        }
        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum * 2, 3200)
            )
        } catch (error: Throwable) {
            onError(error)
            return false
        }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            onError(IllegalStateException("AudioRecord initialization failed"))
            return false
        }
        recorder = audio
        running.set(true)
        worker = thread(name = "jarvis-wake-pcm", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(1600)
            try {
                audio.startRecording()
                while (running.get()) {
                    val count = audio.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        onSamples(FloatArray(count) { index -> buffer[index] / 32768.0f })
                    } else if (count < 0) {
                        throw IllegalStateException("AudioRecord read error: $count")
                    }
                }
            } catch (error: Throwable) {
                if (running.get()) onError(error)
            } finally {
                running.set(false)
                try { audio.stop() } catch (_: Exception) {}
                audio.release()
                if (recorder === audio) recorder = null
            }
        }
        return true
    }

    fun stop() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        val current = worker
        if (current != null && current !== Thread.currentThread()) {
            try { current.join(1500) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        worker = null
    }
}
