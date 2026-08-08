package com.shanistore.autoclicker

import android.graphics.Bitmap
import android.graphics.Point
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Wraps OpenCV template matching so the rest of the app never has to
 * touch OpenCV Mats directly.
 */
object TemplateMatcher {

    data class MatchResult(val found: Boolean, val centerPoint: Point?, val confidence: Double)

    /**
     * Searches `screenBitmap` for `templateBitmap`.
     * Returns the center point of the best match if it clears `minConfidence`.
     */
    fun find(screenBitmap: Bitmap, templateBitmap: Bitmap, minConfidence: Double): MatchResult {
        val screenMat = Mat()
        val templateMat = Mat()

        try {
            Utils.bitmapToMat(screenBitmap, screenMat)
            Utils.bitmapToMat(templateBitmap, templateMat)

            // Convert to grayscale for more robust, lighting-tolerant matching
            Imgproc.cvtColor(screenMat, screenMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_RGBA2GRAY)

            if (templateMat.cols() > screenMat.cols() || templateMat.rows() > screenMat.rows()) {
                return MatchResult(false, null, 0.0)
            }

            val resultCols = screenMat.cols() - templateMat.cols() + 1
            val resultRows = screenMat.rows() - templateMat.rows() + 1
            val result = Mat(resultRows, resultCols, CvType.CV_32FC1)

            Imgproc.matchTemplate(screenMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

            val mmr = Core.minMaxLoc(result)
            val confidence = mmr.maxVal
            result.release()

            if (confidence >= minConfidence) {
                val matchLoc = mmr.maxLoc
                val centerX = (matchLoc.x + templateMat.cols() / 2.0).toInt()
                val centerY = (matchLoc.y + templateMat.rows() / 2.0).toInt()
                return MatchResult(true, Point(centerX, centerY), confidence)
            }
            return MatchResult(false, null, confidence)
        } finally {
            screenMat.release()
            templateMat.release()
        }
    }
}
