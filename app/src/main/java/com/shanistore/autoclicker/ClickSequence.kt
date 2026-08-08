package com.shanistore.autoclicker

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * An ordered list of ClickSteps that make up one recorded automation.
 * Saved/loaded as a single .autoclick file (JSON, images embedded as base64).
 */
class ClickSequence(
    var name: String = "sequence",
    val steps: MutableList<ClickStep> = mutableListOf()
) {

    fun toJson(): JSONObject {
        val stepsArray = JSONArray()
        for (step in steps) {
            stepsArray.put(step.toJson())
        }
        return JSONObject().apply {
            put("name", name)
            put("version", 1)
            put("steps", stepsArray)
        }
    }

    fun saveToFile(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(toJson().toString().toByteArray())
        }
    }

    fun saveToInternalStorage(context: Context, fileName: String): File {
        val dir = File(context.filesDir, "sequences")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$fileName.autoclick")
        file.writeText(toJson().toString())
        return file
    }

    companion object {
        fun fromJson(json: JSONObject): ClickSequence {
            val name = json.optString("name", "sequence")
            val stepsArray = json.getJSONArray("steps")
            val steps = mutableListOf<ClickStep>()
            for (i in 0 until stepsArray.length()) {
                steps.add(ClickStep.fromJson(stepsArray.getJSONObject(i)))
            }
            return ClickSequence(name, steps)
        }

        fun loadFromFile(context: Context, uri: Uri): ClickSequence? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader().readText()
                    fromJson(JSONObject(text))
                }
            } catch (e: Exception) {
                null
            }
        }

        fun loadFromInternalFile(file: File): ClickSequence? {
            return try {
                fromJson(JSONObject(file.readText()))
            } catch (e: Exception) {
                null
            }
        }
    }
}
