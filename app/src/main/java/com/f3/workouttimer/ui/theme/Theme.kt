package com.f3.workouttimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// F3 palette: stark black and white, gray accents.
val F3Black = Color(0xFF000000)
val F3White = Color(0xFFFFFFFF)
val F3Gray = Color(0xFF9E9E9E)
val F3DarkGray = Color(0xFF1C1C1C)
val F3MidGray = Color(0xFF2E2E2E)
val F3Red = Color(0xFFD32F2F)

private val F3ColorScheme = darkColorScheme(
    primary = F3White,
    onPrimary = F3Black,
    secondary = F3Gray,
    onSecondary = F3Black,
    background = F3Black,
    onBackground = F3White,
    surface = F3DarkGray,
    onSurface = F3White,
    surfaceVariant = F3MidGray,
    onSurfaceVariant = F3Gray,
    error = F3Red,
    onError = F3White,
    outline = F3Gray,
)

private val F3Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 96.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = 2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
)

@Composable
fun F3WorkoutTimerTheme(content: @Composable () -> Unit) {
    // F3 is black-and-white regardless of system theme.
    MaterialTheme(
        colorScheme = F3ColorScheme,
        typography = F3Typography,
        content = content,
    )
}
