package com.hush.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        return try {
            val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
            outputFile = file

            recorder = (if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recorder?.release()
            recorder = null
            false
        }
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }
}
