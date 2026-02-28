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
    private lateinit var overlayManager: StreamingOverlayManager

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DictationService.ACTION_OVERLAY_SHOW -> {
                    val text = intent.getStringExtra(DictationService.EXTRA_OVERLAY_TEXT) ?: return
                    overlayManager.show(text)
                }
                DictationService.ACTION_OVERLAY_DISMISS -> {
                    overlayManager.dismiss()
                }
            }
        }
    }

    private val injectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) {
                Log.i(TAG, "No focused text field — text remains on clipboard")
                return
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.i(TAG, "Pasted transcription")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = StreamingOverlayManager(this)

        val overlayFilter = IntentFilter().apply {
            addAction(DictationService.ACTION_OVERLAY_SHOW)
            addAction(DictationService.ACTION_OVERLAY_DISMISS)
        }
        registerReceiver(overlayReceiver, overlayFilter, Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(injectReceiver, IntentFilter(ACTION_INJECT_TEXT), Context.RECEIVER_NOT_EXPORTED)
        Log.i(TAG, "Registered overlay and inject broadcast receivers")
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
                return true
            }
            lastVolumeDownTime = now
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need key event filtering
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility service interrupted")
        overlayManager.dismiss()
    }

    override fun onDestroy() {
        overlayManager.dismiss()
        unregisterReceiver(overlayReceiver)
        unregisterReceiver(injectReceiver)
        super.onDestroy()
    }
}
