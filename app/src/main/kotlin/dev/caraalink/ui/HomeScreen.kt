package dev.caraalink.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.caraalink.R
import dev.caraalink.aa.AaServerAccessibilityService
import dev.caraalink.aa.AndroidAutoServer
import dev.caraalink.aa.ServerIdleTimer
import dev.caraalink.connection.CarLink
import dev.caraalink.connection.ConnectionSnapshot
import dev.caraalink.connection.ConnectionStateHolder
import dev.caraalink.connection.WirelessPhase
import dev.caraalink.diagnostics.DiagnosticLogger
import dev.caraalink.service.UsbLinkForegroundService
import dev.caraalink.settings.AppSettings
import dev.caraalink.ui.theme.Dash
import dev.caraalink.usb.UsbCableConnector
import dev.caraalink.video.PhoneMirrorController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SERVER_POLL_MS = 2_000L

/** True while the screen is resumed: polls the head unit server every 2 s. */
@Composable
fun rememberServerRunning(): Boolean {
    var running by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                running = withContext(Dispatchers.IO) { AndroidAutoServer.isRunning() }
                delay(SERVER_POLL_MS)
            }
        }
    }
    return running
}

@Composable
private fun rememberConnection(): ConnectionSnapshot {
    var snapshot by remember { mutableStateOf(ConnectionStateHolder.snapshot) }
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val listener: (ConnectionSnapshot) -> Unit = { s -> main.post { snapshot = s } }
        ConnectionStateHolder.addListener(listener)
        onDispose { ConnectionStateHolder.removeListener(listener) }
    }
    return snapshot
}

/** The wordmark on screens without the settings gear. */
@Composable
fun BrandBar(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GeelyEmblemMark()
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.app_name),
                color = Dash.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = Dash.TileBorder)
    }
}

/** Starts the wireless search: the remembered car, or [address] when the driver picked one. */
fun startWireless(context: Context, address: String? = null, name: String? = null) {
    val intent = Intent(context, UsbLinkForegroundService::class.java).setAction(UsbLinkForegroundService.ACTION_START_WIRELESS)
    if (address != null) intent.putExtra(UsbLinkForegroundService.EXTRA_CAR_ADDRESS, address).putExtra(UsbLinkForegroundService.EXTRA_CAR_NAME, name)
    context.startForegroundService(intent)
}

private fun retryWireless(context: Context, connection: ConnectionSnapshot) {
    val car = carToRetry(connection, AppSettings.wirelessCar(context))
    startWireless(context, car?.address, car?.name)
}

private fun stopLink(context: Context) {
    context.startService(Intent(context, UsbLinkForegroundService::class.java).setAction(UsbLinkForegroundService.ACTION_STOP))
}

private fun connectByCable(context: Context, silentIfMissing: Boolean = false) {
    when (UsbCableConnector.connect(context)) {
        UsbCableConnector.Result.STARTED -> Unit
        UsbCableConnector.Result.PERMISSION_REQUESTED -> {
            Toast.makeText(context, context.getString(R.string.usb_permission_hint), Toast.LENGTH_SHORT).show()
        }
        UsbCableConnector.Result.NO_ACCESSORY -> {
            if (!silentIfMissing) {
                Toast.makeText(context, context.getString(R.string.usb_not_found_toast), Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun toggleHeadUnitServer(context: Context, action: AaServerAccessibilityService.Action) {
    val started = AaServerAccessibilityService.requestAction(context, action, returnToApp = true)
    if (!started) {
        Toast.makeText(context, context.getString(R.string.a11y_enable_toast), Toast.LENGTH_LONG).show()
        AaServerAccessibilityService.openAccessibilitySettings(context)
    }
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onOpenNearby: () -> Unit) {
    val context = LocalContext.current
    val serverRunning = rememberServerRunning()
    val connection = rememberConnection()
    val carConnected = connection.phase.isCarConnected()
    var displayMode by remember { mutableStateOf(AppSettings.displayMode(context)) }
    val mirrorMode = displayMode == AppSettings.DisplayMode.MIRROR
    val state = homeState(serverRunning, carConnected, connection.wireless, mirrorMode = mirrorMode)
    val searchName = carToRetry(connection, AppSettings.wirelessCar(context))?.name
    val carName = connection.carName ?: searchName ?: stringResource(R.string.state_connected)
    var connectionMode by remember { mutableStateOf(AppSettings.connectionMode(context)) }
    var pendingMirrorAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val mirrorPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            PhoneMirrorController.setPermission(result.resultCode, result.data)
            val action = pendingMirrorAction
            pendingMirrorAction = null
            action?.invoke()
        } else {
            pendingMirrorAction = null
            Toast.makeText(context, context.getString(R.string.mirror_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    fun launchWithMirrorCheck(afterGranted: () -> Unit) {
        if (!mirrorMode || PhoneMirrorController.hasPermission()) {
            afterGranted()
            return
        }
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm == null) {
            Toast.makeText(context, context.getString(R.string.mirror_permission_denied), Toast.LENGTH_SHORT).show()
            return
        }
        pendingMirrorAction = afterGranted
        mirrorPermissionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // Keep UI switch synced if a physical USB cable connects.
    LaunchedEffect(connection.link, carConnected) {
        if (carConnected && connection.link == CarLink.CABLE) {
            connectionMode = AppSettings.ConnectionMode.CABLE
            AppSettings.setConnectionMode(context, AppSettings.ConnectionMode.CABLE)
        } else if (carConnected && connection.link == CarLink.WIRELESS) {
            connectionMode = AppSettings.ConnectionMode.WIRELESS
            AppSettings.setConnectionMode(context, AppSettings.ConnectionMode.WIRELESS)
        }
    }



    var idleRemainingMin by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(serverRunning, carConnected) {
        when {
            !serverRunning || carConnected -> {
                if (!serverRunning) ServerIdleTimer.cancel(context)
                idleRemainingMin = null
            }
            else -> {
                if (ServerIdleTimer.remainingMinutes(context) == null) {
                    ServerIdleTimer.arm(context)
                }
                while (true) {
                    idleRemainingMin = ServerIdleTimer.remainingMinutes(context)
                    delay(15_000L)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dash.Background)
            .systemBarsPadding(),
    ) {
        DashTopBar(
            onOpenSettings = onOpenSettings,
            onOpenAndroidAuto = { AndroidAutoServer.openSettings(context) },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            val statusDot = when (state) {
                HomeState.CONNECTED -> Dash.Success
                HomeState.SEARCHING, HomeState.CONNECTING -> Dash.Teal
                HomeState.NEEDS_TAP, HomeState.NOT_FOUND, HomeState.WIFI_OFF -> Dash.Amber
                HomeState.READY -> Dash.Success
            }
            val statusLabel = when (state) {
                HomeState.READY -> stringResource(R.string.ring_ready)
                HomeState.SEARCHING -> stringResource(R.string.ring_searching)
                HomeState.CONNECTING -> stringResource(R.string.ring_connecting)
                HomeState.CONNECTED -> stringResource(R.string.ring_connected)
                HomeState.NEEDS_TAP -> stringResource(R.string.ring_needs_tap)
                HomeState.NOT_FOUND -> stringResource(R.string.ring_not_found)
                HomeState.WIFI_OFF -> stringResource(R.string.ring_wifi_off)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusDot))
                Spacer(Modifier.width(8.dp))
                Text(statusLabel, color = Dash.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(10.dp))

            val title = when (state) {
                HomeState.READY -> if (mirrorMode) stringResource(R.string.home_mirror_ready_title) else stringResource(R.string.home_ready_title)
                HomeState.NEEDS_TAP -> stringResource(R.string.home_needs_tap_title)
                HomeState.CONNECTED -> carName
                HomeState.SEARCHING, HomeState.CONNECTING -> searchName ?: stringResource(R.string.nearby_your_car)
                HomeState.NOT_FOUND -> if (connection.wireless == WirelessPhase.FAILED) {
                    stringResource(R.string.home_failed_title, searchName ?: "")
                } else {
                    stringResource(R.string.home_not_found_title)
                }
                HomeState.WIFI_OFF -> stringResource(R.string.home_wifi_off_title)
            }
            val body = when (state) {
                HomeState.READY -> when {
                    mirrorMode -> R.string.home_mirror_ready_body
                    connectionMode == AppSettings.ConnectionMode.CABLE -> R.string.home_ready_body_wired
                    else -> R.string.home_ready_body_wireless
                }
                HomeState.NEEDS_TAP -> R.string.home_needs_tap_body
                HomeState.CONNECTED -> if (mirrorMode) R.string.home_mirror_connected_body else R.string.home_connected_body
                HomeState.SEARCHING -> R.string.home_searching_body
                HomeState.CONNECTING -> R.string.home_connecting_body
                HomeState.NOT_FOUND -> R.string.home_not_found_body
                HomeState.WIFI_OFF -> R.string.home_wifi_off_body
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Dash.Text)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = Dash.Muted)

            if (state == HomeState.SEARCHING || state == HomeState.CONNECTING) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = Dash.Teal,
                    trackColor = Dash.Track,
                )
            }

            Spacer(Modifier.height(18.dp))

            DisplayModeSwitch(
                mode = displayMode,
                onSelect = { newDisplay ->
                    displayMode = newDisplay
                    AppSettings.setDisplayMode(context, newDisplay)
                    if (carConnected) {
                        stopLink(context)
                    }
                },
            )

            Spacer(Modifier.height(10.dp))

            ConnectionModeSwitch(
                mode = connectionMode,
                onSelect = { newMode ->
                    connectionMode = newMode
                    AppSettings.setConnectionMode(context, newMode)
                    if (newMode == AppSettings.ConnectionMode.CABLE) {
                        if (!carConnected) {
                            ConnectionStateHolder.update { it.copy(wireless = WirelessPhase.IDLE, suggestion = null) }
                        }
                        if (connection.link == CarLink.WIRELESS ||
                            connection.wireless == WirelessPhase.SEARCHING ||
                            connection.wireless == WirelessPhase.CONNECTING ||
                            connection.wireless == WirelessPhase.RECONNECTING
                        ) {
                            stopLink(context)
                        }
                        if ((mirrorMode || serverRunning) && !carConnected) {
                            if (mirrorMode) {
                                launchWithMirrorCheck { connectByCable(context, silentIfMissing = true) }
                            } else {
                                connectByCable(context, silentIfMissing = true)
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(14.dp))

            val cable = carConnected && connection.link == CarLink.CABLE
            val linkValue = when {
                !carConnected -> stringResource(R.string.state_not_connected)
                cable -> stringResource(R.string.link_cable_short)
                else -> stringResource(R.string.link_wireless_short)
            }
            val linkLamp = when {
                carConnected -> Lamp.ON
                state == HomeState.WIFI_OFF -> Lamp.ALERT
                else -> Lamp.OFF
            }
            val panelShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(panelShape)
                    .background(Dash.Tile)
                    .border(1.dp, Dash.TileBorder, panelShape),
            ) {
                if (mirrorMode) {
                    val touchReady = AaServerAccessibilityService.canDispatchGestures(context)
                    InstrumentRow(
                        label = stringResource(R.string.row_mirror_touch),
                        value = stringResource(if (touchReady) R.string.state_ready else R.string.state_off),
                        lamp = if (touchReady) Lamp.ON else Lamp.ALERT,
                        modifier = if (!touchReady) {
                            Modifier.clickable { AaServerAccessibilityService.openAccessibilitySettings(context) }
                        } else {
                            Modifier
                        },
                    )
                } else {
                    InstrumentRow(
                        label = stringResource(R.string.row_server),
                        value = stringResource(if (serverRunning) R.string.state_running else R.string.state_off),
                        lamp = if (serverRunning) Lamp.ON else Lamp.ALERT,
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = Dash.TileBorder)
                InstrumentRow(
                    label = stringResource(R.string.tile_link),
                    value = linkValue,
                    lamp = linkLamp,
                )
                HorizontalDivider(thickness = 1.dp, color = Dash.TileBorder)
                InstrumentRow(
                    label = stringResource(R.string.row_car),
                    value = if (carConnected) carName else stringResource(R.string.state_not_connected),
                    lamp = if (carConnected) Lamp.ON else Lamp.OFF,
                )
                if (!mirrorMode && serverRunning && !carConnected) {
                    idleRemainingMin?.let { mins ->
                        HorizontalDivider(thickness = 1.dp, color = Dash.TileBorder)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.home_server_auto_stop, mins),
                                style = MaterialTheme.typography.bodySmall,
                                color = Dash.Muted,
                            )
                        }
                    }
                }
            }

            if (state == HomeState.NOT_FOUND) {
                connection.suggestion?.let { found ->
                    Spacer(Modifier.height(14.dp))
                    SuggestionTile(found.name) {
                        launchWithMirrorCheck { startWireless(context, found.address, found.name) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeActions(
                state = state,
                displayMode = displayMode,
                connectionMode = connectionMode,
                serverRunning = serverRunning,
                carConnected = carConnected,
                connection = connection,
                onOpenNearby = { launchWithMirrorCheck(onOpenNearby) },
                onLaunchConnect = { action -> launchWithMirrorCheck(action) },
            )
            SendLogsLink()
        }
    }
}

/** The main and secondary actions; each state offers its own next step. */
@Composable
private fun HomeActions(
    state: HomeState,
    displayMode: AppSettings.DisplayMode,
    connectionMode: AppSettings.ConnectionMode,
    serverRunning: Boolean,
    carConnected: Boolean,
    connection: ConnectionSnapshot,
    onOpenNearby: () -> Unit,
    onLaunchConnect: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val mirrorMode = displayMode == AppSettings.DisplayMode.MIRROR
    when (state) {
        HomeState.READY -> {
            if (connectionMode == AppSettings.ConnectionMode.CABLE) {
                val label = stringResource(if (mirrorMode) R.string.home_start_mirror_wired else R.string.home_connect_wired)
                GlowButton(label, { onLaunchConnect { connectByCable(context) } })
            } else {
                val remembered = AppSettings.wirelessCar(context)
                val label = stringResource(if (mirrorMode) R.string.home_start_mirror_wireless else R.string.home_connect_wireless)
                if (remembered != null) {
                    GlowButton(label, {
                        onLaunchConnect { startWireless(context, remembered.address, remembered.name) }
                    })
                } else {
                    GlowButton(label, onOpenNearby)
                }
            }
            if (!mirrorMode) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.home_stop_server), {
                    toggleHeadUnitServer(context, AaServerAccessibilityService.Action.STOP)
                })
            }
            if (connectionMode == AppSettings.ConnectionMode.WIRELESS && AppSettings.wirelessCar(context) != null) {
                Spacer(Modifier.height(4.dp))
                DashLink(stringResource(R.string.home_choose_car), onOpenNearby)
            }
        }
        HomeState.SEARCHING, HomeState.CONNECTING -> {
            SecondaryButton(stringResource(R.string.home_disconnect), { stopLink(context) })
            Spacer(Modifier.height(4.dp))
            DashLink(stringResource(R.string.home_use_another_car), onOpenNearby)
        }
        HomeState.CONNECTED -> {
            SecondaryButton(stringResource(R.string.home_disconnect), { stopLink(context) })
        }
        HomeState.NEEDS_TAP -> {
            AmberButton(
                stringResource(R.string.home_start_server),
                { toggleHeadUnitServer(context, AaServerAccessibilityService.Action.START) },
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(stringResource(R.string.open_aa_manual), { AndroidAutoServer.openSettings(context) })
        }
        HomeState.NOT_FOUND -> {
            if (connectionMode == AppSettings.ConnectionMode.CABLE) {
                GlowButton(stringResource(R.string.home_connect_wired), { onLaunchConnect { connectByCable(context) } })
            } else {
                GlowButton(stringResource(R.string.home_try_again), { onLaunchConnect { retryWireless(context, connection) } })
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.home_choose_car), onOpenNearby)
            }
            if (!mirrorMode && serverRunning && !carConnected) {
                Spacer(Modifier.height(6.dp))
                DashLink(stringResource(R.string.home_stop_server)) {
                    toggleHeadUnitServer(context, AaServerAccessibilityService.Action.STOP)
                }
            }
        }
        HomeState.WIFI_OFF -> {
            if (connectionMode == AppSettings.ConnectionMode.CABLE) {
                GlowButton(stringResource(R.string.home_connect_wired), { onLaunchConnect { connectByCable(context) } })
            } else {
                AmberButton(stringResource(R.string.home_wifi_settings), { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) })
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.home_try_again), { onLaunchConnect { retryWireless(context, connection) } })
            }
            if (!mirrorMode && serverRunning && !carConnected) {
                Spacer(Modifier.height(6.dp))
                DashLink(stringResource(R.string.home_stop_server)) {
                    toggleHeadUnitServer(context, AaServerAccessibilityService.Action.STOP)
                }
            }
        }
    }
}

/** Zips the logs and opens the share sheet, so a tester far away can send them over any chat app. */
@Composable
fun SendLogsLink() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(4.dp))
    DashLink(stringResource(R.string.home_send_logs)) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { DiagnosticLogger(context, "app").shareAllLogsIntent() } }
                .onSuccess { context.startActivity(Intent.createChooser(it, null)) }
                .onFailure { Toast.makeText(context, it.toString(), Toast.LENGTH_LONG).show() }
        }
    }
}

/** A device with the remembered car's name, one tap away. */
@Composable
private fun SuggestionTile(name: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dash.Tile)
            .border(1.dp, Dash.Teal, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_car), contentDescription = null, tint = Dash.Teal)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.home_found_nearby, name), color = Dash.Text, style = MaterialTheme.typography.bodyLarge)
    }
}
