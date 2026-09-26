package com.autovision.clicker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AutoVisionColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF00E5D0),
    background = Color(0xFF0F1115),
    surface = Color(0xFF1A1D24),
    onPrimary = Color.White,
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6)
)

@Composable
fun AutoVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AutoVisionColorScheme,
        content = content
    )
}
