package com.example.flashalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IosDarkColorScheme = darkColorScheme(
    primary = IosOrange,
    onPrimary = IosBackground,
    primaryContainer = IosOrangeDark,
    onPrimaryContainer = IosTextPrimary,
    secondary = IosTextSecondary,
    onSecondary = IosTextPrimary,
    background = IosBackground,
    onBackground = IosTextPrimary,
    surface = IosCardSurface,
    onSurface = IosTextPrimary,
    surfaceVariant = IosCardSurfaceVariant,
    onSurfaceVariant = IosTextSecondary,
    outline = IosSeparator
)

@Composable
fun FlashAlarmTheme(
    content: @Composable () -> Unit
) {
    // 强制采用 iOS 闹钟沉浸黑夜主题
    MaterialTheme(
        colorScheme = IosDarkColorScheme,
        content = content
    )
}
