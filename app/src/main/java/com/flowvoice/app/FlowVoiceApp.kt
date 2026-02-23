package com.flowvoice.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class FlowVoiceApp : Application() {

    companion object {
        const val CHANNEL_ID = "dictation_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
