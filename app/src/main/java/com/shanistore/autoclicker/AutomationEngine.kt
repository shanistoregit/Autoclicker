package com.shanistore.autoclicker

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Drives replay of a ClickSequence:
 *  - For each step, repeatedly capture the screen and search for the
 *    step's template image.
 *  - On match: tap the matched location, move to next step.
 *  - On no match after `maxRetriesPerStep` attempts: restart the whole
 *    sequence from step 0 (per user's requested behavior).
 */
class AutomationEngine(
    private val sequence: ClickSequence,
    private val maxRetriesPerStep: Int,
    private val minConfidence: Double,
    private val pollIntervalMs: Long = 500L,
    private val logCallback: (String) -> Unit
) {
    companion object {
        private const val TAG = "AutomationEngine"
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var currentStepIndex = 0
    private var attemptsOnCurrentStep = 0

    fun start() {
        if (sequence.steps.isEmpty()) {
            logCallback("Sequence is empty -- nothing to run.")
            return
        }
        running = true
        currentStepIndex = 0
        attemptsOnCurrentStep = 0
        logCallback("Replay started: ${sequence.steps.size} steps")
        scheduleNextAttempt(0)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        logCallback("Replay stopped.")
    }

    private fun scheduleNextAttempt(delayMs: Long) {
        if (!running) return
        handler.postDelayed({ attemptCurrentStep() }, delayMs)
    }

    private fun attemptCurrentStep() {
        if (!running) return

        val service = ScreenCaptureService.instance
        val accessibility = ClickAccessibilityService.instance

        if (service == null || accessibility == null) {
            logCallback("Error: capture or accessibility service not available. Stopping.")
            running = false
            return
        }

        val screenFrame = service.captureSingleFrame()
        if (screenFrame == null) {
            // Frame not ready yet, try again shortly
            scheduleNextAttempt(pollIntervalMs)
            return
        }

        val step = sequence.steps[currentStepIndex]
        val result = TemplateMatcher.find(screenFrame, step.templateBitmap, minConfidence)

        if (result.found && result.centerPoint != null) {
            logCallback(
                "Step ${currentStepIndex + 1}/${sequence.steps.size}: match found " +
                    "(confidence=${"%.2f".format(result.confidence)}), tapping."
            )
            accessibility.performTap(result.centerPoint.x, result.centerPoint.y) { success ->
                if (success) {
                    advanceToNextStep()
                } else {
                    logCallback("Tap gesture failed, retrying step.")
                    retryOrRestart()
                }
            }
        } else {
            logCallback(
                "Step ${currentStepIndex + 1}/${sequence.steps.size}: not found " +
                    "(best confidence=${"%.2f".format(result.confidence)}), " +
                    "attempt ${attemptsOnCurrentStep + 1}/$maxRetriesPerStep"
            )
            retryOrRestart()
        }
    }

    private fun retryOrRestart() {
        attemptsOnCurrentStep++
        if (attemptsOnCurrentStep >= maxRetriesPerStep) {
            logCallback(
                "Step ${currentStepIndex + 1} failed after $maxRetriesPerStep attempts. " +
                    "Restarting sequence from step 1."
            )
            currentStepIndex = 0
            attemptsOnCurrentStep = 0
        }
        scheduleNextAttempt(pollIntervalMs)
    }

    private fun advanceToNextStep() {
        attemptsOnCurrentStep = 0
        currentStepIndex++
        if (currentStepIndex >= sequence.steps.size) {
            logCallback("Sequence complete. Restarting from step 1 (continuous loop).")
            currentStepIndex = 0
        }
        scheduleNextAttempt(pollIntervalMs)
    }
}
