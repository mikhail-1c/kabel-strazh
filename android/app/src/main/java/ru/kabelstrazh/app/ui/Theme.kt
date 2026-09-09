package ru.kabelstrazh.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0D10)
val Panel = Color(0xFF171B21)
val Gold = Color(0xFFE8B84A)
val SafeGreen = Color(0xFF3DDC97)
val LeakRed = Color(0xFFC62828)
val AllowedBlue = Color(0xFF4C8DFF)
val Mute = Color(0xFF9AA3B2)

private val colors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    background = Ink,
    surface = Panel,
    onBackground = Color(0xFFF4F1EA),
    onSurface = Color(0xFFF4F1EA),
    error = LeakRed,
)

@Composable
fun KabelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
