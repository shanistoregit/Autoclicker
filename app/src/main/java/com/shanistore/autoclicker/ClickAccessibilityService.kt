package com.shanistore.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

// Note: GestureResultCallback is an inner class of AccessibilityService,
// accessed below as AccessibilityService.GestureResultCallback via
// direct reference (no separate import needed).

/**
 * System-wide tap execution via GestureDescription dispatch.
 * This does NOT capture user taps (Android does not expose raw touch
 * coordinates to AccessibilityService for privacy/security reasons).
 *
 * Recording therefore works differently: see RecordingOverlayService,
 * which uses its own transparent touch-catching overlay to capture
 * tap coordinates during the record phase. This service is only
 * responsible for *performing* taps during replay.
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickAccessibilityService"
        var instance: ClickAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used -- we don't need window content, only gesture dispatch.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Performs a single tap at the given screen coordinates.
     * Returns true if the gesture was dispatched successfully.
     */
    fun performTap(x: Int, y: Int, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 80)
        gestureBuilder.addStroke(strokeDescription)

        return dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Tap gesture cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)
    }
}
