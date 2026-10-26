package com.voicereminder.vip.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF0E0B04),
    primaryContainer = Color(0xFF3A2C08),
    onPrimaryContainer = Color(0xFFFFE7A3),
    secondary = Teal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF0D3A34),
    onSecondaryContainer = Color(0xFFB9F5EC),
    tertiary = Lilac,
    onTertiary = Color(0xFF1B103D),
    tertiaryContainer = Color(0xFF33265E),
    onTertiaryContainer = Color(0xFFE5DDFF),
    background = Ink,
    onBackground = TextLight,
    surface = InkSurface,
    onSurface = TextLight,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = MutedLight,
    outline = Color(0xFF3A4359),
    outlineVariant = Color(0xFF2A3246),
    error = Rose,
    onError = Color(0xFF2A0006),
    errorContainer = Color(0xFF4A1620),
    onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = Cream,
    inverseOnSurface = TextDark,
    inversePrimary = GoldDeep,
    scrim = Color(0xFF000000)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6400),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE7A3),
    onPrimaryContainer = Color(0xFF2A1E00),
    secondary = Color(0xFF00695F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9F4ED),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF5B44A8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF1B103D),
    background = Cream,
    onBackground = TextDark,
    surface = Color(0xFFFFFFFF),
    onSurface = TextDark,
    surfaceVariant = Color(0xFFECE8DD),
    onSurfaceVariant = MutedDark,
    outline = Color(0xFF8A8F9C),
    outlineVariant = Color(0xFFD6D1C6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = InkSurface,
    inverseOnSurface = TextLight,
    inversePrimary = Gold,
    scrim = Color(0xFF000000)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun VoiceReminderTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
