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
    private lateinit var statusPillOverlay: StatusPillOverlay

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DictationService.ACTION_OVERLAY_SHOW -> {
                    val text = intent.getStringExtra(DictationService.EXTRA_OVERLAY_TEXT) ?: return
                    val model = intent.getStringExtra(DictationService.EXTRA_OVERLAY_MODEL) ?: ""
                    overlayManager.show(text, model)
                }
                DictationService.ACTION_OVERLAY_DISMISS -> {
                    overlayManager.dismiss()
                }
            }
        }
    }

    private val injectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val wordCount = intent?.getIntExtra(DictationService.EXTRA_WORD_COUNT, 0) ?: 0
            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) {
                Log.i(TAG, "No focused text field — text remains on clipboard")
                statusPillOverlay.show(StatusPillOverlay.PillType.DONE, "Copied \u00b7 no text field")
                return
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.i(TAG, "Pasted transcription")
            statusPillOverlay.show(StatusPillOverlay.PillType.DONE, "Pasted \u00b7 $wordCount words")
        }
    }

    private val pillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val type = intent?.getStringExtra(DictationService.EXTRA_PILL_TYPE) ?: return
            val message = intent.getStringExtra(DictationService.EXTRA_PILL_MESSAGE) ?: return
            statusPillOverlay.show(type, message)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = StreamingOverlayManager(this)
        statusPillOverlay = StatusPillOverlay(this)

        val overlayFilter = IntentFilter().apply {
            addAction(DictationService.ACTION_OVERLAY_SHOW)
            addAction(DictationService.ACTION_OVERLAY_DISMISS)
        }
        registerReceiver(overlayReceiver, overlayFilter, Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(injectReceiver, IntentFilter(ACTION_INJECT_TEXT), Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(pillReceiver, IntentFilter(DictationService.ACTION_STATUS_PILL), Context.RECEIVER_NOT_EXPORTED)
        Log.i(TAG, "Registered overlay, inject, and pill broadcast receivers")
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
        statusPillOverlay.dismiss()
    }

    override fun onDestroy() {
        overlayManager.dismiss()
        statusPillOverlay.dismiss()
        unregisterReceiver(overlayReceiver)
        unregisterReceiver(injectReceiver)
        unregisterReceiver(pillReceiver)
        super.onDestroy()
    }
}
