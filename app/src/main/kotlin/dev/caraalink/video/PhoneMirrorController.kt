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
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface

/**
 * Mirrors the phone's own screen directly into [CarVideoEncoder]'s input surface
 * via Android's MediaProjection API.
 *
 * Android 14+ enforces strictly single-use MediaProjection consent tokens:
 * [permissionData] is consumed once in [start] and immediately cleared so the
 * next session cleanly asks for a fresh consent dialog instead of crashing with
 * SecurityException. Within an already-active session, [updateSurface] reuses
 * the live [MediaProjection] if the car sends a new CONFIG_CAPTURE.
 */
object PhoneMirrorController {
    private const val TAG = "PhoneMirrorController"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile private var permissionResultCode: Int = 0
    @Volatile private var permissionData: Intent? = null
    @Volatile private var projection: MediaProjection? = null
    @Volatile private var virtualDisplay: VirtualDisplay? = null

    @Volatile var isActive: Boolean = false
        private set

    /** True if we have either a live projection or a fresh unused consent token. */
    fun hasPermission(): Boolean = synchronized(lock) {
        projection != null || (permissionResultCode == Activity.RESULT_OK && permissionData != null)
    }

    fun setPermission(resultCode: Int, data: Intent?) = synchronized(lock) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            permissionResultCode = resultCode
            permissionData = Intent(data)
            Log.i(TAG, "Fresh screen capture consent stored")
        } else {
            clearTokenLocked()
        }
    }

    private fun clearTokenLocked() {
        permissionResultCode = 0
        permissionData = null
    }

    /**
     * Attaches [surface] at [width]x[height] to the live MediaProjection, or
     * creates one from the stored single-use consent token.
     */
    fun startOrUpdate(context: Context, surface: Surface, width: Int, height: Int): Boolean = synchronized(lock) {
        val dpi = context.resources.displayMetrics.densityDpi.coerceIn(160, 480)

        // Reuse live projection if this is a mid-session encoder rebuild.
        val existingProjection = projection
        if (existingProjection != null) {
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            val vd = createMirrorDisplay(existingProjection, surface, width, height, dpi)
            if (vd != null) {
                virtualDisplay = vd
                isActive = true
                Log.i(TAG, "Reattached live MediaProjection to ${width}x${height}")
                return true
            }
            stopLocked()
            return false
        }

        val data = permissionData ?: return false
        val resultCode = permissionResultCode
        if (resultCode != Activity.RESULT_OK) return false

        // Consume token immediately — Android 14+ rejects reusing it.
        clearTokenLocked()

        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return false
        val mp = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            null
        } ?: return false

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by system")
                synchronized(lock) {
                    isActive = false
                    runCatching { virtualDisplay?.release() }
                    virtualDisplay = null
                    projection = null
                }
            }
        }, mainHandler)

        val vd = createMirrorDisplay(mp, surface, width, height, dpi)
        if (vd == null) {
            runCatching { mp.stop() }
            return false
        }

        projection = mp
        virtualDisplay = vd
        isActive = true
        Log.i(TAG, "Phone screen mirror started at ${width}x${height} dpi=$dpi")
        return true
    }

    private fun createMirrorDisplay(
        mp: MediaProjection,
        surface: Surface,
        width: Int,
        height: Int,
        dpi: Int,
    ): VirtualDisplay? = try {
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
        null
    }

    fun stop() = synchronized(lock) {
        stopLocked()
    }

    private fun stopLocked() {
        isActive = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.stop() }
        projection = null
        clearTokenLocked()
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

        val px = ((carX - offsetX) / scale).coerceIn(1f, (phoneW - 2).toFloat())
        val py = ((carY - offsetY) / scale).coerceIn(1f, (phoneH - 2).toFloat())
        return px to py
    }

    private fun currentPhoneScreenSize(context: Context): Pair<Int, Int> {
        runCatching {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    return metrics.widthPixels to metrics.heightPixels
                }
            }
        }
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }
}
