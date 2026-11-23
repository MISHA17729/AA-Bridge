package dev.caraalink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caraalink.R
import dev.caraalink.ui.theme.Dash

enum class RingMode { IDLE, SPIN, FULL, ALERT }

/** Clean matte cabin background — no decorative radial glow. */
fun Modifier.dashGlow(@Suppress("UNUSED_PARAMETER") glow: Color): Modifier = background(Dash.Background)

/** Minimal 6-block geometric shield emblem inspired by modern Geely identity. */
@Composable
fun GeelyEmblemMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 20.dp, height = 22.dp)) {
        val gap = 2.dp.toPx()
        val cellW = (size.width - gap) / 2f
        val cellH = (size.height - gap * 2f) / 3f
        val r = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())
        val colors = listOf(
            Dash.Text, Dash.Teal,
            Dash.Teal, Dash.Text,
            Dash.Text.copy(alpha = 0.72f), Dash.Teal.copy(alpha = 0.85f),
        )
        for (row in 0 until 3) {
            for (col in 0 until 2) {
                drawRoundRect(
                    color = colors[row * 2 + col],
                    topLeft = Offset(col * (cellW + gap), row * (cellH + gap)),
                    size = Size(cellW, cellH),
                    cornerRadius = r,
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

/** Primary action: solid flat Geely cobalt blue button. */
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
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Attention action: solid flat restrained amber button. */
@Composable
fun AmberButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Dash.Amber)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF141008), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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

/** Clean top bar with geometric Geely emblem, sentence-case title, and settings icon. */
@Composable
fun DashTopBar(onOpenSettings: () -> Unit) {
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
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings), tint = Dash.Muted)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Dash.TileBorder)
    }
}
