package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BgDark,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyanLight,
    secondary = ElectricViolet,
    onSecondary = BgDark,
    secondaryContainer = ElectricVioletContainer,
    onSecondaryContainer = ElectricVioletLight,
    tertiary = NeonEmerald,
    onTertiary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = CoralRose,
    onError = TextPrimary
)

private val LightColorScheme = darkColorScheme( // For flagship look, prefer dark theme / dark aesthetic by default
    primary = NeonCyan,
    onPrimary = BgDark,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyanLight,
    secondary = ElectricViolet,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = BgDark.toArgb()
                window.navigationBarColor = BgDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
