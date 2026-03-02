package com.hush.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class StatusPillOverlay(private val service: AccessibilityService) {

    enum class PillType(val borderColor: Int) {
        START(Color.parseColor("#6C63FF")),
        DONE(Color.parseColor("#4ECDC4")),
        ERROR(Color.parseColor("#FF6B6B"));

        companion object {
            fun fromString(value: String): PillType = when (value.uppercase()) {
                "START" -> START
                "DONE" -> DONE
                "ERROR" -> ERROR
                else -> START
            }
        }
    }

    companion object {
        private const val AUTO_DISMISS_MS = 2500L
        private const val BG_COLOR = "#F20D0D1A"
        private const val TEXT_COLOR = "#E6FFFFFF"
        private const val TEXT_SIZE_SP = 13f
        private const val PADDING_H_DP = 12f
        private const val PADDING_V_DP = 6f
        private const val BORDER_WIDTH_DP = 2
        private const val TOP_OFFSET_DP = 8f
    }

    private val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }
    private var pillView: LinearLayout? = null

    fun show(type: PillType, message: String) {
        dismiss()

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()
        }

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(PADDING_H_DP), dp(PADDING_V_DP), dp(PADDING_H_DP), dp(PADDING_V_DP))
        }

        val textView = TextView(service).apply {
            text = message
            setTextColor(Color.parseColor(TEXT_COLOR))
            textSize = TEXT_SIZE_SP
        }
        container.addView(textView)

        // Measure to get height for pill radius
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pillHeight = container.measuredHeight.toFloat()

        container.background = GradientDrawable().apply {
            setColor(Color.parseColor(BG_COLOR))
            setStroke(dp(BORDER_WIDTH_DP.toFloat()), type.borderColor)
            cornerRadius = pillHeight / 2f
        }

        val statusBarHeight = getStatusBarHeight()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight + dp(TOP_OFFSET_DP)
        }

        windowManager.addView(container, params)
        pillView = container

        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
    }

    fun show(typeString: String, message: String) {
        show(PillType.fromString(typeString), message)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        pillView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // View already removed
            }
        }
        pillView = null
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) service.resources.getDimensionPixelSize(resourceId) else 0
    }
}
