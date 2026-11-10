package com.jarvisai.assistant

import android.content.Context
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean

/**
 * sherpa-onnx offline keyword spotting for English "JARVIS".
 * The model and keywords are bundled by scripts/prepare-sherpa.sh during CI.
 *
 * The callback is delivered from the microphone thread: the service must post
 * to its main handler before stopping the microphone or showing an overlay.
 */
class SherpaWakeEngine(
    private val context: Context,
    private val onDetected: () -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    private val gate = Any()
    private val enabled = AtomicBoolean(false)
    private val signalled = AtomicBoolean(false)
    private var capture: WakeAudioCapture? = null
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    fun start() {
        if (!enabled.compareAndSet(false, true)) return
        signalled.set(false)
        thread(name = "jarvis-load-kws", isDaemon = true) {
            var kws: KeywordSpotter? = null
            var onlineStream: OnlineStream? = null
            try {
                val dir = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
                val config = KeywordSpotterConfig(
                    featConfig = getFeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "$dir/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                            decoder = "$dir/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                            joiner = "$dir/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
                        ),
                        tokens = "$dir/tokens.txt",
                        modelType = "zipformer2",
                        numThreads = 2
                    ),
                    keywordsFile = "$dir/keywords.txt",
                    keywordsScore = 1.5f,
                    keywordsThreshold = 0.35f
                )
                val loadedSpotter = KeywordSpotter(assetManager = context.assets, config = config)
                kws = loadedSpotter
                val loadedStream = loadedSpotter.createStream()
                onlineStream = loadedStream
                require(loadedStream.ptr != 0L) { "Keyword stream creation failed" }
                synchronized(gate) {
                    if (!enabled.get()) return@synchronized
                    val source = WakeAudioCapture(context)
                    val detector = loadedSpotter
                    val sourceStream = loadedStream
                    val started = source.start(
                        onSamples = { samples ->
                            if (enabled.get() && !signalled.get()) {
                                sourceStream.acceptWaveform(samples, sampleRate = 16000)
                                while (enabled.get() && !signalled.get() && detector.isReady(sourceStream)) {
                                    detector.decode(sourceStream)
                                    if (detector.getResult(sourceStream).keyword.isNotBlank()) {
                                        if (signalled.compareAndSet(false, true)) onDetected()
                                    }
                                }
                            }
                        },
                        onError = { error -> if (enabled.get()) onFailure(error) }
                    )
                    check(started) { "Unable to start microphone capture" }
                    capture = source
                    spotter = detector
                    stream = sourceStream
                    kws = null
                    onlineStream = null
                }
            } catch (error: Throwable) {
                if (enabled.get()) onFailure(error)
            } finally {
                // If pause/stop occurred while the model was loading, release it.
                onlineStream?.release()
                kws?.release()
            }
        }
    }

    fun stop() {
        enabled.set(false)
        synchronized(gate) {
            capture?.stop() // waits for PCM callback to complete before releasing native stream
            capture = null
            stream?.release()
            stream = null
            spotter?.release()
            spotter = null
        }
    }
}
