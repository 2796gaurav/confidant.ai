package com.confidant.ai.ui.theme

import androidx.compose.ui.graphics.Color

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CONFIDANT AI - "MIDNIGHT TRUST" COLOR SYSTEM
// Privacy-First, Dark Mode Only, WCAG AAA Compliant
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// PRIMARY COLORS - Deep Indigo (Trust, Intelligence, Calm)
val DeepIndigoLight = Color(0xFF5C6BC0)
val DeepIndigoMain = Color(0xFF3949AB)
val DeepIndigoDark = Color(0xFF283593)

// MIDNIGHT BACKGROUND (Surface) - Privacy, Depth, Modern
val MidnightLight = Color(0xFF1E2228)
val MidnightMain = Color(0xFF131720)
val MidnightDark = Color(0xFF0B0E14)

// ELECTRIC LIME (Success/Active) - Growth, Vitality, Positive
val ElectricLimeLight = Color(0xFFAED581)
val ElectricLimeMain = Color(0xFF8BC34A)
val ElectricLimeDark = Color(0xFF689F38)

// CRIMSON ALERT (Error/Warning) - Urgency, Attention, Caution
val CrimsonAlertLight = Color(0xFFEF5350)
val CrimsonAlertMain = Color(0xFFF44336)
val CrimsonAlertDark = Color(0xFFC62828)

// AMBER CAUTION (Medium Priority) - Attention without Alarm
val AmberCautionLight = Color(0xFFFFB74D)
val AmberCautionMain = Color(0xFFFF9800)
val AmberCautionDark = Color(0xFFF57C00)

// FROST WHITE (Text/Icons) - High Contrast
val FrostWhitePrimary = Color(0xFFFFFFFF)
val FrostWhiteSecondary = Color(0xFFE0E0E0)
val FrostWhiteTertiary = Color(0xFF9E9E9E)
val FrostWhiteMain = Color(0xFFFFFFFF)  // Alias for FrostWhitePrimary

// EMERALD SUCCESS (Success/Completion) - Achievement, Positive
val EmeraldSuccessMain = Color(0xFF10B981)  // Emerald 500
val EmeraldSuccessLight = Color(0xFF34D399)  // Emerald 400
val EmeraldSuccessDark = Color(0xFF059669)  // Emerald 600

// SEMANTIC COLORS - System Status
val SystemHealthy = Color(0xFF4CAF50)      // Material Green 500
val SystemWarning = Color(0xFFFF9800)      // Material Orange 500
val SystemCritical = Color(0xFFF44336)     // Material Red 500
val SystemInfo = Color(0xFF2196F3)         // Material Blue 500
val SystemOffline = Color(0xFF607D8B)      // Material Blue Grey 600

// FUNCTIONAL COLORS
val DataAnalytics = Color(0xFF00BCD4)      // Material Cyan 500
val PrivacySecurity = Color(0xFF673AB7)    // Material Deep Purple 500
val AIProcessing = Color(0xFF9C27B0)       // Material Purple 500
val Communication = Color(0xFF3F51B5)      // Material Indigo 500

// THERMAL STATE COLORS (Optimized for Colorblind)
val ThermalNominal = Color(0xFF4CAF50)     // Green - Cool
val ThermalLight = Color(0xFF2196F3)       // Blue - Warm
val ThermalModerate = Color(0xFFFF9800)    // Orange - Hot
val ThermalSevere = Color(0xFFFF6B6B)      // Light Red - Very Hot
val ThermalCritical = Color(0xFFC62828)    // Dark Red - Critical

// MEMORY CATEGORY COLORS
val CategorySocial = Color(0xFF2196F3)     // Blue
val CategoryFinance = Color(0xFF4CAF50)    // Green
val CategoryHealth = Color(0xFFEC4899)     // Pink
val CategoryWork = Color(0xFF9C27B0)       // Purple
val CategoryGeneral = Color(0xFF8B5CF6)    // Light Purple

// LEGACY ALIASES (for backward compatibility during migration)
val Primary = DeepIndigoMain
val PrimaryVariant = DeepIndigoDark
val Secondary = Color(0xFF8B5CF6)
val SecondaryVariant = Color(0xFF7C3AED)
val BackgroundDark = MidnightMain
val SurfaceDark = MidnightLight
val ThermalCool = ThermalNominal
val ThermalWarm = ThermalModerate
val ThermalHot = ThermalSevere
val InfoBlue = SystemInfo
val WarningOrange = SystemWarning
val ErrorRed = SystemCritical
val SuccessGreen = SystemHealthy
