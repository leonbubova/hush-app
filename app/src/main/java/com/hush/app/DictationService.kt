package com.hush.app

import android.app.Notification
import com.hush.app.BuildConfig
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hush.app.transcription.ModelManager
import com.hush.app.transcription.MoonshineProvider
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderFactory
import com.hush.app.transcription.PostProcessorConfig
import com.hush.app.transcription.ProviderRepository
import com.hush.app.transcription.ErrorMessages
import com.hush.app.transcription.TextPostProcessor
import com.hush.app.transcription.TranscribeResult
import com.hush.app.transcription.VoxtralRealtimeProvider
import kotlinx.coroutines.*

class DictationService : Service() {

    companion object {
        private const val TAG = "DictationService"
        private const val MAX_RECORDING_MS = 300_000L // 5 minutes
        const val ACTION_TOGGLE = "com.hush.TOGGLE"
        var currentState: DictationState = DictationState.IDLE
            private set
        const val ACTION_OVERLAY_SHOW = "com.hush.ACTION_OVERLAY_SHOW"
        const val ACTION_OVERLAY_DISMISS = "com.hush.ACTION_OVERLAY_DISMISS"
        const val EXTRA_OVERLAY_TEXT = "com.hush.EXTRA_OVERLAY_TEXT"
        const val EXTRA_OVERLAY_MODEL = "com.hush.EXTRA_OVERLAY_MODEL"
        const val NOTIF_ID = 1
        const val ACTION_STATUS_PILL = "com.hush.ACTION_STATUS_PILL"
        const val EXTRA_PILL_TYPE = "com.hush.EXTRA_PILL_TYPE"
        const val EXTRA_PILL_MESSAGE = "com.hush.EXTRA_PILL_MESSAGE"
        const val EXTRA_WORD_COUNT = "com.hush.EXTRA_WORD_COUNT"
    }

    inner class LocalBinder : Binder() {
        fun getService(): DictationService = this@DictationService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var isStreaming = false
    private var isForegroundStarted = false
    private var recordingStartMs: Long = 0L
    private var autoStopJob: Job? = null

    // Streaming state
    private val mainHandler = Handler(Looper.getMainLooper())
    private var moonshineProvider: MoonshineProvider? = null
    private var voxtralRealtimeProvider: VoxtralRealtimeProvider? = null
    private val accumulatedText = StringBuilder()
    private var streamingToExternalApp = false
    private var streamingModelLabel = ""

    var isAppInForeground = false

    var onStateChanged: ((DictationState, String?) -> Unit)? = null
    var onStreamingTextChanged: ((String) -> Unit)? = null

    enum class DictationState {
        IDLE, RECORDING, STREAMING, PROCESSING, DONE, ERROR
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForegroundStarted) {
            startForeground(NOTIF_ID, buildNotification(DictationState.IDLE))
            isForegroundStarted = true
        }
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
        }
        return START_STICKY
    }

    fun toggle() {
        if (isStreaming) {
            stopStreaming()
        } else if (isRecording) {
            stopRecording()
        } else if (ProviderFactory.isStreaming(this)) {
            startStreaming()
        } else {
            startRecording()
        }
    }

    // --- Batch recording (existing flow) ---

    private fun startRecording() {
        val started = audioRecorder?.start() ?: false
        if (started) {
            isRecording = true
            recordingStartMs = System.currentTimeMillis()
            updateState(DictationState.RECORDING)
            val config = ProviderRepository.getConfig(this, ProviderRepository.getActiveProviderId(this))
            sendStatusPill("START", "Hush \u00b7 ${config.displayLabel} \u00b7 ${versionLabel()}")
            autoStopJob = scope.launch {
                delay(MAX_RECORDING_MS)
                if (isRecording) {
                    withContext(Dispatchers.Main) { stopRecording() }
                }
            }
        } else {
            updateState(DictationState.ERROR, ErrorMessages.micUnavailable())
            sendStatusPill("ERROR", ErrorMessages.micUnavailable())
        }
    }

    private fun stopRecording() {
        autoStopJob?.cancel()
        autoStopJob = null
        val durationSeconds = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt()
        isRecording = false
        updateState(DictationState.PROCESSING)

        val file = audioRecorder?.stop() ?: run {
            updateState(DictationState.ERROR, ErrorMessages.recorderFailed())
            sendStatusPill("ERROR", ErrorMessages.recorderFailed())
            return
        }

        scope.launch {
            try {
                val provider = ProviderFactory.resolve(this@DictationService)
                Log.i(TAG, "Sending file to ${provider.displayName}: ${file.length()} bytes")
                val result = provider.transcribe(file)
                Log.i(TAG, "Transcription result: $result")
                withContext(Dispatchers.Main) {
                    when (result) {
                        is TranscribeResult.Success -> {
                            if (result.text.isNotBlank()) {
                                val finalText = postProcessIfEnabled(result.text)
                                val wordCount = finalText.trim().split("\\s+".toRegex()).size
                                UsageRepository.recordSession(this@DictationService, recordingStartMs, durationSeconds, wordCount)
                                HistoryRepository.addEntry(this@DictationService, finalText)
                                copyToClipboard(finalText)
                                if (!isAppInForeground) {
                                    sendBroadcast(Intent(HushAccessibilityService.ACTION_INJECT_TEXT).setPackage(packageName).apply {
                                        putExtra(EXTRA_WORD_COUNT, wordCount)
                                    })
                                }
                            } else {
                                sendStatusPill("DONE", "Done \u00b7 no speech detected")
                            }
                            updateState(DictationState.DONE, result.text)
                        }
                        is TranscribeResult.Error -> {
                            updateState(DictationState.ERROR, result.message)
                            sendStatusPill("ERROR", result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during transcription", e)
                withContext(Dispatchers.Main) {
                    updateState(DictationState.ERROR, ErrorMessages.unexpectedError())
                    sendStatusPill("ERROR", ErrorMessages.unexpectedError())
                }
            } finally {
                file.delete()
            }
        }
    }

    // --- Streaming ---

    private fun startStreaming() {
        val activeId = ProviderRepository.getActiveProviderId(this)
        when (activeId) {
            ProviderConfig.PROVIDER_MOONSHINE -> startMoonshineStreaming()
            ProviderConfig.PROVIDER_VOXTRAL_REALTIME -> startVoxtralRealtimeStreaming()
        }
    }

    private fun startMoonshineStreaming() {
        val modelManager = ModelManager(this)
        val config = ProviderRepository.getConfig(
            this, ProviderConfig.PROVIDER_MOONSHINE
        ) as? ProviderConfig.Moonshine ?: ProviderConfig.Moonshine()

        val modelPath = modelManager.getMoonshineModelPath(config.model)
        if (modelPath == null) {
            updateState(DictationState.ERROR, ErrorMessages.moonshineNotDownloaded())
            sendStatusPill("ERROR", ErrorMessages.moonshineNotDownloaded())
            return
        }

        accumulatedText.clear()
        streamingToExternalApp = !isAppInForeground
        streamingModelLabel = config.displayLabel

        val provider = MoonshineProvider()
        try {
            provider.initialize(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Moonshine", e)
            updateState(DictationState.ERROR, ErrorMessages.modelLoadFailed())
            sendStatusPill("ERROR", ErrorMessages.modelLoadFailed())
            return
        }

        provider.onLineStarted = {
            // No injection state to reset
        }

        provider.onLineTextChanged = { partialText ->
            mainHandler.post {
                val fullText = if (accumulatedText.isEmpty()) partialText
                    else "$accumulatedText $partialText"
                onStreamingTextChanged?.invoke(fullText)
                if (streamingToExternalApp) {
                    sendBroadcast(Intent(ACTION_OVERLAY_SHOW).setPackage(packageName).apply {
                        putExtra(EXTRA_OVERLAY_TEXT, fullText)
                        putExtra(EXTRA_OVERLAY_MODEL, streamingModelLabel)
                    })
                }
            }
        }

        provider.onLineCompleted = { finalText ->
            mainHandler.post {
                if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                accumulatedText.append(finalText)

                val fullText = accumulatedText.toString()
                onStreamingTextChanged?.invoke(fullText)
                if (streamingToExternalApp) {
                    sendBroadcast(Intent(ACTION_OVERLAY_SHOW).setPackage(packageName).apply {
                        putExtra(EXTRA_OVERLAY_TEXT, fullText)
                        putExtra(EXTRA_OVERLAY_MODEL, streamingModelLabel)
                    })
                }
            }
        }

        provider.onError = { message ->
            mainHandler.post {
                Log.e(TAG, "Moonshine error: $message")
                if (streamingToExternalApp) {
                    sendBroadcast(Intent(ACTION_OVERLAY_DISMISS).setPackage(packageName))
                }
                try {
                    stopStreaming()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in stopStreaming during error handler", e)
                    isStreaming = false
                    moonshineProvider?.release()
                    moonshineProvider = null
                }
                updateState(DictationState.ERROR, message)
                sendStatusPill("ERROR", message)
            }
        }

        moonshineProvider = provider
        isStreaming = true
        recordingStartMs = System.currentTimeMillis()
        provider.start()
        updateState(DictationState.STREAMING)
        sendStatusPill("START", "Hush \u00b7 ${config.displayLabel} \u00b7 ${versionLabel()}")

        autoStopJob = scope.launch {
            delay(MAX_RECORDING_MS)
            if (isStreaming) {
                withContext(Dispatchers.Main) { stopStreaming() }
            }
        }
    }

    private fun startVoxtralRealtimeStreaming() {
        val config = ProviderRepository.getConfig(
            this, ProviderConfig.PROVIDER_VOXTRAL_REALTIME
        ) as? ProviderConfig.VoxtralRealtime ?: ProviderConfig.VoxtralRealtime()

        accumulatedText.clear()
        streamingToExternalApp = !isAppInForeground
        streamingModelLabel = config.displayLabel

        val provider = VoxtralRealtimeProvider(config)

        provider.onLineStarted = {
            // No injection state to reset
        }

        provider.onLineTextChanged = { partialText ->
            mainHandler.post {
                val fullText = if (accumulatedText.isEmpty()) partialText
                    else "$accumulatedText $partialText"
                onStreamingTextChanged?.invoke(fullText)
                if (streamingToExternalApp) {
                    sendBroadcast(Intent(ACTION_OVERLAY_SHOW).setPackage(packageName).apply {
                        putExtra(EXTRA_OVERLAY_TEXT, fullText)
                        putExtra(EXTRA_OVERLAY_MODEL, streamingModelLabel)
                    })
                }
            }
        }

        provider.onLineCompleted = { finalText ->
            mainHandler.post {
                if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                accumulatedText.append(finalText)

                val fullText = accumulatedText.toString()
                onStreamingTextChanged?.invoke(fullText)
                if (streamingToExternalApp) {
                    sendBroadcast(Intent(ACTION_OVERLAY_SHOW).setPackage(packageName).apply {
                        putExtra(EXTRA_OVERLAY_TEXT, fullText)
                        putExtra(EXTRA_OVERLAY_MODEL, streamingModelLabel)
                    })
                }
            }
        }

        provider.onError = { message ->
            mainHandler.post {
                Log.e(TAG, "Voxtral Realtime error: $message")
                if (streamingToExternalApp) {
                    sendBroadcast(Intent(ACTION_OVERLAY_DISMISS).setPackage(packageName))
                }
                try {
                    stopStreaming()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in stopStreaming during error handler", e)
                    isStreaming = false
                    voxtralRealtimeProvider?.release()
                    voxtralRealtimeProvider = null
                }
                updateState(DictationState.ERROR, message)
                sendStatusPill("ERROR", message)
            }
        }

        voxtralRealtimeProvider = provider
        isStreaming = true
        recordingStartMs = System.currentTimeMillis()
        provider.start()
        updateState(DictationState.STREAMING)
        sendStatusPill("START", "Hush \u00b7 ${config.displayLabel} \u00b7 ${versionLabel()}")

        autoStopJob = scope.launch {
            delay(MAX_RECORDING_MS)
            if (isStreaming) {
                withContext(Dispatchers.Main) { stopStreaming() }
            }
        }
    }

    private fun stopStreaming() {
        autoStopJob?.cancel()
        autoStopJob = null
        isStreaming = false

        // Capture partial text BEFORE stopping providers — once stopped, the text is gone
        val pendingText = moonshineProvider?.getCurrentText()
            ?: voxtralRealtimeProvider?.getCurrentText()
            ?: ""

        moonshineProvider?.stop()
        moonshineProvider?.release()
        moonshineProvider = null

        voxtralRealtimeProvider?.stop()
        voxtralRealtimeProvider?.release()
        voxtralRealtimeProvider = null

        // Always append pending partial text (the in-progress line the user saw)
        if (pendingText.isNotBlank()) {
            if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
            accumulatedText.append(pendingText)
        }

        val rawText = accumulatedText.toString().trim()

        // Dismiss overlay immediately — don't wait for post-processing
        if (streamingToExternalApp) {
            sendBroadcast(Intent(ACTION_OVERLAY_DISMISS).setPackage(packageName))
        }

        if (rawText.isBlank()) {
            if (streamingToExternalApp) {
                sendStatusPill("DONE", "Done \u00b7 no speech detected")
            }
            updateState(DictationState.DONE, rawText)
            return
        }

        val ppConfig = ProviderRepository.getPostProcessorConfig(this)
        if (ppConfig.enabled && ppConfig.apiKey.isNotBlank()) {
            updateState(DictationState.PROCESSING)
            scope.launch {
                val finalText = postProcessIfEnabled(rawText)
                withContext(Dispatchers.Main) {
                    finishStreaming(finalText)
                }
            }
        } else {
            finishStreaming(rawText)
        }
    }

    private fun finishStreaming(finalText: String) {
        val durationSeconds = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt()
        val wordCount = finalText.split("\\s+".toRegex()).size
        UsageRepository.recordSession(this, recordingStartMs, durationSeconds, wordCount)
        HistoryRepository.addEntry(this, finalText)
        copyToClipboard(finalText)

        if (streamingToExternalApp) {
            mainHandler.postDelayed({
                sendBroadcast(Intent(HushAccessibilityService.ACTION_INJECT_TEXT).setPackage(packageName).apply {
                    putExtra(EXTRA_WORD_COUNT, wordCount)
                })
            }, 150)
        }

        updateState(DictationState.DONE, finalText)
    }

    // --- Post-processing ---

    private suspend fun postProcessIfEnabled(rawText: String): String {
        val config = ProviderRepository.getPostProcessorConfig(this@DictationService)
        if (!config.enabled || config.apiKey.isBlank()) return rawText
        return TextPostProcessor(config).process(rawText)
    }

    // --- Common ---

    private fun sendStatusPill(type: String, message: String) {
        if (isAppInForeground) return
        sendBroadcast(Intent(ACTION_STATUS_PILL).setPackage(packageName).apply {
            putExtra(EXTRA_PILL_TYPE, type)
            putExtra(EXTRA_PILL_MESSAGE, message)
        })
    }

    private fun versionLabel(): String {
        val v = BuildConfig.VERSION_NAME
        return if (BuildConfig.DEBUG) "${v}-dev" else v
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
    }

    private fun updateState(state: DictationState, text: String? = null) {
        currentState = state
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIF_ID, buildNotification(state, text))
        onStateChanged?.invoke(state, text)
        TileService.requestListeningState(this, ComponentName(this, DictationTileService::class.java))
    }

    private fun buildNotification(state: DictationState, text: String? = null): Notification {
        val toggleIntent = Intent(this, DictationService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePending = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body, icon) = when (state) {
            DictationState.IDLE -> Triple(if (BuildConfig.DEBUG) "Hush dev" else "Hush", "Tap to start dictating", R.drawable.ic_notif)
            DictationState.RECORDING -> Triple("Recording...", "Tap to stop", R.drawable.ic_notif)
            DictationState.STREAMING -> Triple("Streaming...", "Speaking — tap to stop", R.drawable.ic_notif)
            DictationState.PROCESSING -> Triple("Processing...", "Transcribing your audio", R.drawable.ic_notif)
            DictationState.DONE -> Triple("Copied to clipboard!", text?.take(80) ?: "", R.drawable.ic_notif)
            DictationState.ERROR -> Triple("Error", text ?: ErrorMessages.unexpectedError(), R.drawable.ic_notif)
        }

        val actionLabel = when (state) {
            DictationState.RECORDING, DictationState.STREAMING -> "Stop"
            else -> "Record"
        }

        return NotificationCompat.Builder(this, HushApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notif, actionLabel, togglePending)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        moonshineProvider?.release()
        voxtralRealtimeProvider?.release()
        audioRecorder?.release()
        super.onDestroy()
    }
}
