package com.novadrift.runner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NovaColorScheme = darkColorScheme(
    primary = Color(0xFF3FE0FF),
    secondary = Color(0xFFFFB454),
    tertiary = Color(0xFF9D7BFF),
    background = Color(0xFF05060F),
    surface = Color(0xFF0C1026),
    onPrimary = Color(0xFF02131A),
    onSecondary = Color(0xFF231200),
    onBackground = Color(0xFFEAF6FF),
    onSurface = Color(0xFFEAF6FF)
)

@Composable
fun NovaDriftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaColorScheme,
        typography = Typography,
        content = content
    )
}
