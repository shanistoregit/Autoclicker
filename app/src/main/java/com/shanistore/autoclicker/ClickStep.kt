package com.shanistore.autoclicker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/**
 * A single recorded action: a template image (cropped around the tap point)
 * plus the exact tap coordinates that were used when recording it.
 *
 * During replay, the template is searched for on screen. If found, we tap
 * at the *matched* location (translated from the template's origin), not
 * the original recorded coordinates -- this is what makes it work even if
 * the UI has shifted slightly.
 */
data class ClickStep(
    val id: Int,
    var templateBitmap: Bitmap,
    val recordedX: Int,
    val recordedY: Int,
    // Width/height of the region captured around the tap
    val templateWidth: Int,
    val templateHeight: Int,
    // Offset of the tap point within the template (usually center)
    val tapOffsetX: Int,
    val tapOffsetY: Int,
    var label: String = ""
) {
    fun toJson(): JSONObject {
        val stream = ByteArrayOutputStream()
        templateBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        return JSONObject().apply {
            put("id", id)
            put("image", base64Image)
            put("recordedX", recordedX)
            put("recordedY", recordedY)
            put("templateWidth", templateWidth)
            put("templateHeight", templateHeight)
            put("tapOffsetX", tapOffsetX)
            put("tapOffsetY", tapOffsetY)
            put("label", label)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ClickStep {
            val base64Image = json.getString("image")
            val bytes = Base64.decode(base64Image, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            return ClickStep(
                id = json.getInt("id"),
                templateBitmap = bitmap,
                recordedX = json.getInt("recordedX"),
                recordedY = json.getInt("recordedY"),
                templateWidth = json.getInt("templateWidth"),
                templateHeight = json.getInt("templateHeight"),
                tapOffsetX = json.getInt("tapOffsetX"),
                tapOffsetY = json.getInt("tapOffsetY"),
                label = json.optString("label", "")
            )
        }
    }
}
