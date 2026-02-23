package com.flowvoice.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class DictationService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.flowvoice.TOGGLE"
        const val NOTIF_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): DictationService = this@DictationService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false

    var onStateChanged: ((DictationState) -> Unit)? = null

    enum class DictationState {
        IDLE, RECORDING, PROCESSING, DONE, ERROR
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
        }
        startForeground(NOTIF_ID, buildNotification(DictationState.IDLE))
        return START_STICKY
    }

    fun toggle() {
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        isRecording = true
        updateState(DictationState.RECORDING)
        audioRecorder?.start()
    }

    private fun stopRecording() {
        isRecording = false
        updateState(DictationState.PROCESSING)
        val file = audioRecorder?.stop() ?: run {
            updateState(DictationState.ERROR)
            return
        }

        scope.launch {
            try {
                val apiKey = getSharedPreferences("flowvoice", MODE_PRIVATE)
                    .getString("voxtral_api_key", "") ?: ""
                if (apiKey.isBlank()) {
                    withContext(Dispatchers.Main) {
                        updateState(DictationState.ERROR)
                    }
                    return@launch
                }

                Log.d("DictationService", "Sending file to Voxtral: ${file.length()} bytes, apiKey length: ${apiKey.length}")
                val transcription = VoxtralApi.transcribe(file, apiKey)
                Log.d("DictationService", "Transcription result: $transcription")
                withContext(Dispatchers.Main) {
                    if (transcription != null) {
                        copyToClipboard(transcription)
                        updateState(DictationState.DONE, transcription)
                    } else {
                        updateState(DictationState.ERROR)
                    }
                }
            } catch (e: Exception) {
                Log.e("DictationService", "Error during transcription", e)
                withContext(Dispatchers.Main) {
                    updateState(DictationState.ERROR)
                }
            } finally {
                file.delete()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
    }

    private fun updateState(state: DictationState, text: String? = null) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIF_ID, buildNotification(state, text))
        onStateChanged?.invoke(state)
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
            DictationState.IDLE -> Triple("FlowVoice", "Tap to start dictating", R.drawable.ic_mic)
            DictationState.RECORDING -> Triple("Recording...", "Tap to stop", R.drawable.ic_mic_active)
            DictationState.PROCESSING -> Triple("Processing...", "Transcribing your audio", R.drawable.ic_mic)
            DictationState.DONE -> Triple("Copied to clipboard!", text?.take(80) ?: "", R.drawable.ic_mic)
            DictationState.ERROR -> Triple("Error", "Something went wrong", R.drawable.ic_mic)
        }

        val actionLabel = if (state == DictationState.RECORDING) "Stop" else "Record"

        return NotificationCompat.Builder(this, FlowVoiceApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_mic, actionLabel, togglePending)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        audioRecorder?.release()
        super.onDestroy()
    }
}
