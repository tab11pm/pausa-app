package com.tabek.mindfulpause.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — dark glass surfaces, green accent.
val Background = Color(0xFF0B0F0C)
val Surface = Color(0xFF12181400) // unused alpha placeholder; see SurfaceGlass
val SurfaceGlass = Color(0xFF141B16)
val SurfaceElevated = Color(0xFF1A231C)
val Accent = Color(0xFF22C55E)
val AccentDeep = Color(0xFF16A34A)
val TextPrimary = Color(0xFFF1F5F2)
val TextMuted = Color(0xFF8A968D)
val Divider = Color(0xFF222B24)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF04150B),
    secondary = AccentDeep,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceGlass,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMuted,
    outline = Divider,
)

@Composable
fun MindfulPauseTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // App is dark-only by design.
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
