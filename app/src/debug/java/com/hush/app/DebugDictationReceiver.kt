package com.hush.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderRepository
import org.json.JSONObject

/**
 * Debug-only exported receiver to toggle dictation and switch providers from adb.
 *
 * Toggle dictation:
 *   adb shell am broadcast -n com.hush.app.debug/com.hush.app.DebugDictationReceiver \
 *     -a com.hush.app.debug.TOGGLE_DICTATION
 *
 * Switch provider (and optionally model):
 *   adb shell am broadcast -n com.hush.app.debug/com.hush.app.DebugDictationReceiver \
 *     -a com.hush.app.debug.SWITCH_PROVIDER --es provider openai --es model gpt-4o-transcribe
 */
class DebugDictationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                Log.i(TAG, "Received toggle broadcast, starting DictationService")
                val serviceIntent = Intent(context, DictationService::class.java).apply {
                    action = DictationService.ACTION_TOGGLE
                }
                context.startForegroundService(serviceIntent)
            }
            ACTION_SWITCH_PROVIDER -> {
                val providerId = intent.getStringExtra("provider") ?: return
                val model = intent.getStringExtra("model")
                Log.i(TAG, "Switching provider to $providerId" + if (model != null) " model=$model" else "")

                // Update model in existing config if specified
                if (model != null) {
                    val config = ProviderRepository.getConfig(context, providerId)
                    val json = config.toJson()
                    json.put("model", model)
                    val updated = ProviderConfig.fromJson(providerId, json)
                    ProviderRepository.saveConfig(context, providerId, updated)
                }

                ProviderRepository.setActiveProviderId(context, providerId)
                Log.i(TAG, "Active provider is now: ${ProviderRepository.getActiveProviderId(context)}")
            }
        }
    }

    companion object {
        private const val TAG = "DebugDictation"
        private const val ACTION_TOGGLE = "com.hush.app.debug.TOGGLE_DICTATION"
        private const val ACTION_SWITCH_PROVIDER = "com.hush.app.debug.SWITCH_PROVIDER"
    }
}
