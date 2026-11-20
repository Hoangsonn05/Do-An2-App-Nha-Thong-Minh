package com.example.smarthomeai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Blue700 = Color(0xFF102D35)
val Blue600 = Color(0xFF087F75)
val Blue500 = Color(0xFF0A9185)
val Blue300 = Color(0xFF69C9B9)
val Blue50 = Color(0xFFE5F4EF)
val Bg = Color(0xFFF6F7F8)
val CardWhite = Color.White
val TextDark = Color(0xFF172D33)
val TextGray = Color(0xFF617478)
val OffGray = Color(0xFFE9EEEB)
val GreenOk = Color(0xFF087F65)
val GreenSoft = Color(0xFFE2F3EA)
val RedAlert = Color(0xFFBC4444)
val RedSoft = Color(0xFFFFEEEE)
val OrangeWarn = Color(0xFF9B6715)
val YellowSoft = Color(0xFFFFF4DD)
val BlueSoft = Color(0xFFE7F1F7)
val PurpleSoft = Color(0xFFF0EDFA)
val Purple = Color(0xFF6C58A5)
val TempSoft = Color(0xFFFAEEE4)
val HumSoft = Color(0xFFE7F1F7)
val Accent = Color(0xFFD2F5A6)
val Outline = Color(0xFFDDE6E1)
val HeaderBrush = Brush.linearGradient(listOf(Color(0xFF102D35), Color(0xFF1B514E)))
val LoginBrush = HeaderBrush

private val Scheme = lightColorScheme(
    primary = Blue600, onPrimary = Color.White, background = Bg, surface = CardWhite,
    primaryContainer = Blue50, onPrimaryContainer = Blue700,
    secondaryContainer = Blue50, onSecondaryContainer = Blue700,
    tertiary = OrangeWarn, tertiaryContainer = YellowSoft, onTertiaryContainer = TextDark,
    onSurface = TextDark, onBackground = TextDark, secondary = GreenOk,
    surfaceVariant = OffGray, onSurfaceVariant = TextGray, outline = Outline, outlineVariant = Outline,
    surfaceTint = Blue600, error = RedAlert
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 33.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)
@Composable
fun SmartHomeTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = Scheme, typography = Type, content = content) }
