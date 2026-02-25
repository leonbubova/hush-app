package com.hush.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*

class DictationService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.hush.TOGGLE"
        const val NOTIF_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): DictationService = this@DictationService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var isForegroundStarted = false
    private var recordingStartMs: Long = 0L
    private var autoStopJob: Job? = null

    var onStateChanged: ((DictationState, String?) -> Unit)? = null

    enum class DictationState {
        IDLE, RECORDING, PROCESSING, DONE, ERROR
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
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val started = audioRecorder?.start() ?: false
        if (started) {
            isRecording = true
            recordingStartMs = System.currentTimeMillis()
            updateState(DictationState.RECORDING)
            autoStopJob = scope.launch {
                delay(60_000)
                if (isRecording) {
                    withContext(Dispatchers.Main) { stopRecording() }
                }
            }
        } else {
            updateState(DictationState.ERROR, "Microphone unavailable — check permissions")
        }
    }

    private fun stopRecording() {
        autoStopJob?.cancel()
        autoStopJob = null
        val durationSeconds = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt()
        isRecording = false
        updateState(DictationState.PROCESSING)

        val file = audioRecorder?.stop() ?: run {
            updateState(DictationState.ERROR, "Recorder failed to save audio")
            return
        }

        scope.launch {
            try {
                val apiKey = getEncryptedPrefs()
                    .getString("voxtral_api_key", "") ?: ""
                if (apiKey.isBlank()) {
                    withContext(Dispatchers.Main) {
                        updateState(DictationState.ERROR, "No API key configured")
                    }
                    return@launch
                }

                Log.i("DictationService", "Sending file to Voxtral: ${file.length()} bytes, apiKey length: ${apiKey.length}")
                val result = VoxtralApi.transcribe(file, apiKey)
                Log.i("DictationService", "Transcription result: $result")
                withContext(Dispatchers.Main) {
                    when (result) {
                        is TranscribeResult.Success -> {
                            if (result.text.isNotBlank()) {
                                val wordCount = result.text.trim().split("\\s+".toRegex()).size
                                UsageRepository.recordSession(this@DictationService, recordingStartMs, durationSeconds, wordCount)
                            }
                            copyToClipboard(result.text)
                            sendBroadcast(Intent(HushAccessibilityService.ACTION_INJECT_TEXT).setPackage(packageName))
                            updateState(DictationState.DONE, result.text)
                        }
                        is TranscribeResult.Error -> {
                            updateState(DictationState.ERROR, result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DictationService", "Error during transcription", e)
                withContext(Dispatchers.Main) {
                    updateState(DictationState.ERROR, "Unexpected error: ${e.message}")
                }
            } finally {
                file.delete()
            }
        }
    }

    private fun getEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            this,
            "hush_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
    }

    private fun updateState(state: DictationState, text: String? = null) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIF_ID, buildNotification(state, text))
        onStateChanged?.invoke(state, text)
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
            DictationState.IDLE -> Triple("Hush", "Tap to start dictating", R.drawable.ic_notif)
            DictationState.RECORDING -> Triple("Recording...", "Tap to stop", R.drawable.ic_notif)
            DictationState.PROCESSING -> Triple("Processing...", "Transcribing your audio", R.drawable.ic_notif)
            DictationState.DONE -> Triple("Copied to clipboard!", text?.take(80) ?: "", R.drawable.ic_notif)
            DictationState.ERROR -> Triple("Error", text ?: "Something went wrong", R.drawable.ic_notif)
        }

        val actionLabel = if (state == DictationState.RECORDING) "Stop" else "Record"

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
        audioRecorder?.release()
        super.onDestroy()
    }
}
