package com.hesabyar.garage.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DeepTeal = Color(0xFF123B43)
val Teal = Color(0xFF0F766E)
val Mint = Color(0xFFDDF5EF)
val Amber = Color(0xFFE7A83E)
val Canvas = Color(0xFFF7FAF9)
val Success = Color(0xFF1B7A4B)
val Danger = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = DeepTeal,
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = Amber,
    background = Canvas,
    surface = Color.White,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81D6CB),
    secondary = Color(0xFF5EC6BB),
    tertiary = Color(0xFFFFC66A),
)

@Composable
fun HesabYarTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            // Android 15 enforces edge-to-edge, so the status bar is drawn over the
            // light app canvas. Dark icons keep the clock and system indicators legible.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
