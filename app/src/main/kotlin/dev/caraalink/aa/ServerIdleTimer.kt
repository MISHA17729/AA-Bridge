package dev.caraalink.aa

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.caraalink.MainActivity
import dev.caraalink.R
import dev.caraalink.connection.ConnectionStateHolder
import dev.caraalink.ui.isCarConnected
import kotlin.math.ceil

/**
 * Automatically stops Android Auto's head unit server if no car connects
 * within [IDLE_TIMEOUT_MS] (60 minutes).
 */
object ServerIdleTimer {
    private const val TAG = "ServerIdleTimer"
    private const val PREFS_NAME = "aa_server_idle_timer"
    private const val KEY_DEADLINE_WALL_MS = "deadline_wall_ms"
    private const val KEY_PENDING_STOP = "pending_stop_on_unlock"
    private const val CHANNEL_ID = "aa_server_idle"
    private const val NOTIFICATION_ID = 5
    const val ACTION_IDLE_TIMEOUT = "dev.caraalink.aa.ACTION_SERVER_IDLE_TIMEOUT"

    const val IDLE_TIMEOUT_MS = 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, ServerIdleReceiver::class.java)
            .setAction(ACTION_IDLE_TIMEOUT)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun arm(context: Context, delayMs: Long = IDLE_TIMEOUT_MS) {
        val appContext = context.applicationContext
        val deadlineWall = System.currentTimeMillis() + delayMs
        prefs(appContext).edit()
            .putLong(KEY_DEADLINE_WALL_MS, deadlineWall)
            .putBoolean(KEY_PENDING_STOP, false)
            .apply()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            alarmPendingIntent(appContext),
        )
        Log.i(TAG, "Armed server idle timeout for ${delayMs / 60_000L} min")
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(appContext))
        prefs(appContext).edit()
            .remove(KEY_DEADLINE_WALL_MS)
            .putBoolean(KEY_PENDING_STOP, false)
            .apply()
        appContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun remainingMinutes(context: Context): Int? {
        val deadline = prefs(context).getLong(KEY_DEADLINE_WALL_MS, 0L)
        if (deadline <= 0L) return null
        val remainingMs = deadline - System.currentTimeMillis()
        if (remainingMs <= 0L) return null
        return ceil(remainingMs / 60_000.0).toInt().coerceAtLeast(1)
    }

    fun isPendingStopOnUnlock(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_STOP, false)

    internal fun markPendingStopOnUnlock(context: Context) {
        prefs(context).edit()
            .remove(KEY_DEADLINE_WALL_MS)
            .putBoolean(KEY_PENDING_STOP, true)
            .apply()
    }

    fun onCarConnectionChanged(context: Context, connected: Boolean) {
        val appContext = context.applicationContext
        if (connected) {
            cancel(appContext)
            return
        }
        Thread({
            if (AndroidAutoServer.isRunning() && remainingMinutes(appContext) == null) {
                arm(appContext)
            }
        }, "aa-idle-check").start()
    }

    fun tryPendingStopOnUnlock(context: Context) {
        val appContext = context.applicationContext
        if (!isPendingStopOnUnlock(appContext)) return
        Thread({
            if (ConnectionStateHolder.snapshot.phase.isCarConnected()) {
                cancel(appContext)
                return@Thread
            }
            if (!AndroidAutoServer.isRunning()) {
                cancel(appContext)
                return@Thread
            }
            if (AaServerAccessibilityService.isServiceEnabled(appContext)) {
                prefs(appContext).edit().putBoolean(KEY_PENDING_STOP, false).apply()
                Handler(Looper.getMainLooper()).post {
                    AaServerAccessibilityService.requestAction(
                        appContext,
                        AaServerAccessibilityService.Action.STOP,
                        returnToApp = false,
                    )
                }
            }
        }, "aa-idle-unlock-stop").start()
    }

    internal fun notifyIdleTimeout(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val open = PendingIntent.getActivity(
            appContext,
            1002,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(appContext.getString(R.string.notif_idle_stop_title))
            .setContentText(appContext.getString(R.string.notif_idle_stop_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

class ServerIdleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ServerIdleTimer.ACTION_IDLE_TIMEOUT) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread({
            try {
                if (ConnectionStateHolder.snapshot.phase.isCarConnected()) {
                    ServerIdleTimer.cancel(appContext)
                    return@Thread
                }
                if (!AndroidAutoServer.isRunning()) {
                    ServerIdleTimer.cancel(appContext)
                    return@Thread
                }
                val pm = appContext.getSystemService(PowerManager::class.java)
                val km = appContext.getSystemService(KeyguardManager::class.java)
                val unlocked = pm.isInteractive && !km.isKeyguardLocked
                if (unlocked && AaServerAccessibilityService.isServiceEnabled(appContext)) {
                    ServerIdleTimer.cancel(appContext)
                    Handler(Looper.getMainLooper()).post {
                        AaServerAccessibilityService.requestAction(
                            appContext,
                            AaServerAccessibilityService.Action.STOP,
                            returnToApp = false,
                        )
                    }
                } else {
                    ServerIdleTimer.markPendingStopOnUnlock(appContext)
                    ServerIdleTimer.notifyIdleTimeout(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }, "aa-idle-timeout").start()
    }
}
