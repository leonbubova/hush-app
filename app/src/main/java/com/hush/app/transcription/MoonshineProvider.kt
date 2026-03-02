package com.hush.app.transcription

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptEventListener

class MoonshineProvider {

    companion object {
        private const val TAG = "MoonshineProvider"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_SAMPLES = 1600 // 100ms at 16kHz
    }

    private var transcriber: Transcriber? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var isRunning = false
    @Volatile private var lastPartialText = ""

    var onLineStarted: (() -> Unit)? = null
    var onLineTextChanged: ((String) -> Unit)? = null
    var onLineCompleted: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun initialize(modelPath: String) {
        Log.i(TAG, "Initializing with model path: $modelPath")
        transcriber = Transcriber().apply {
            loadFromFiles(modelPath, JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING)
            addListener { event ->
                event.accept(object : TranscriptEventListener() {
                    override fun onLineStarted(e: TranscriptEvent.LineStarted) {
                        Log.d(TAG, "Line started")
                        this@MoonshineProvider.onLineStarted?.invoke()
                    }

                    override fun onLineTextChanged(e: TranscriptEvent.LineTextChanged) {
                        val text = e.line.text ?: ""
                        Log.d(TAG, "Line text changed: $text")
                        lastPartialText = text
                        this@MoonshineProvider.onLineTextChanged?.invoke(text)
                    }

                    override fun onLineCompleted(e: TranscriptEvent.LineCompleted) {
                        val text = e.line.text ?: ""
                        Log.d(TAG, "Line completed: $text")
                        lastPartialText = ""
                        this@MoonshineProvider.onLineCompleted?.invoke(text)
                    }

                    override fun onError(e: TranscriptEvent.Error) {
                        Log.e(TAG, "Transcriber error", e.cause)
                        this@MoonshineProvider.onError?.invoke(ErrorMessages.unexpectedError())
                    }
                })
            }
        }
        Log.i(TAG, "Transcriber initialized")
    }

    fun start() {
        val t = transcriber ?: run {
            onError?.invoke(ErrorMessages.modelLoadFailed())
            return
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferSize = maxOf(minBufSize, BUFFER_SIZE_SAMPLES * 4) // 4 bytes per float

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            onError?.invoke(ErrorMessages.micUnavailable())
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRunning = true
        audioRecord?.startRecording()
        t.start()
        Log.i(TAG, "Streaming started")

        captureThread = Thread({
            val buffer = FloatArray(BUFFER_SIZE_SAMPLES)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1
                if (read > 0 && isRunning) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    t.addAudio(chunk, SAMPLE_RATE)
                }
            }
        }, "MoonshineCapture").apply { start() }
    }

    fun stop() {
        isRunning = false
        captureThread?.join(2000)
        captureThread = null

        try {
            transcriber?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping transcriber", e)
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Streaming stopped")
    }

    fun getCurrentText(): String = lastPartialText

    fun release() {
        stop()
        lastPartialText = ""
        transcriber = null
        onLineStarted = null
        onLineTextChanged = null
        onLineCompleted = null
        onError = null
    }
}
