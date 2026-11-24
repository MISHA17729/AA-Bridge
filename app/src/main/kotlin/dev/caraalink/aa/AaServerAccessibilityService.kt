package dev.caraalink.aa

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
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
 * Android Auto's settings screen using both accessibility events and an active
 * polling loop, with a geometric top-right fallback for the ⋮ overflow button.
 */
class AaServerAccessibilityService : AccessibilityService() {

    enum class Action { START, STOP }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastOverflowClickMs = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (pendingAction == null) return
            if (SystemClock.elapsedRealtime() > pendingDeadlineMs) {
                Log.w(TAG, "Pending action $pendingAction timed out")
                clearPending()
                return
            }
            stepAutomation()
            if (pendingAction != null) {
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
        if (pendingAction != null) {
            schedulePolling()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        mainHandler.removeCallbacks(pollRunnable)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        mainHandler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pendingAction == null) return
        stepAutomation()
    }

    override fun onInterrupt() {
        clearPending()
        mainHandler.removeCallbacks(pollRunnable)
    }

    internal fun schedulePolling() {
        lastOverflowClickMs = 0L
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, 250L)
    }

    private fun stepAutomation() {
        val action = pendingAction ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > pendingDeadlineMs) {
            Log.w(TAG, "Pending action $action timed out")
            clearPending()
            return
        }

        val roots = collectRoots()
        if (roots.isEmpty()) return

        // 1. If the desired menu item is already visible in the popup, tap it and finish.
        findMenuItem(roots, action)?.let { targetNode ->
            if (clickNodeOrParent(targetNode)) {
                Log.i(TAG, "Clicked menu item for $action")
                finishAction(action)
                return
            }
        }

        // 2. If the opposite menu item is visible, the server is already in the desired state.
        val opposite = if (action == Action.START) Action.STOP else Action.START
        if (findMenuItem(roots, opposite) != null) {
            Log.i(TAG, "Server already in desired state for $action")
            performGlobalAction(GLOBAL_ACTION_BACK)
            finishAction(action)
            return
        }

        // 3. Only tap the overflow menu when Android Auto's settings screen is active.
        val onAaScreen = roots.any { root ->
            val pkg = root.packageName?.toString() ?: ""
            pkg == ANDROID_AUTO_PACKAGE
        }
        if (!onAaScreen) return

        if (now - lastOverflowClickMs >= OVERFLOW_RETRY_MS) {
            val overflow = findOverflowNode(roots) ?: findTopRightOverflowFallback(roots)
            if (overflow != null && clickNodeOrParent(overflow)) {
                lastOverflowClickMs = now
                Log.i(TAG, "Clicked overflow menu button")
            }
        }
    }

    private fun finishAction(action: Action) {
        val returnToApp = pendingReturnToApp
        clearPending()
        mainHandler.removeCallbacks(pollRunnable)
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
        runCatching {
            windows?.forEach { window ->
                window.root?.let { root ->
                    if (result.none { it == root }) result.add(root)
                }
            }
        }
        rootInActiveWindow?.let { active ->
            if (result.none { it == active }) result.add(active)
        }
        return result
    }

    private fun findMenuItem(roots: List<AccessibilityNodeInfo>, action: Action): AccessibilityNodeInfo? {
        for (root in roots) {
            val found = findMenuItemRecursive(root, action)
            if (found != null) return found
        }
        return null
    }

    private fun findMenuItemRecursive(node: AccessibilityNodeInfo, action: Action): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.trim()?.lowercase()
        if (text != null && matchesActionLabel(text, action)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findMenuItemRecursive(child, action)
            if (found != null) return found
        }
        return null
    }

    private fun matchesActionLabel(text: String, action: Action): Boolean = when (action) {
        Action.START -> (text.contains("запустить") && text.contains("сервер")) ||
            (text.contains("start") && text.contains("head unit server"))
        Action.STOP -> (text.contains("остановить") && text.contains("сервер")) ||
            (text.contains("stop") && text.contains("head unit server"))
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

    /**
     * Fallback for localized / One UI toolbars where ⋮ has no expected contentDescription:
     * picks the rightmost clickable icon in the top toolbar band.
     */
    private fun findTopRightOverflowFallback(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val dm = resources.displayMetrics
        val maxTop = (dm.heightPixels * 0.20f).toInt()
        val minLeft = (dm.widthPixels * 0.72f).toInt()
        val maxWidth = (dm.widthPixels * 0.28f).toInt()
        var best: AccessibilityNodeInfo? = null
        var bestRight = -1

        fun scan(node: AccessibilityNodeInfo) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val cls = node.className?.toString() ?: ""
            val looksLikeIcon = cls.contains("Image", ignoreCase = true) ||
                cls.contains("Button", ignoreCase = true) ||
                cls.contains("ActionMenu", ignoreCase = true)
            if (node.isClickable && looksLikeIcon &&
                rect.top in 0..maxTop &&
                rect.left >= minLeft &&
                rect.width() in 1..maxWidth &&
                rect.height() in 1..maxTop
            ) {
                if (rect.right > bestRight) {
                    bestRight = rect.right
                    best = node
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scan(child)
            }
        }

        for (root in roots) {
            if (root.packageName?.toString() == ANDROID_AUTO_PACKAGE) {
                scan(root)
            }
        }
        return best
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
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
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        private const val ACTION_TIMEOUT_MS = 12_000L
        private const val POLL_INTERVAL_MS = 300L
        private const val OVERFLOW_RETRY_MS = 850L
        private const val RETURN_DELAY_MS = 400L

        private val OVERFLOW_DESCRIPTIONS = listOf(
            "другие параметры",
            "дополнительные параметры",
            "дополнительные функции",
            "ещё",
            "еще",
            "more options",
            "overflow",
        )

        @Volatile private var instance: AaServerAccessibilityService? = null
        @Volatile private var pendingAction: Action? = null
        @Volatile private var pendingReturnToApp: Boolean = true
        @Volatile private var pendingDeadlineMs: Long = 0L

        private fun clearPending() {
            pendingAction = null
            pendingReturnToApp = true
            pendingDeadlineMs = 0L
        }

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
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
            instance?.schedulePolling()
            val opened = AndroidAutoServer.openSettings(context)
            if (!opened) clearPending()
            return opened
        }
    }
}
