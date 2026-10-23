package com.example.accessibilityautomator.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibilityautomator.models.RecordedStep

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: AutomationAccessibilityService? = null

        fun isServiceConnected(): Boolean = instance != null

        fun executeReplayStep(step: RecordedStep) {
            val service = instance
            if (service != null) {
                service.performReplayStep(step)
            } else {
                Log.e("AutomationService", "Cannot execute replay step: service instance is null")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutomationService", "Accessibility service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d("AutomationService", "Accessibility service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Log.d("AutomationService", "Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !RecordingManager.isRecording) return

        val eventType = event.eventType
        val packageName = event.packageName?.toString()

        val type = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "text_changed"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll"
            else -> return
        }

        val node = event.source

        val text = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> event.text.joinToString("")
            else -> node?.text?.toString()
                ?: node?.hintText?.toString()
                ?: node?.contentDescription?.toString()
                ?: event.text.joinToString("").ifBlank { null }
        }

        // Ignore recorder control actions in our app
        if (packageName == this.packageName) {
            if (text == "Start Recording" || text == "Stop and Save" || text == "Play Last Recording") {
                return
            }
        }

        val bounds = Rect()
        node?.getBoundsInScreen(bounds)

        val step = RecordedStep(
            type = type,
            packageName = packageName,
            className = node?.className?.toString() ?: event.className?.toString(),
            text = text,
            resourceId = node?.viewIdResourceName,
            x = if (node != null && bounds.width() > 0 && bounds.height() > 0) bounds.centerX() else null,
            y = if (node != null && bounds.width() > 0 && bounds.height() > 0) bounds.centerY() else null,
            timestamp = System.currentTimeMillis()
        )

        RecordingManager.addStep(step)

        Log.d(
            "UIRecorder",
            "Captured: $type | pkg=$packageName | id=${step.resourceId} | text=$text | pos=(${step.x}, ${step.y})"
        )
    }

    private fun performReplayStep(step: RecordedStep) {
        when (step.type) {
            "click" -> performClick(step)
            "text_changed" -> performTextInput(step)
            "scroll" -> performScroll(step)
            "back" -> performGlobalBack()
            "home" -> performGlobalHome()
            else -> Log.d("AutomationService", "Ignoring unsupported replay step: ${step.type}")
        }
    }

    private fun performClick(step: RecordedStep) {
        val rootNode = rootInActiveWindow

        if (rootNode == null) {
            Log.e("AutomationService", "Root node is null during click replay. Attempting coordinate fallback.")
            performCoordinateClick(step)
            return
        }

        var targetNode: AccessibilityNodeInfo? = null

        // 1. Try resource ID
        step.resourceId?.let { resourceId ->
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
            targetNode = nodes.firstOrNull()
        }

        // 2. Try text
        if (targetNode == null && !step.text.isNullOrBlank()) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(step.text)
            targetNode = nodes.firstOrNull()
        }

        // 3. Try spatial search by coordinates (x, y)
        if (targetNode == null && step.x != null && step.y != null) {
            targetNode = findNodeAtCoordinates(rootNode, step.x, step.y, mustBeEditableOrClickable = true)
        }

        // 4. Try clicking node or clickable parent
        if (targetNode != null) {
            var clickableNode: AccessibilityNodeInfo? = targetNode
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }

            val nodeToClick = clickableNode ?: targetNode
            val clicked = nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (clicked) {
                Log.d("AutomationService", "Element click action succeeded via accessibility node")
                return
            } else {
                Log.w("AutomationService", "Element ACTION_CLICK returned false. Falling back to coordinate click.")
            }
        }

        // 5. Coordinate fallback
        Log.d("AutomationService", "Element not clickable via node. Using coordinate fallback.")
        performCoordinateClick(step)
    }

    private fun performCoordinateClick(step: RecordedStep) {
        val x = step.x ?: run {
            Log.e("AutomationService", "Cannot perform coordinate click: x is null")
            return
        }
        val y = step.y ?: run {
            Log.e("AutomationService", "Cannot perform coordinate click: y is null")
            return
        }

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d("AutomationService", "Coordinate click completed at ($x, $y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w("AutomationService", "Coordinate click cancelled at ($x, $y)")
                }
            },
            null
        )
    }

    private fun performTextInput(step: RecordedStep) {
        val rootNode = rootInActiveWindow ?: run {
            Log.e("AutomationService", "Root node is null during text input replay")
            return
        }

        var targetNode: AccessibilityNodeInfo? = null

        // 1. Find using resource ID first
        step.resourceId?.let { resourceId ->
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
            targetNode = nodes.firstOrNull()
        }

        // 2. Spatial search by coordinates (x, y)
        if (targetNode == null && step.x != null && step.y != null) {
            targetNode = findNodeAtCoordinates(rootNode, step.x, step.y, mustBeEditableOrClickable = true)
        }

        // 3. Locate editable/focusable node
        var editableNode: AccessibilityNodeInfo? = targetNode
        while (editableNode != null && !editableNode.isEditable && !editableNode.isFocusable) {
            editableNode = editableNode.parent
        }
        var nodeToSet = editableNode ?: targetNode

        // 4. Fallback: if no target node found or node cannot be focused, perform coordinate tap first
        if (nodeToSet == null && step.x != null && step.y != null) {
            performCoordinateClick(step)
            try { Thread.sleep(150) } catch (_: Exception) {}
            val newRoot = rootInActiveWindow
            nodeToSet = newRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: (if (newRoot != null) findNodeAtCoordinates(newRoot, step.x, step.y, true) else null)
        }

        if (nodeToSet == null) {
            nodeToSet = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (nodeToSet == null) {
            Log.e("AutomationService", "Text input target node not found")
            return
        }

        nodeToSet.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                step.text ?: ""
            )
        }

        var result = nodeToSet.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )

        // If setting text failed on target node, try parent node
        if (!result && nodeToSet.parent != null) {
            result = nodeToSet.parent.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
        }

        Log.d("AutomationService", "Text input result: $result for text '${step.text}'")
    }

    private fun findNodeAtCoordinates(
        root: AccessibilityNodeInfo?,
        x: Int,
        y: Int,
        mustBeEditableOrClickable: Boolean = false
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val bounds = Rect()
        root.getBoundsInScreen(bounds)

        if (!bounds.contains(x, y)) {
            return null
        }

        var bestMatch: AccessibilityNodeInfo? = null
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val childMatch = findNodeAtCoordinates(child, x, y, mustBeEditableOrClickable)
            if (childMatch != null) {
                bestMatch = childMatch
                break
            }
        }

        if (bestMatch != null) {
            return bestMatch
        }

        if (mustBeEditableOrClickable) {
            if (root.isEditable || root.isClickable || root.isFocusable) {
                return root
            }
            var parent = root.parent
            while (parent != null) {
                if (parent.isEditable || parent.isClickable || parent.isFocusable) {
                    return parent
                }
                parent = parent.parent
            }
        }

        return root
    }

    private fun performScroll(step: RecordedStep) {
        val rootNode = rootInActiveWindow ?: return

        val scrollableNode = findScrollableNode(rootNode)

        if (scrollableNode == null) {
            Log.e("AutomationService", "Scrollable node not found")
            return
        }

        val result = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        Log.d("AutomationService", "Scroll result: $result")
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findScrollableNode(child)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun performGlobalBack() {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.d("AutomationService", "Back result: $result")
    }

    private fun performGlobalHome() {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.d("AutomationService", "Home result: $result")
    }

    override fun onInterrupt() {
        Log.w("AutomationService", "Accessibility service interrupted")
    }
}