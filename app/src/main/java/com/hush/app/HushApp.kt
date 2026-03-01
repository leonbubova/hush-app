package com.hush.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class HushApp : Application() {

    companion object {
        const val CHANNEL_ID = "dictation_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        cleanOrphanedAudioFiles()
    }

    private fun cleanOrphanedAudioFiles() {
        cacheDir.listFiles()?.filter {
            it.name.startsWith("recording_") && it.extension == "m4a"
        }?.forEach { it.delete() }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dictation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows dictation recording status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
