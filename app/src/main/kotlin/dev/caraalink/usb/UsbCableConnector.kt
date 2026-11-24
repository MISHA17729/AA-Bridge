package dev.caraalink.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import dev.caraalink.service.UsbLinkForegroundService

/**
 * Probes already-attached car USB accessories and starts the wired session
 * (or requests USB permission first), so wired mode works reliably even when
 * the cable was plugged in before the app opened or after wireless was used.
 */
object UsbCableConnector {
    private const val TAG = "UsbCableConnector"
    const val ACTION_USB_PERMISSION = "dev.caraalink.USB_PERMISSION"

    enum class Result { STARTED, PERMISSION_REQUESTED, NO_ACCESSORY }

    fun attachedAccessory(context: Context): UsbAccessory? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        return runCatching { usbManager.accessoryList?.firstOrNull() }.getOrNull()
    }

    fun startSession(context: Context, accessory: UsbAccessory) {
        val serviceIntent = Intent(context, UsbLinkForegroundService::class.java)
            .setAction(UsbLinkForegroundService.ACTION_START_SESSION)
            .putExtra(UsbManager.EXTRA_ACCESSORY, accessory)
        context.startForegroundService(serviceIntent)
    }

    /** Starts wired session immediately if an accessory is already attached and permitted. */
    fun connectIfPermitted(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val accessory = attachedAccessory(context) ?: return false
        if (!usbManager.hasPermission(accessory)) return false
        Log.i(TAG, "connectIfPermitted: starting wired session for ${accessory.manufacturer}/${accessory.model}")
        startSession(context, accessory)
        return true
    }

    /** Connects over USB or asks Android for accessory permission if not yet granted. */
    fun connect(context: Context): Result {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return Result.NO_ACCESSORY
        val accessory = attachedAccessory(context) ?: return Result.NO_ACCESSORY
        if (usbManager.hasPermission(accessory)) {
            Log.i(TAG, "connect: permission already granted, starting wired session")
            startSession(context, accessory)
            return Result.STARTED
        }
        Log.i(TAG, "connect: requesting USB permission for ${accessory.manufacturer}/${accessory.model}")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permIntent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, 2001, permIntent, flags)
        usbManager.requestPermission(accessory, pendingIntent)
        return Result.PERMISSION_REQUESTED
    }
}
