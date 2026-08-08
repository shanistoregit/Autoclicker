package com.shanistore.autoclicker

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages a recording session:
 *  1. 3-second countdown
 *  2. Shows RecordingOverlay to catch taps
 *  3. For each tap, crops a template region from the current screen frame
 *     around the tap point (auto-capture)
 *  4. Builds up a ClickSequence as taps come in
 *
 * Manual crop adjustment (resizing the captured region for a given step)
 * is handled afterward in the review/edit screen using the same
 * templateWidth/templateHeight fields on ClickStep -- this class only
 * handles the initial auto-capture.
 */
class RecordingSession(
    private val context: Context,
    private val sequenceName: String,
    private val autoTemplateSize: Int = 160, // px, region captured around each tap
    private val countdownCallback: (Int) -> Unit,
    private val tapRecordedCallback: (ClickStep) -> Unit,
    private val logCallback: (String) -> Unit
) {
    companion object {
        private const val TAG = "RecordingSession"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val overlay = RecordingOverlay(context)
    private val sequence = ClickSequence(sequenceName)
    private var stepIdCounter = 0
    @Volatile private var active = false

    fun startWithCountdown() {
        var remaining = 3
        countdownCallback(remaining)
        val tick = object : Runnable {
            override fun run() {
                remaining--
                if (remaining > 0) {
                    countdownCallback(remaining)
                    handler.postDelayed(this, 1000)
                } else {
                    countdownCallback(0)
                    beginCapturing()
                }
            }
        }
        handler.postDelayed(tick, 1000)
    }

    private fun beginCapturing() {
        active = true
        overlay.tapListener = object : RecordingOverlay.TapListener {
            override fun onTapDetected(x: Int, y: Int) {
                if (!active) return
                onTap(x, y)
            }
        }
        overlay.show()
        logCallback("Recording active. Perform your taps now.")
    }

    private fun onTap(x: Int, y: Int) {
        val frame = ScreenCaptureService.instance?.captureSingleFrame()
        if (frame == null) {
            logCallback("Warning: no screen frame available, skipped a tap at ($x, $y)")
            return
        }

        val template = cropAround(frame, x, y, autoTemplateSize)
        if (template == null) {
            logCallback("Warning: could not crop template at ($x, $y)")
            return
        }

        stepIdCounter++
        val step = ClickStep(
            id = stepIdCounter,
            templateBitmap = template,
            recordedX = x,
            recordedY = y,
            templateWidth = template.width,
            templateHeight = template.height,
            tapOffsetX = template.width / 2,
            tapOffsetY = template.height / 2,
            label = "Step $stepIdCounter"
        )
        sequence.steps.add(step)
        logCallback("Captured step $stepIdCounter at ($x, $y)")
        tapRecordedCallback(step)
    }

    private fun cropAround(bitmap: Bitmap, x: Int, y: Int, size: Int): Bitmap? {
        val half = size / 2
        val left = (x - half).coerceIn(0, bitmap.width - 1)
        val top = (y - half).coerceIn(0, bitmap.height - 1)
        val right = (x + half).coerceIn(left + 1, bitmap.width)
        val bottom = (y + half).coerceIn(top + 1, bitmap.height)

        return try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            Log.e(TAG, "Crop failed", e)
            null
        }
    }

    fun stop(): ClickSequence {
        active = false
        overlay.hide()
        handler.removeCallbacksAndMessages(null)
        logCallback("Recording stopped. ${sequence.steps.size} steps captured.")
        return sequence
    }
}
