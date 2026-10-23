package com.example.accessibilityautomator.automation

import android.content.Context
import android.util.Log
import com.example.accessibilityautomator.models.RecordedStep
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RecordingManager {

    @Volatile
    var isRecording = false
        private set

    private val steps = mutableListOf<RecordedStep>()

    @Synchronized
    fun startRecording() {
        steps.clear()
        isRecording = true
    }

    @Synchronized
    fun addStep(step: RecordedStep) {
        if (!isRecording) return

        if (step.type == "text_changed") {
            val lastStep = steps.lastOrNull()
            if (lastStep != null && lastStep.type == "text_changed" && isSameTargetField(lastStep, step)) {
                // Update the last text_changed step with updated text for the same field
                steps[steps.size - 1] = step
                return
            }
        }

        steps.add(step)
    }

    private fun isSameTargetField(step1: RecordedStep, step2: RecordedStep): Boolean {
        // If both have non-blank resource IDs, check if they match
        if (!step1.resourceId.isNullOrBlank() && !step2.resourceId.isNullOrBlank()) {
            return step1.resourceId == step2.resourceId
        }

        // If coordinates are available for both, check if they are spatially close (same field)
        if (step1.x != null && step1.y != null && step2.x != null && step2.y != null) {
            val deltaX = Math.abs(step1.x - step2.x)
            val deltaY = Math.abs(step1.y - step2.y)
            return deltaX < 60 && deltaY < 60
        }

        // Fallback: compare package name and class name
        return step1.packageName == step2.packageName && step1.className == step2.className
    }

    @Synchronized
    fun stopAndSave(context: Context): File? {

        isRecording = false

        val jsonArray = JSONArray()

        steps.forEach { step ->

            val item = JSONObject().apply {
                put("type", step.type)
                put("packageName", step.packageName)
                put("className", step.className)
                put("text", step.text)
                put("resourceId", step.resourceId)
                put("x", step.x)
                put("y", step.y)
                put("timestamp", step.timestamp)
                put("waitAfterMs", step.waitAfterMs)
            }

            jsonArray.put(item)
        }

        val jsonObject = JSONObject().apply {
            put("steps", jsonArray)
        }

        val jsonString = jsonObject.toString(2)

        val file = File(
            context.filesDir,
            "recording_${System.currentTimeMillis()}.json"
        )

        return runCatching {

            file.writeText(jsonString)

            Log.d("RecordingManager", "========== RECORDING JSON ==========")
            Log.d("RecordingManager", jsonString)
            Log.d("RecordingManager", "========== END RECORDING JSON ==========")
            Log.d("RecordingManager", "File saved at: ${file.absolutePath}")

            file

        }.onFailure { error ->

            Log.e("RecordingManager", "Failed to save recording", error)

        }.getOrNull()
    }
}