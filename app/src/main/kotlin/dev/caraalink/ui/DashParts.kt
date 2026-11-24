package dev.caraalink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caraalink.R
import dev.caraalink.settings.AppSettings
import dev.caraalink.ui.theme.Dash

enum class RingMode { IDLE, SPIN, FULL, ALERT }

/** Clean matte cabin background — no decorative radial glow. */
fun Modifier.dashGlow(@Suppress("UNUSED_PARAMETER") glow: Color): Modifier = background(Dash.Background)

/** Flat silver-titanium Geely 2023 shield emblem (exact 3.4:1 vector). */
@Composable
fun GeelyEmblemMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_geely_emblem),
        contentDescription = null,
        modifier = modifier.size(width = 44.dp, height = 13.dp),
    )
}


/** Minimal 2-segment graphite-silver switch for Android Auto vs Phone Screen Mirror. */
@Composable
fun DisplayModeSwitch(
    mode: AppSettings.DisplayMode,
    onSelect: (AppSettings.DisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(12.dp)
    val itemShape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(Dash.Tile)
            .border(1.dp, Dash.TileBorder, outerShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val items = listOf(
            AppSettings.DisplayMode.ANDROID_AUTO to stringResource(R.string.display_mode_aa),
            AppSettings.DisplayMode.MIRROR to stringResource(R.string.display_mode_mirror),
        )
        items.forEach { (value, label) ->
            val selected = mode == value
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(itemShape)
                    .background(if (selected) Dash.Teal else Color.Transparent)
                    .clickable { if (!selected) onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color(0xFF0E1014) else Dash.Muted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Minimal 2-segment graphite-silver switch for Wired (USB) vs Wireless (Wi-Fi). */
@Composable
fun ConnectionModeSwitch(
    mode: AppSettings.ConnectionMode,
    onSelect: (AppSettings.ConnectionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(12.dp)
    val itemShape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(Dash.Tile)
            .border(1.dp, Dash.TileBorder, outerShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val items = listOf(
            AppSettings.ConnectionMode.CABLE to stringResource(R.string.mode_wired),
            AppSettings.ConnectionMode.WIRELESS to stringResource(R.string.mode_wireless),
        )
        items.forEach { (value, label) ->
            val selected = mode == value
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(itemShape)
                    .background(if (selected) Dash.Teal else Color.Transparent)
                    .clickable { if (!selected) onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color(0xFF0E1014) else Dash.Muted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Kept for API compatibility; flat minimal indicator without neon glow. */
@Composable
fun StatusRing(mode: RingMode, icon: Painter, label: String, modifier: Modifier = Modifier) {
    val dotColor = when (mode) {
        RingMode.FULL -> Dash.Success
        RingMode.SPIN -> Dash.Teal
        RingMode.ALERT -> Dash.Amber
        RingMode.IDLE -> Dash.Muted
    }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Dash.Tile)
            .border(1.dp, Dash.TileBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Dash.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

enum class Lamp { ON, OFF, ALERT }

/** Quiet status row item for the unified instrument panel. */
@Composable
fun InstrumentRow(
    label: String,
    value: String,
    lamp: Lamp,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (lamp) {
        Lamp.ON -> Dash.Success
        Lamp.ALERT -> Dash.Amber
        Lamp.OFF -> Color(0xFF475060)
    }
    val valueColor = when (lamp) {
        Lamp.ON -> Dash.Text
        Lamp.ALERT -> Dash.Amber
        Lamp.OFF -> Dash.Muted
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Dash.Muted, fontSize = 14.sp, fontWeight = FontWeight.Normal)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(8.dp))
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Kept for compatibility if referenced elsewhere. */
@Composable
fun Telltale(icon: Painter, label: String, lamp: Lamp) {
    val color = when (lamp) {
        Lamp.ON -> Dash.Success
        Lamp.ALERT -> Dash.Amber
        Lamp.OFF -> Color(0xFF475060)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (lamp == Lamp.OFF) Dash.Muted else Dash.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Primary action: solid flat silver-titanium button with deep obsidian text. */
@Composable
fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Dash.Teal)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF0E1014), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Attention action: unified flat silver-titanium button. */
@Composable
fun AmberButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Dash.Teal)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF0E1014), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Secondary surface action: flat graphite tile with hairline border. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Dash.Tile)
            .border(BorderStroke(1.dp, Dash.TileBorder), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Dash.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/** Quiet tertiary text action. */
@Composable
fun DashLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Dash.Muted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Version info shown quietly at the foot of Settings. */
@Composable
fun Byline(modifier: Modifier = Modifier) {
    Text(
        "${stringResource(R.string.made_by)} — v${dev.caraalink.BuildConfig.VERSION_NAME} (${dev.caraalink.BuildConfig.GIT_SHA})",
        modifier,
        color = Dash.Muted,
        fontSize = 12.sp,
    )
}

/** Clean top bar with Geely shield, title, quick Android Auto settings icon, and app settings icon. */
@Composable
fun DashTopBar(
    onOpenSettings: () -> Unit,
    onOpenAndroidAuto: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenAndroidAuto != null) {
                    IconButton(onClick = onOpenAndroidAuto) {
                        Icon(
                            painterResource(R.drawable.ic_android_auto),
                            contentDescription = stringResource(R.string.open_aa),
                            tint = Dash.Muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings), tint = Dash.Muted)
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Dash.TileBorder)
    }
}
