package com.sms.edgenode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sms.edgenode.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The Secure Ledger Design System
// Creative North Star: Efficient Authority - treating messages as line items in a premium ledger
object DesignTokens {
    // Shapes - Fluid Organic Curves
    val CardShape = RoundedCornerShape(16.dp)
    val ButtonShape = CircleShape  // Fully rounded pill format
    val ChipShape = RoundedCornerShape(12.dp)
    val DialogShape = RoundedCornerShape(24.dp)
    
    // Spacing - Editorial Scale
    val Spacing1 = 4.dp      // spacing-1
    val Spacing2 = 8.dp      // spacing-2
    val Spacing3 = 12.dp     // spacing-3
    val Spacing4 = 14.dp     // spacing-4 (0.9rem) - between message items
    val Spacing6 = 24.dp     // spacing-6
    val Spacing8 = 28.dp     // spacing-8 (1.75rem) - exterior page margins
    
    // Elevation - Tonal Layering & Ambient Diffusion
    val ElevationCard = 0.dp        // No traditional shadows
    val ElevationFloating = 0.dp    // Use tonal shifts instead
    val AmbientShadowBlur = 20.dp   // For floating modals: 20px blur, 6% opacity
    
    // Border - Ghost Border
    val BorderWidth = 1.dp
    val BorderWidthAccent = 2.dp    // For unread accent bars
    val GhostBorderOpacity = 0.15f  // 15% opacity for subtle borders
}

// Dark Theme - The Secure Ledger (Dark Mode)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9C8DA),           // primary_fixed_dim
    onPrimary = Color(0xFF0E1D2A),         // on_primary_fixed
    primaryContainer = Color(0xFF3A4857),  // on_primary_fixed_variant
    onPrimaryContainer = Color(0xFFD5E4F6), // primary_fixed
    
    secondary = Color(0xFFAFCBD8),         // secondary_fixed_dim
    onSecondary = Color(0xFF021F29),       // on_secondary_fixed
    secondaryContainer = Color(0xFF304A55), // on_secondary_fixed_variant
    onSecondaryContainer = Color(0xFFCBE7F5), // secondary_fixed
    
    tertiary = Color(0xFFDEC2A2),          // tertiary_fixed_dim
    onTertiary = Color(0xFF271905),        // on_tertiary_fixed
    tertiaryContainer = Color(0xFF56432B), // on_tertiary_fixed_variant
    onTertiaryContainer = Color(0xFFFBDEBC), // tertiary_fixed
    
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = Color(0xFF191C1E),        // on_background (inverted for dark)
    onBackground = Color(0xFFEFF1F3),      // inverse_on_surface
    
    surface = Color(0xFF2D3133),           // inverse_surface
    onSurface = Color(0xFFEFF1F3),         // inverse_on_surface
    surfaceVariant = Color(0xFF44474C),    // on_surface_variant (darker)
    onSurfaceVariant = Color(0xFFC4C6CC),  // outline_variant
    
    outline = Color(0xFF74777C),
    outlineVariant = Color(0xFF44474C),
    
    surfaceTint = Color(0xFFB9C8DA),
    inverseSurface = Color(0xFFEFF1F3),
    inverseOnSurface = Color(0xFF2D3133),
    inversePrimary = Color(0xFF52606F),
)

// Light Theme - The Secure Ledger (Light Mode)
// Deep Navy & Slate palette for high-end financial/security terminal feel
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF101E2B),           // Deep Navy - foundation for high-importance
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF253341),  // primary_container - softer navy
    onPrimaryContainer = Color(0xFF8D9BAC), // on_primary_container
    
    secondary = Color(0xFF48626E),         // Slate - professional bridge
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBE7F5), // Light blue-gray
    onSecondaryContainer = Color(0xFF4E6874),
    
    tertiary = Color(0xFF291A06),          // Warm cautionary tone
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF402F18),
    onTertiaryContainer = Color(0xFFAF9678),
    
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    
    background = Color(0xFFF7F9FB),        // Soft architectural background
    onBackground = Color(0xFF191C1E),      // Never pure black - soft on eyes
    
    surface = Color(0xFFF7F9FB),           // Level 0 (Base)
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE0E3E5),    // surface_variant
    onSurfaceVariant = Color(0xFF44474C),  // For secondary metadata
    
    outline = Color(0xFF74777C),
    outlineVariant = Color(0xFFC4C6CC),
    
    surfaceTint = Color(0xFF52606F),
    inverseSurface = Color(0xFF2D3133),
    inverseOnSurface = Color(0xFFEFF1F3),
    inversePrimary = Color(0xFFB9C8DA),
)

// The Editorial Scale Typography System
// Dual-typeface: Manrope (Headlines) + Inter (Body/Labels)
// Removed Manrope to use existing Inter fonts exclusively

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // Headlines use Manrope - geometric sans-serif, modern yet institutional
    headlineLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,              // 1.5rem for contact names
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // Titles use Inter - for sender names and message previews
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,              // 1.125rem (title-md)
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // Body uses Inter - exceptional legibility at small sizes
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // Labels use Inter - for metadata and timestamps
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,               // 0.6875rem for timestamps
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

object ThemeState {
    var isDarkTheme = mutableStateOf<Boolean?>(null)
}

@Composable
fun EdgeNodeTheme(
    darkTheme: Boolean = ThemeState.isDarkTheme.value ?: isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp)
            ),
            content = content
        )
    }
}
