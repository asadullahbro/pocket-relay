package com.pocketrelay.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Only used when the control panel asks it to tap a Quick Settings tile; ignores all events. */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: RemoteAccessibilityService? = null
    }

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Blocking: call off the main thread. Each tap is its own open-Quick-Settings, tap, close cycle. */
    fun tapTile(labels: List<String>, times: Int, gapMs: Long): Boolean {
        var ok = true
        for (i in 0 until times) {
            if (!tapOnce(labels)) { ok = false; break }
            if (i < times - 1) Thread.sleep(gapMs)
        }
        return ok
    }

    private fun tapOnce(labels: List<String>): Boolean {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        Thread.sleep(1800)
        val node = findTile(labels)
        val ok = node != null && click(node)
        Thread.sleep(600)
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        return ok
    }

    private fun findTile(labels: List<String>): AccessibilityNodeInfo? {
        repeat(5) {
            rootInActiveWindow?.let { root -> search(root, labels)?.let { return it } }
            Thread.sleep(400)
        }
        return null
    }

    private fun search(node: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val text = "${node.text ?: ""} ${node.contentDescription ?: ""}"
        if (labels.any { text.contains(it, ignoreCase = true) }) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> search(child, labels)?.let { return it } }
        }
        return null
    }

    private fun click(start: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = start
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }
}
