package com.example.accessibilityautomator.automation

import android.content.Context
import android.util.Log
import com.example.accessibilityautomator.models.RecordedStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object ReplayManager {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private var replayJob: Job? = null

    fun play(
        context: Context,
        filePath: String
    ) {
        if (replayJob?.isActive == true) {
            return
        }

        if (!AutomationAccessibilityService.isServiceConnected()) {
            Log.e("ReplayManager", "Cannot start replay: Accessibility Service is NOT connected.")
            return
        }

        replayJob = scope.launch {
            try {
                val file = File(filePath)

                if (!file.exists()) {
                    Log.e("ReplayManager", "Recording file does not exist: $filePath")
                    return@launch
                }

                val jsonString = file.readText()
                val root = JSONObject(jsonString)
                val steps = root.getJSONArray("steps")

                Log.d("ReplayManager", "Starting replay. Total steps: ${steps.length()}")

                for (index in 0 until steps.length()) {
                    val stepJson = steps.getJSONObject(index)

                    val step = RecordedStep(
                        type = stepJson.optString("type"),
                        packageName = stepJson.optString("packageName").takeIf { it.isNotBlank() },
                        className = stepJson.optString("className").takeIf { it.isNotBlank() },
                        text = stepJson.optString("text").takeIf { it.isNotBlank() },
                        resourceId = stepJson.optString("resourceId").takeIf { it.isNotBlank() },
                        x = if (stepJson.isNull("x")) null else stepJson.optInt("x"),
                        y = if (stepJson.isNull("y")) null else stepJson.optInt("y"),
                        timestamp = stepJson.optLong("timestamp"),
                        waitAfterMs = stepJson.optLong("waitAfterMs", 500L)
                    )

                    Log.d("ReplayManager", "Executing step ${index + 1}/${steps.length()}: $step")

                    AutomationAccessibilityService.executeReplayStep(step)

                    delay(step.waitAfterMs)
                }

                Log.d("ReplayManager", "Replay completed successfully")

            } catch (e: Exception) {
                Log.e("ReplayManager", "Replay failed", e)
            } finally {
                replayJob = null
            }
        }
    }

    fun stop() {
        replayJob?.cancel()
        replayJob = null
    }
}