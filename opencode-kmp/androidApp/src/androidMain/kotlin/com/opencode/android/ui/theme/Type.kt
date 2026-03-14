package com.opencode.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system monospace as fallback (JetBrains Mono should be added as a font asset)
val MonoFamily = FontFamily.Monospace
val SansFamily = FontFamily.Default

val OpenCodeTypography = Typography(
    bodyLarge = TextStyle(fontFamily = SansFamily, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 0.08.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
    titleMedium = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = MonoFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold),
)
