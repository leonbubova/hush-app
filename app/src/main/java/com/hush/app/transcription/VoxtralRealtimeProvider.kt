package com.hush.app.transcription

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VoxtralRealtimeProvider(private val config: ProviderConfig.VoxtralRealtime) {

    companion object {
        private const val TAG = "VoxtralRealtimeProvider"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_MS = 480
        private const val CHUNK_SIZE_BYTES = SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000 // 15360 bytes (PCM16)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var isRunning = false

    var onLineStarted: (() -> Unit)? = null
    var onLineTextChanged: ((String) -> Unit)? = null
    var onLineCompleted: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start() {
        if (config.apiKey.isBlank()) {
            onError?.invoke("Mistral API key not configured — go to Settings")
            return
        }

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(config.endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                val sessionUpdate = JSONObject().apply {
                    put("type", "session.update")
                    put("model", config.model)
                }
                ws.send(sessionUpdate.toString())
                startAudioCapture()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "transcription.delta" -> {
                            val delta = msg.optString("text", "")
                            if (delta.isNotEmpty()) {
                                mainHandler.post { onLineTextChanged?.invoke(delta) }
                            }
                        }
                        "transcription.done" -> {
                            val finalText = msg.optString("text", "")
                            mainHandler.post { onLineCompleted?.invoke(finalText) }
                        }
                        "error" -> {
                            val errorMsg = msg.optJSONObject("error")?.optString("message")
                                ?: msg.optString("message", "Server error")
                            Log.e(TAG, "Server error: $errorMsg")
                            mainHandler.post { onError?.invoke(errorMsg) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse WebSocket message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                val message = when {
                    response?.code == 401 -> "Invalid API key — check your Mistral key in Settings"
                    response?.code == 429 -> "Rate limited — try again later"
                    t.message?.contains("Unable to resolve host") == true -> "No internet connection"
                    else -> "Connection failed: ${t.message}"
                }
                mainHandler.post { onError?.invoke(message) }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }
        })
    }

    private fun startAudioCapture() {
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBufSize, CHUNK_SIZE_BYTES * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            mainHandler.post { onError?.invoke("Microphone unavailable") }
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRunning = true
        audioRecord?.startRecording()
        mainHandler.post { onLineStarted?.invoke() }
        Log.i(TAG, "Audio capture started")

        captureThread = Thread({
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0 && isRunning) {
                    val data = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
                    val msg = JSONObject().apply {
                        put("type", "input_audio_buffer.append")
                        put("audio", encoded)
                    }
                    webSocket?.send(msg.toString())
                }
            }
        }, "VoxtralRealtimeCapture").apply { start() }
    }

    fun stop() {
        isRunning = false
        captureThread?.join(2000)
        captureThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        try {
            val commit = JSONObject().apply {
                put("type", "input_audio_buffer.commit")
            }
            webSocket?.send(commit.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error sending commit", e)
        }

        webSocket?.close(1000, "Session ended")
        webSocket = null
        Log.i(TAG, "Streaming stopped")
    }

    fun release() {
        stop()
        onLineStarted = null
        onLineTextChanged = null
        onLineCompleted = null
        onError = null
    }
}
