package com.hush.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class StreamingOverlayManager(private val service: AccessibilityService) {

    companion object {
        private const val WATCHDOG_TIMEOUT_MS = 5000L
    }

    private val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var overlayView: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var textView: TextView? = null
    private var tagView: TextView? = null
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = Runnable { dismiss() }

    fun show(text: String, model: String = "") {
        if (overlayView == null) {
            createOverlay()
        }
        textView?.text = text
        scrollView?.post { scrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
        if (model.isNotEmpty()) {
            tagView?.text = "Hush · $model"
        }
        resetWatchdog()
    }

    fun dismiss() {
        watchdogHandler.removeCallbacks(watchdogRunnable)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // View already removed
            }
        }
        overlayView = null
        scrollView = null
        textView = null
        tagView = null
    }

    private fun resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)
    }

    private fun createOverlay() {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()
        }

        // Container with rounded corners
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F21A1A2E"))
                setStroke(1, Color.parseColor("#4D6C63FF"))
                cornerRadius = dp(16f).toFloat()
            }
        }

        // Scrolling text view
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64f),
            )
        }
        val tv = TextView(service).apply {
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 14f
        }
        scrollView.addView(tv)
        container.addView(scrollView)
        this.scrollView = scrollView

        // "Hush" tag — subtle, right-aligned
        val tag = TextView(service).apply {
            this.text = "Hush"
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 10f
            gravity = Gravity.END
            setPadding(0, dp(2f), 0, 0)
        }
        container.addView(tag)

        textView = tv
        tagView = tag

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, service.resources.displayMetrics).toInt()
            horizontalMargin = 0.04f // ~4% margin on each side
        }

        windowManager.addView(container, params)
        overlayView = container
    }
}
