package com.hush.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HushAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HushA11y"
        private const val DOUBLE_TAP_THRESHOLD_MS = 400L
        const val ACTION_INJECT_TEXT = "com.hush.ACTION_INJECT_TEXT"
    }

    private var lastVolumeDownTime = 0L

    private val injectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                Log.i(TAG, "Pasted transcription into focused text field")
            } else {
                Log.i(TAG, "No focused text field — text remains on clipboard")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(injectReceiver, IntentFilter(ACTION_INJECT_TEXT), Context.RECEIVER_NOT_EXPORTED)
        Log.i(TAG, "Registered inject text broadcast receiver")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime < DOUBLE_TAP_THRESHOLD_MS) {
                lastVolumeDownTime = 0L
                Log.i(TAG, "Double-tap volume down detected, toggling dictation")
                val intent = Intent(this, DictationService::class.java).apply {
                    action = DictationService.ACTION_TOGGLE
                }
                startForegroundService(intent)
                return true // consume the key event
            }
            lastVolumeDownTime = now
        }
        return false // let the system handle it normally
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need key event filtering
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        unregisterReceiver(injectReceiver)
        super.onDestroy()
    }
}
