package com.tkleisas.multituner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InTuneGreen = Color(0xFF5CE07A)
val WarnYellow = Color(0xFFFFD166)
val ErrorRed = Color(0xFFFF6B6B)
val MutedText = Color(0xFF8FA398)
val PanelColor = Color(0xFF111813)
val PanelBorder = Color(0xFF24312A)
val DeepBackground = Color(0xFF0B0F0C)

private val DarkColors = darkColorScheme(
    primary = InTuneGreen,
    onPrimary = Color(0xFF06210D),
    primaryContainer = Color(0xFF163D23),
    onPrimaryContainer = Color(0xFFB7FFC6),
    secondary = Color(0xFF7FD7FF),
    onSecondary = Color(0xFF00212E),
    background = DeepBackground,
    onBackground = Color(0xFFE6F2E9),
    surface = PanelColor,
    onSurface = Color(0xFFE6F2E9),
    surfaceVariant = Color(0xFF1C2620),
    onSurfaceVariant = Color(0xFFB8C9BE),
    outline = PanelBorder,
    error = ErrorRed,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 84.sp,
        letterSpacing = (-2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun MultiTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
