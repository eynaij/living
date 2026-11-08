package com.mealpal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class MealPalColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color
)

val LightMealPalColors = MealPalColors(
    isDark = false,
    backgroundTop = Color(0xFFFAFAF7),
    backgroundBottom = Color(0xFFEDEDE6),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1B1B18),
    muted = Color(0xFF8A8A80),
    line = Color(0xFFE7E7DF),
    accent = Color(0xFFD95F2B)
)

val DarkMealPalColors = MealPalColors(
    isDark = true,
    backgroundTop = Color(0xFF0F0F11),
    backgroundBottom = Color(0xFF18181B),
    card = Color(0xFF1D1D21),
    ink = Color(0xFFF3F3EE),
    muted = Color(0xFF9A9A92),
    line = Color(0xFF2C2C31),
    accent = Color(0xFFF07B4D)
)

val LocalMealPalColors = staticCompositionLocalOf { LightMealPalColors }

private val MealPalTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.5).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun MealPalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val ext = if (darkTheme) DarkMealPalColors else LightMealPalColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = ext.ink,
            onPrimary = ext.backgroundTop,
            background = ext.backgroundTop,
            onBackground = ext.ink,
            surface = ext.card,
            onSurface = ext.ink,
            surfaceVariant = ext.card,
            onSurfaceVariant = ext.muted,
            outline = ext.line,
            outlineVariant = ext.line
        )
    } else {
        lightColorScheme(
            primary = ext.ink,
            onPrimary = ext.card,
            background = ext.backgroundTop,
            onBackground = ext.ink,
            surface = ext.card,
            onSurface = ext.ink,
            surfaceVariant = ext.card,
            onSurfaceVariant = ext.muted,
            outline = ext.line,
            outlineVariant = ext.line
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalMealPalColors provides ext
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MealPalTypography,
            content = content
        )
    }
}
