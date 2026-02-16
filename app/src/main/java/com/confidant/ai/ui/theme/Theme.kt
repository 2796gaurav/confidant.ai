package com.confidant.ai.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CONFIDANT AI - "MIDNIGHT TRUST" THEME
// Dark Mode Only - Privacy-First Design Philosophy
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private val ConfidantDarkColors = darkColorScheme(
    // Primary - Deep Indigo (Trust & Intelligence)
    primary = DeepIndigoMain,
    onPrimary = FrostWhitePrimary,
    primaryContainer = DeepIndigoDark,
    onPrimaryContainer = DeepIndigoLight,
    
    // Secondary - Electric Lime (Success & Active)
    secondary = ElectricLimeMain,
    onSecondary = MidnightDark,
    secondaryContainer = ElectricLimeDark,
    onSecondaryContainer = ElectricLimeLight,
    
    // Tertiary - Amber Caution (Warnings)
    tertiary = AmberCautionMain,
    onTertiary = MidnightDark,
    tertiaryContainer = AmberCautionDark,
    onTertiaryContainer = AmberCautionLight,
    
    // Error - Crimson Alert
    error = CrimsonAlertMain,
    onError = FrostWhitePrimary,
    errorContainer = CrimsonAlertDark,
    onErrorContainer = CrimsonAlertLight,
    
    // Background - Midnight
    background = MidnightMain,
    onBackground = FrostWhiteSecondary,
    
    // Surface - Midnight Light
    surface = MidnightLight,
    onSurface = FrostWhiteSecondary,
    surfaceVariant = MidnightDark,
    onSurfaceVariant = FrostWhiteTertiary,
    
    // Outline
    outline = FrostWhiteTertiary,
    outlineVariant = MidnightLight,
    
    // Inverse
    inverseSurface = FrostWhiteSecondary,
    inverseOnSurface = MidnightMain,
    inversePrimary = DeepIndigoDark,
    
    // Surface Tint
    surfaceTint = DeepIndigoMain,
    scrim = MidnightDark,
)

@Composable
fun ConfidantTheme(
    darkTheme: Boolean = true, // Always dark mode
    dynamicColor: Boolean = false, // Disabled for consistent branding
    content: @Composable () -> Unit
) {
    // Force dark mode - privacy-first design philosophy
    val colorScheme = ConfidantDarkColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar to midnight dark for immersive experience
            window.statusBarColor = MidnightDark.toArgb()
            window.navigationBarColor = MidnightDark.toArgb()
            // Light status bar icons would be invisible on dark background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}