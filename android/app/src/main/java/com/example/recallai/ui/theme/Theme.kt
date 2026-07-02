package com.example.recallai.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF7B6CF5),              // Soft periwinkle-violet (matches reference buttons)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E4FE),     // Light lavender (card tints)
    onPrimaryContainer = Color(0xFF2D1F8A),
    secondary = Color(0xFFFF6B9D),            // Pink accent (reference activity cards)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4EF),   // Blush
    onSecondaryContainer = Color(0xFF7A0035),
    tertiary = Color(0xFF4DC9C0),             // Teal (reference activity card color)
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF4F1),
    onTertiaryContainer = Color(0xFF004D48),
    background = Color(0xFFF0EFFE),           // THE KEY: very light periwinkle-lavender
    onBackground = Color(0xFF1A1635),         // Deep dark for crisp text
    surface = Color(0xFFFFFFFF),              // Pure white — all cards are white
    onSurface = Color(0xFF1A1635),
    surfaceVariant = Color(0xFFEDE9FF),       // Slightly more lavender for input backgrounds
    onSurfaceVariant = Color(0xFF5E4E9A),     // Muted violet for secondary text
    error = Color(0xFFE53935),
    errorContainer = Color(0xFFFFECEB),
    onErrorContainer = Color(0xFF7D1E1E),
    outline = Color(0xFFD4CFF0),              // Barely-there lavender outline
    outlineVariant = Color(0xFFEBE8FF)        // Ghost border
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAFA0FF),              // Soft pastel violet on dark
    onPrimary = Color(0xFF1A0C6E),
    primaryContainer = Color(0xFF3D2DA0),
    onPrimaryContainer = Color(0xFFE8E4FE),
    secondary = Color(0xFFFFAAC8),            // Pastel pink
    onSecondary = Color(0xFF5E0030),
    secondaryContainer = Color(0xFF7A2050),
    onSecondaryContainer = Color(0xFFFFE4EF),
    tertiary = Color(0xFF80E0D8),             // Pastel teal
    onTertiary = Color(0xFF003330),
    tertiaryContainer = Color(0xFF004B46),
    onTertiaryContainer = Color(0xFFCCF4F1),
    background = Color(0xFF141128),           // Deep dark with lavender tint
    onBackground = Color(0xFFEDE9FF),
    surface = Color(0xFF1E1A36),              // Dark surface
    onSurface = Color(0xFFEDE9FF),
    surfaceVariant = Color(0xFF2A2548),
    onSurfaceVariant = Color(0xFFCDC4EE),
    error = Color(0xFFFF8080),
    errorContainer = Color(0xFF8A2020),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7A70AA),
    outlineVariant = Color(0xFF3A3460)
)

@Composable
fun RecallAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val effectiveDarkTheme = if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        true
    } else if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO) {
        false
    } else {
        darkTheme
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDarkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RecallTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

private val RecallTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)