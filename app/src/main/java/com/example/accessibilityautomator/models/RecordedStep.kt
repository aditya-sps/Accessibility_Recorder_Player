package com.example.accessibilityautomator.models

data class RecordedStep(
    val type: String,
    val packageName: String?,
    val className: String?,
    val text: String?,
    val resourceId: String?,
    val x: Int?,
    val y: Int?,
    val timestamp: Long,
    val waitAfterMs: Long = 300L
)
