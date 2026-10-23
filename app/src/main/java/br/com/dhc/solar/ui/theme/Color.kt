package br.com.dhc.solar.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Paleta própria (sem dynamic color), inspirada nas referências 1.webp/2.webp:
 * neutros creme quentes como pano de fundo para um único acento laranja.
 * Âmbar como tom secundário; verde só para estado saudável.
 */
object SolarPalette {
    val Sun = Color(0xFFFFB020)
    val Ember = Color(0xFFFF5B14)
    val Leaf = Color(0xFF2FBF71)
    val Sky = Color(0xFF3D8BFD)
    val Alert = Color(0xFFFF453A)

    val SunGradient = Brush.linearGradient(listOf(Sun, Ember))
}

val SolarLightColors = lightColorScheme(
    primary = SolarPalette.Ember,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D3),
    onPrimaryContainer = Color(0xFF4A1A00),
    secondary = SolarPalette.Leaf,
    tertiary = SolarPalette.Sky,
    background = Color(0xFFEFEDE8),
    onBackground = Color(0xFF1A1917),
    surface = Color(0xFFEFEDE8),
    onSurface = Color(0xFF1A1917),
    surfaceVariant = Color(0xFFE4E1DB),
    onSurfaceVariant = Color(0xFF75726C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F2EE),
    surfaceContainer = Color(0xFFF8F7F4),
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFD8D4CD),
    outlineVariant = Color(0xFFFFFFFF),
    error = SolarPalette.Alert,
)

val SolarDarkColors = darkColorScheme(
    primary = Color(0xFFFF6B2C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D1E0E),
    onPrimaryContainer = Color(0xFFFFD9C4),
    secondary = SolarPalette.Leaf,
    tertiary = SolarPalette.Sky,
    background = Color(0xFF0E0D0C),
    onBackground = Color(0xFFF3F1EC),
    surface = Color(0xFF0E0D0C),
    onSurface = Color(0xFFF3F1EC),
    surfaceVariant = Color(0xFF221F1C),
    onSurfaceVariant = Color(0xFF9F9A92),
    surfaceContainerLowest = Color(0xFF121110),
    surfaceContainerLow = Color(0xFF161514),
    surfaceContainer = Color(0xFF1A1817),
    surfaceContainerHigh = Color(0xFF26231F),
    outline = Color(0xFF3B3732),
    outlineVariant = Color(0xFF2A2724),
    error = SolarPalette.Alert,
)
