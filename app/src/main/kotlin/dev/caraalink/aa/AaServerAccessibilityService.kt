package dev.caraalink.aa

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.caraalink.MainActivity

/**
 * Automates tapping "Start head unit server" / "Stop head unit server" inside
 * Android Auto's settings screen so the user can toggle the server with one
 * button inside AA Bridge and auto-stop it after an idle timeout.
 */
class AaServerAccessibilityService : AccessibilityService() {

    enum class Action { START, STOP }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastOverflowClickMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val action = pendingAction ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > pendingDeadlineMs) {
            Log.w(TAG, "Pending action $action timed out")
            clearPending()
            return
        }

        val roots = collectRoots()
        if (roots.isEmpty()) return

        val targetLabels = if (action == Action.START) START_LABELS else STOP_LABELS
        val oppositeLabels = if (action == Action.START) STOP_LABELS else START_LABELS

        // 1. If the desired menu entry is already visible, tap it and finish.
        findNodeByLabels(roots, targetLabels)?.let { targetNode ->
            if (clickNodeOrParent(targetNode)) {
                Log.i(TAG, "Clicked menu item for $action")
                finishAction(action)
                return
            }
        }

        // 2. If the opposite entry is visible, the server is already in the desired state.
        if (findNodeByLabels(roots, oppositeLabels) != null) {
            Log.i(TAG, "Server already in desired state for $action")
            performGlobalAction(GLOBAL_ACTION_BACK)
            finishAction(action)
            return
        }

        // 3. Otherwise, open the overflow menu (⋮) in Android Auto's toolbar.
        if (now - lastOverflowClickMs >= OVERFLOW_RETRY_MS) {
            findOverflowNode(roots)?.let { overflow ->
                if (clickNodeOrParent(overflow)) {
                    lastOverflowClickMs = now
                    Log.i(TAG, "Clicked overflow menu button")
                }
            }
        }
    }

    override fun onInterrupt() {
        clearPending()
    }

    private fun finishAction(action: Action) {
        val returnToApp = pendingReturnToApp
        clearPending()
        if (action == Action.START) {
            ServerIdleTimer.arm(applicationContext)
        } else {
            ServerIdleTimer.cancel(applicationContext)
        }
        if (returnToApp) {
            mainHandler.postDelayed({
                val intent = Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                runCatching { startActivity(intent) }
            }, RETURN_DELAY_MS)
        }
    }

    private fun collectRoots(): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { result.add(it) }
        runCatching {
            windows?.forEach { window ->
                window.root?.let { root ->
                    if (result.none { it == root }) result.add(root)
                }
            }
        }
        return result
    }

    private fun findNodeByLabels(
        roots: List<AccessibilityNodeInfo>,
        labels: List<String>,
    ): AccessibilityNodeInfo? {
        for (root in roots) {
            for (label in labels) {
                val matches = root.findAccessibilityNodeInfosByText(label)
                if (!matches.isNullOrEmpty()) {
                    val exact = matches.firstOrNull { node ->
                        val text = node.text?.toString()?.trim()?.lowercase()
                        text != null && labels.any { text.contains(it) }
                    }
                    if (exact != null) return exact
                }
            }
        }
        return null
    }

    private fun findOverflowNode(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (root in roots) {
            val found = findOverflowRecursive(root)
            if (found != null) return found
        }
        return null
    }

    private fun findOverflowRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.trim()?.lowercase()
        if (desc != null && OVERFLOW_DESCRIPTIONS.any { desc.contains(it) }) {
            return node
        }
        val cls = node.className?.toString() ?: ""
        if (cls.contains("OverflowMenuButton", ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findOverflowRecursive(child)
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    companion object {
        private const val TAG = "AaServerA11y"
        private const val ACTION_TIMEOUT_MS = 12_000L
        private const val OVERFLOW_RETRY_MS = 900L
        private const val RETURN_DELAY_MS = 350L

        private val START_LABELS = listOf(
            "запустить сервер головного устройства",
            "start head unit server",
        )
        private val STOP_LABELS = listOf(
            "остановить сервер головного устройства",
            "stop head unit server",
        )
        private val OVERFLOW_DESCRIPTIONS = listOf(
            "другие параметры",
            "дополнительные параметры",
            "ещё",
            "еще",
            "more options",
            "overflow",
        )

        @Volatile private var pendingAction: Action? = null
        @Volatile private var pendingReturnToApp: Boolean = true
        @Volatile private var pendingDeadlineMs: Long = 0L

        private fun clearPending() {
            pendingAction = null
            pendingReturnToApp = true
            pendingDeadlineMs = 0L
        }

        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AaServerAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val item = ComponentName.unflattenFromString(splitter.next())
                if (item != null && item == expected) return true
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        /**
         * Requests starting or stopping Android Auto's head unit server.
         * Returns false if the accessibility service is not enabled yet.
         */
        fun requestAction(
            context: Context,
            action: Action,
            returnToApp: Boolean = true,
        ): Boolean {
            if (!isServiceEnabled(context)) return false
            pendingAction = action
            pendingReturnToApp = returnToApp
            pendingDeadlineMs = SystemClock.elapsedRealtime() + ACTION_TIMEOUT_MS
            val opened = AndroidAutoServer.openSettings(context)
            if (!opened) clearPending()
            return opened
        }
    }
}
