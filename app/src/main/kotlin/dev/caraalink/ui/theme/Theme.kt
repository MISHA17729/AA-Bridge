package dev.caraalink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Geely-inspired cockpit palette: matte obsidian cabin surfaces, hairline
 * titanium dividers, understated Geely cobalt blue, and restrained status tones.
 */
object Dash {
    val Background = Color(0xFF0E1014)
    val Tile = Color(0xFF161920)
    val TileBorder = Color(0xFF242934)
    val Track = Color(0xFF1E222B)
    val Teal = Color(0xFF3370FF)
    val Cyan = Color(0xFF5C8FFF)
    val Success = Color(0xFF22B07D)
    val Amber = Color(0xFFE59A2B)
    val Orange = Color(0xFFD9821E)
    val Text = Color(0xFFF2F4F8)
    val Muted = Color(0xFF8C95A6)
    val GlowTeal = Color(0xFF182238)
    val GlowAmber = Color(0xFF261F14)
    val GlowIdle = Color(0xFF161920)
}

private val Scheme = darkColorScheme(
    primary = Dash.Teal,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Dash.GlowTeal,
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Dash.Cyan,
    onSecondary = Color(0xFF0B1528),
    tertiary = Dash.Amber,
    onTertiary = Color(0xFF1A1205),
    tertiaryContainer = Dash.GlowAmber,
    onTertiaryContainer = Color(0xFFF6D9A8),
    error = Dash.Amber,
    onError = Color(0xFF1A1205),
    background = Dash.Background,
    onBackground = Dash.Text,
    surface = Dash.Background,
    onSurface = Dash.Text,
    surfaceVariant = Dash.Track,
    onSurfaceVariant = Dash.Muted,
    surfaceContainerLowest = Color(0xFF0B0D11),
    surfaceContainerLow = Color(0xFF13161C),
    surfaceContainer = Dash.Tile,
    surfaceContainerHigh = Color(0xFF1B1F28),
    surfaceContainerHighest = Color(0xFF222733),
    outline = Dash.TileBorder,
    outlineVariant = Dash.Track,
)

private val Base = Typography()
private val Type = Base.copy(
    headlineMedium = Base.headlineMedium.copy(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = Base.headlineSmall.copy(
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = Base.titleLarge.copy(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = Base.titleMedium.copy(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = Base.bodyLarge.copy(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = Base.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = Base.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = Base.labelLarge.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun AaBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
