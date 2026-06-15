package com.amg.hisabkitab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = InkDeep,
    primaryContainer = TealContainer,
    onPrimaryContainer = TextPrimary,
    secondary = Teal,
    onSecondary = InkDeep,
    secondaryContainer = SurfaceHigh,
    onSecondaryContainer = TextPrimary,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    outline = Border,
    outlineVariant = Border.copy(alpha = 0.55f),
    error = Danger,
    onError = InkDeep,
    errorContainer = DangerContainer,
    onErrorContainer = Danger
)

@Composable
fun HisabKitabTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = HisabKitabShapes,
        content = content
    )
}
