package dev.caraalink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.caraalink.aa.AaServerAccessibilityService
import dev.caraalink.aa.AaSessionProbe
import dev.caraalink.aa.AaTlsReceiver
import dev.caraalink.aa.AaVersionProbe
import dev.caraalink.aa.AndroidAutoServer
import dev.caraalink.connection.ConnectionStateHolder
import dev.caraalink.settings.AppSettings
import dev.caraalink.ui.AboutScreen
import dev.caraalink.ui.HelpScreen
import dev.caraalink.ui.HomeScreen
import dev.caraalink.ui.NearbyCarsScreen
import dev.caraalink.ui.SettingsScreen
import dev.caraalink.ui.SetupScreen
import dev.caraalink.ui.isCarConnected
import dev.caraalink.ui.theme.AaBridgeTheme
import dev.caraalink.usb.UsbCableConnector

enum class Screen { SETUP, HOME, SETTINGS, HELP, ABOUT, NEARBY }

/** The phone app: one Compose activity; also where Android sends the car's USB attach. */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbCableConnector.ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            @Suppress("DEPRECATION")
            val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                ?: UsbCableConnector.attachedAccessory(context)
            if (granted && accessory != null) {
                Log.i(TAG, "USB permission granted -- starting wired session")
                AppSettings.setConnectionMode(context, AppSettings.ConnectionMode.CABLE)
                UsbCableConnector.startSession(context, accessory)
            } else {
                Log.w(TAG, "USB permission denied or accessory gone")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val usbFilter = IntentFilter(UsbCableConnector.ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, usbFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbPermissionReceiver, usbFilter)
        }
        Log.i(TAG, "onCreate: launch intent action=${intent?.action} extras=${intent?.extras}")
        dev.caraalink.diagnostics.CrashMarker.consumeLastCrash(this)?.let { summary ->
            Toast.makeText(this, "Restarted after a crash: $summary", Toast.LENGTH_LONG).show()
        }
        dev.caraalink.update.UpdateChecker.checkIfDue(this)
        maybeAutoStartOnAttach(intent)
        maybeStartAaTls(intent)
        val firstScreen = if (AppSettings.setupComplete(this)) Screen.HOME else Screen.SETUP
        setContent {
            AaBridgeTheme {
                var screen by rememberSaveable { mutableStateOf(firstScreen) }
                var fromSettings by rememberSaveable { mutableStateOf(false) }
                var nearbyFrom by rememberSaveable { mutableStateOf(Screen.HOME) }
                val back = {
                    screen = when {
                        screen == Screen.NEARBY -> nearbyFrom
                        fromSettings && (screen == Screen.HELP || screen == Screen.ABOUT) -> Screen.SETTINGS
                        else -> Screen.HOME
                    }
                    if (screen == Screen.HOME) fromSettings = false
                }
                val openNearby = { from: Screen -> nearbyFrom = from; screen = Screen.NEARBY }
                BackHandler(enabled = screen != Screen.HOME && screen != Screen.SETUP) { back() }
                when (screen) {
                    Screen.SETUP -> SetupScreen(onDone = { screen = Screen.HOME }, onOpenNearby = { openNearby(Screen.SETUP) })
                    Screen.HOME -> HomeScreen(
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenNearby = { openNearby(Screen.HOME) },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        onBack = back,
                        onHelp = { fromSettings = true; screen = Screen.HELP },
                        onAbout = { fromSettings = true; screen = Screen.ABOUT },
                    )
                    Screen.HELP -> HelpScreen(onBack = back, onRunSetup = { fromSettings = false; screen = Screen.SETUP })
                    Screen.ABOUT -> AboutScreen(onBack = back)
                    Screen.NEARBY -> NearbyCarsScreen(onBack = back, onConnecting = { screen = Screen.HOME })
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbPermissionReceiver) }
        super.onDestroy()
    }

    private fun maybeStartAaTls(intent: Intent?) {
        val probe = intent?.getStringExtra("aa_probe")
        if (probe != null) {
            if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                when (probe) {
                    "stop" -> { AaVersionProbe.active?.stop(); AaSessionProbe.active?.stop() }
                    "session" -> if (AaSessionProbe.active == null) {
                        val cfg = AaSessionProbe.Config(
                            versionMajor = intent.getIntExtra("aa_vmajor", -1).takeIf { it >= 0 },
                            versionMinor = intent.getIntExtra("aa_vminor", -1).takeIf { it >= 0 },
                        )
                        AaSessionProbe(this, cfg).start()
                    }
                    else -> if (AaVersionProbe.active == null) AaVersionProbe(this).start()
                }
            }
            return
        }

        val op = intent?.getStringExtra("aa_tls") ?: return
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            Log.w(TAG, "aa_tls='$op' ignored: not a debuggable build")
            return
        }

        if (op == "stop") {
            Log.i(TAG, "aa_tls: stopping receiver")
            AaTlsReceiver.active?.stop()
            Toast.makeText(this, "AA TLS receiver stopped", Toast.LENGTH_SHORT).show()
            return
        }

        if (AaTlsReceiver.active != null) {
            Log.w(TAG, "aa_tls: receiver already running, ignoring '$op'")
            Toast.makeText(this, "AA TLS receiver already running", Toast.LENGTH_SHORT).show()
            return
        }

        val mode = when (intent.getStringExtra("aa_mode")?.lowercase()) {
            "listen" -> AaTlsReceiver.Mode.LISTEN
            else -> AaTlsReceiver.Mode.DIAL
        }
        val role = when (intent.getStringExtra("aa_role")?.lowercase()) {
            "server" -> AaTlsReceiver.TlsRole.SERVER
            "client" -> AaTlsReceiver.TlsRole.CLIENT
            else -> AaTlsReceiver.TlsRole.AUTO
        }
        val config = AaTlsReceiver.Config(
            mode = mode,
            host = intent.getStringExtra("aa_host") ?: "127.0.0.1",
            port = intent.getIntExtra("aa_port", 5277),
            tlsRole = role,
        )
        Log.i(TAG, "aa_tls: starting receiver $config")
        AaTlsReceiver(this, config).start()
        Toast.makeText(this, "AA TLS receiver: ${mode.name}/${role.name}", Toast.LENGTH_SHORT).show()
    }

    private fun maybeAutoStartOnAttach(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_ACCESSORY_ATTACHED) return
        @Suppress("DEPRECATION")
        val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY) ?: return
        Log.i(TAG, "maybeAutoStartOnAttach: launched via attach intent, starting car session")
        AppSettings.setConnectionMode(this, AppSettings.ConnectionMode.CABLE)
        UsbCableConnector.startSession(this, accessory)
    }

    override fun onStart() {
        super.onStart()
        // Never collide with an in-progress server toggle or an active connection.
        if (AaServerAccessibilityService.isBusy()) return
        val snapshot = ConnectionStateHolder.snapshot
        if (snapshot.phase.isCarConnected()) return

        // Mirror mode requires a fresh user-confirmed screen capture dialog, so never auto-connect silently.
        if (AppSettings.displayMode(this) == AppSettings.DisplayMode.MIRROR) return

        // If a USB car accessory is physically attached in Android Auto mode, cable wins.
        if (UsbCableConnector.connectIfPermitted(this)) {
            AppSettings.setConnectionMode(this, AppSettings.ConnectionMode.CABLE)
            return
        }

        // Only auto-start wireless search when Wireless mode is selected AND AA server is already running.
        if (AppSettings.connectionMode(this) == AppSettings.ConnectionMode.WIRELESS) {
            val canScan = checkSelfPermission(dev.caraalink.ui.NEARBY_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCar = AppSettings.wirelessCar(this) != null
            if (canScan && dev.caraalink.ui.shouldAutoConnect(snapshot, hasCar)) {
                Thread({
                    if (AndroidAutoServer.isRunning()) {
                        mainHandler.post {
                            val current = ConnectionStateHolder.snapshot
                            if (!isFinishing && !isDestroyed && dev.caraalink.ui.shouldAutoConnect(current, AppSettings.wirelessCar(this) != null)) {
                                dev.caraalink.ui.startWireless(this)
                            }
                        }
                    }
                }, "aa-onstart-check").start()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "onNewIntent: action=${intent.action}")
        maybeAutoStartOnAttach(intent)
        maybeStartAaTls(intent)
    }
}
