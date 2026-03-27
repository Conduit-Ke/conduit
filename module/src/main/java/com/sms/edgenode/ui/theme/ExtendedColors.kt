package com.sms.edgenode.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended color tokens from The Secure Ledger design system
 * These provide the surface hierarchy for tonal layering
 */
@Immutable
data class ExtendedColorScheme(
    // Surface Hierarchy & Nesting (Light Mode)
    val surfaceContainerLowest: Color,  // Level 2 - Active Cards (#ffffff)
    val surfaceContainerLow: Color,     // Level 1 - List Backgrounds (#f2f4f6)
    val surfaceContainer: Color,        // (#eceef0)
    val surfaceContainerHigh: Color,    // (#e6e8ea)
    val surfaceContainerHighest: Color, // Active navigation/Compose area (#e0e3e5)
    val surfaceDim: Color,              // (#d8dadc)
    val surfaceBright: Color,           // (#f7f9fb)
    
    // Additional semantic colors
    val primaryFixed: Color,            // Outgoing messages (#d5e4f6)
    val primaryFixedDim: Color,         // (#b9c8da)
    val onPrimaryFixed: Color,          // (#0e1d2a)
    val onPrimaryFixedVariant: Color,   // (#3a4857)
    
    val secondaryFixed: Color,          // (#cbe7f5)
    val secondaryFixedDim: Color,       // (#afcbd8)
    val onSecondaryFixed: Color,        // (#021f29)
    val onSecondaryFixedVariant: Color, // (#304a55)
    
    val tertiaryFixed: Color,           // Spam/Flagged items (#fbdebc)
    val tertiaryFixedDim: Color,        // Forwarded icon (#dec2a2)
    val onTertiaryFixed: Color,         // (#271905)
    val onTertiaryFixedVariant: Color,  // (#56432b)
)

// Light mode extended colors
val LightExtendedColors = ExtendedColorScheme(
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F6),
    surfaceContainer = Color(0xFFECEEF0),
    surfaceContainerHigh = Color(0xFFE6E8EA),
    surfaceContainerHighest = Color(0xFFE0E3E5),
    surfaceDim = Color(0xFFD8DADC),
    surfaceBright = Color(0xFFF7F9FB),
    
    primaryFixed = Color(0xFFD5E4F6),
    primaryFixedDim = Color(0xFFB9C8DA),
    onPrimaryFixed = Color(0xFF0E1D2A),
    onPrimaryFixedVariant = Color(0xFF3A4857),
    
    secondaryFixed = Color(0xFFCBE7F5),
    secondaryFixedDim = Color(0xFFAFCBD8),
    onSecondaryFixed = Color(0xFF021F29),
    onSecondaryFixedVariant = Color(0xFF304A55),
    
    tertiaryFixed = Color(0xFFFBDEBC),
    tertiaryFixedDim = Color(0xFFDEC2A2),
    onTertiaryFixed = Color(0xFF271905),
    onTertiaryFixedVariant = Color(0xFF56432B),
)

// Dark mode extended colors
val DarkExtendedColors = ExtendedColorScheme(
    surfaceContainerLowest = Color(0xFF1A1C1E),
    surfaceContainerLow = Color(0xFF1F2123),
    surfaceContainer = Color(0xFF23252A),
    surfaceContainerHigh = Color(0xFF2D3033),
    surfaceContainerHighest = Color(0xFF38393D),
    surfaceDim = Color(0xFF111316),
    surfaceBright = Color(0xFF37393D),
    
    primaryFixed = Color(0xFFD5E4F6),
    primaryFixedDim = Color(0xFFB9C8DA),
    onPrimaryFixed = Color(0xFF0E1D2A),
    onPrimaryFixedVariant = Color(0xFF3A4857),
    
    secondaryFixed = Color(0xFFCBE7F5),
    secondaryFixedDim = Color(0xFFAFCBD8),
    onSecondaryFixed = Color(0xFF021F29),
    onSecondaryFixedVariant = Color(0xFF304A55),
    
    tertiaryFixed = Color(0xFFFBDEBC),
    tertiaryFixedDim = Color(0xFFDEC2A2),
    onTertiaryFixed = Color(0xFF271905),
    onTertiaryFixedVariant = Color(0xFF56432B),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Access extended colors in your composables:
 * val extendedColors = LocalExtendedColors.current
 */
@Composable
fun ColorScheme.extended(): ExtendedColorScheme {
    return LocalExtendedColors.current
}
