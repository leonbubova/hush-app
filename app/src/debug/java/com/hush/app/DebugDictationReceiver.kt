package com.hush.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-only exported receiver to toggle dictation from adb.
 * Usage: adb shell am broadcast -a com.hush.app.debug.TOGGLE_DICTATION
 */
class DebugDictationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("DebugDictation", "Received toggle broadcast, starting DictationService")
        val serviceIntent = Intent(context, DictationService::class.java).apply {
            action = DictationService.ACTION_TOGGLE
        }
        context.startForegroundService(serviceIntent)
    }
}
