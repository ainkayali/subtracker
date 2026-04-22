package com.subtracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF445849),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6DC),
    onPrimaryContainer = Color(0xFF1E1A16),
    secondary = Color(0xFF756E66),
    background = Color(0xFFF7F1E8),
    onBackground = Color(0xFF1E1A16),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1E1A16),
    onSurfaceVariant = Color(0xFF756E66),
    outline = Color(0xFFE6DCCF),
    outlineVariant = Color(0xFFEFE7DB),
    error = Color(0xFF9D4036),
)

@Composable
fun SubTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
