package com.opencode.android.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF3DFFA0)
val GreenDim = Color(0x143DFFA0)
val GreenMid = Color(0x333DFFA0)
val Red = Color(0xFFFF4D6A)
val Yellow = Color(0xFFFFD166)
val Blue = Color(0xFF58A6FF)
val Purple = Color(0xFFC084FC)
val Orange = Color(0xFFFB923C)

val BgDark       = Color(0xFF0C0C10)
val SurfaceDark  = Color(0xFF13131A)
val CardDark     = Color(0xFF1A1A24)
val BorderDark   = Color(0xFF252535)
val TextPrimary  = Color(0xFFE2E2F0)
val TextSecond   = Color(0xFF9090B0)
val TextThird    = Color(0xFF5A5A7A)

private val DarkColorScheme = darkColorScheme(
    primary         = Green,
    onPrimary       = Color(0xFF060610),
    primaryContainer = GreenDim,
    secondary       = Purple,
    tertiary        = Blue,
    background      = BgDark,
    surface         = SurfaceDark,
    surfaceVariant  = CardDark,
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    onSurfaceVariant = TextSecond,
    outline         = BorderDark,
    error           = Red,
)

@Composable
fun OpenCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = OpenCodeTypography,
        content = content,
    )
}
