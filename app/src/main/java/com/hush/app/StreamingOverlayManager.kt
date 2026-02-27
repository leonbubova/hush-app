package com.hush.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class StreamingOverlayManager(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var overlayView: LinearLayout? = null
    private var textView: TextView? = null

    fun show(text: String) {
        if (overlayView == null) {
            createOverlay()
        }
        textView?.text = text
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // View already removed
            }
        }
        overlayView = null
        textView = null
    }

    private fun createOverlay() {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()
        }

        // Container with rounded corners
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F21A1A2E"))
                setStroke(dp(1f), Color.parseColor("#6C63FF"))
                cornerRadius = dp(16f).toFloat()
            }
        }

        // "Streaming..." label
        val label = TextView(service).apply {
            this.text = "Streaming..."
            setTextColor(Color.parseColor("#6C63FF"))
            textSize = 12f
        }
        container.addView(label)

        // Scrolling text view
        val scrollView = ScrollView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(80f),
            )
        }
        val tv = TextView(service).apply {
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 14f
            setPadding(0, dp(4f), 0, 0)
        }
        scrollView.addView(tv)
        container.addView(scrollView)

        textView = tv

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
