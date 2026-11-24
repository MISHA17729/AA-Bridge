package dev.caraalink.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.WindowManager

/**
 * Mirrors the phone's own screen directly into [CarVideoEncoder]'s input surface
 * via Android's MediaProjection API, and maps car touchscreen coordinates back to
 * the phone's physical screen coordinates for AccessibilityService gesture dispatch.
 */
object PhoneMirrorController {
    private const val TAG = "PhoneMirrorController"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var permissionResultCode: Int = 0
    @Volatile private var permissionData: Intent? = null
    @Volatile private var projection: MediaProjection? = null
    @Volatile private var virtualDisplay: VirtualDisplay? = null

    @Volatile var isActive: Boolean = false
        private set

    fun hasPermission(): Boolean = permissionResultCode == Activity.RESULT_OK && permissionData != null

    fun setPermission(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            permissionResultCode = resultCode
            permissionData = Intent(data)
            Log.i(TAG, "Screen capture permission stored")
        } else {
            clearPermission()
        }
    }

    fun clearPermission() {
        permissionResultCode = 0
        permissionData = null
    }

    /**
     * Starts mirroring the phone screen onto [surface] at [width]x[height].
     * Must be called while a foreground service with type mediaProjection is active.
     */
    fun start(context: Context, surface: Surface, width: Int, height: Int): Boolean {
        val data = permissionData ?: return false
        if (permissionResultCode != Activity.RESULT_OK) return false
        stop()

        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return false
        val mp = try {
            mpm.getMediaProjection(permissionResultCode, Intent(data))
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            clearPermission()
            null
        } ?: return false

        // Android 14+ requires registering a callback before createVirtualDisplay.
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by system")
                isActive = false
                virtualDisplay?.release()
                virtualDisplay = null
                projection = null
            }
        }, mainHandler)

        val dpi = context.resources.displayMetrics.densityDpi.coerceIn(160, 480)
        val vd = try {
            mp.createVirtualDisplay(
                "GeelyLinkMirror",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                mainHandler,
            )
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed", e)
            runCatching { mp.stop() }
            clearPermission()
            null
        } ?: return false

        projection = mp
        virtualDisplay = vd
        isActive = true
        Log.i(TAG, "Phone screen mirror started at ${width}x${height} dpi=$dpi")
        return true
    }

    fun stop() {
        isActive = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.stop() }
        projection = null
    }

    /**
     * Maps car touchscreen coordinates (in car screen pixels [carW]x[carH]) to
     * phone screen coordinates, accounting for centered letterbox/pillarbox scaling
     * used by VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR.
     */
    fun mapCarTouchToPhone(
        context: Context,
        carX: Int,
        carY: Int,
        carW: Int,
        carH: Int,
    ): Pair<Float, Float>? {
        if (carW <= 0 || carH <= 0) return null
        val (phoneW, phoneH) = currentPhoneScreenSize(context)
        if (phoneW <= 0 || phoneH <= 0) return null

        val scale = minOf(carW.toFloat() / phoneW, carH.toFloat() / phoneH)
        val drawnW = phoneW * scale
        val drawnH = phoneH * scale
        val offsetX = (carW - drawnW) / 2f
        val offsetY = (carH - drawnH) / 2f

        val px = ((carX - offsetX) / scale).coerceIn(0f, (phoneW - 1).toFloat())
        val py = ((carY - offsetY) / scale).coerceIn(0f, (phoneH - 1).toFloat())
        return px to py
    }

    private fun currentPhoneScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }
}
