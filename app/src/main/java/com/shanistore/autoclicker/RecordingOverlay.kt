package com.shanistore.autoclicker

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * A transparent, full-screen overlay that intercepts taps while recording
 * is active, then lets the touch pass through to the app underneath so the
 * user's tap still actually does whatever it normally does (this app is
 * meant to record real interactions with other apps/games).
 *
 * Requires "Display over other apps" permission (SYSTEM_ALERT_WINDOW).
 */
class RecordingOverlay(private val context: Context) {

    interface TapListener {
        fun onTapDetected(x: Int, y: Int)
    }

    var tapListener: TapListener? = null
    private var overlayView: View? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun show() {
        if (overlayView != null) return

        val view = object : View(context) {
            override fun onTouchEvent(event: MotionEvent?): Boolean {
                if (event?.action == MotionEvent.ACTION_DOWN) {
                    tapListener?.onTapDetected(event.rawX.toInt(), event.rawY.toInt())
                }
                // Return false so the event is NOT consumed -- it passes
                // through to the app below (requires FLAG_NOT_TOUCHABLE
                // to be OFF while still not consuming... see note below).
                return false
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            // NOT_TOUCH_MODAL lets touches outside pass through to windows
            // beneath, while our view still receives the DOWN event first
            // for detection purposes.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(view, params)
        overlayView = view
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
