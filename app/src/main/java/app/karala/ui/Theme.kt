package app.karala.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Dark = darkColorScheme(
    primary = Color(0xFFFFD54A), onPrimary = Color(0xFF1B1E26),
    primaryContainer = Color(0xFF4A3D0E), onPrimaryContainer = Color(0xFFFFE9A6),
    secondary = Color(0xFF6FE3B0), onSecondary = Color(0xFF0B2A1D),
    secondaryContainer = Color(0xFF173D2E), onSecondaryContainer = Color(0xFFB5F3D6),
    tertiary = Color(0xFF7CB6FF), onTertiary = Color(0xFF0A2440),
    tertiaryContainer = Color(0xFF16345A), onTertiaryContainer = Color(0xFFCFE4FF),
    background = Color(0xFF111318), onBackground = Color(0xFFECEEF3),
    surface = Color(0xFF111318), onSurface = Color(0xFFECEEF3),
    surfaceVariant = Color(0xFF252A35), onSurfaceVariant = Color(0xFFAAB2C0),
    surfaceContainerLowest = Color(0xFF0D0F13), surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1B1E26), surfaceContainerHigh = Color(0xFF232732), surfaceContainerHighest = Color(0xFF2C313D),
    outline = Color(0xFF596273), outlineVariant = Color(0xFF333946),
    error = Color(0xFFFF6B6B), onError = Color(0xFF3A0A0A), errorContainer = Color(0xFF5A1B1B), onErrorContainer = Color(0xFFFFD4D4),
)

private val Light = lightColorScheme(
    primary = Color(0xFF6E5400), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A), onPrimaryContainer = Color(0xFF231A00),
    secondary = Color(0xFF12704C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC3F2DA), onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF1D5FA8), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E6FF), onTertiaryContainer = Color(0xFF001C3A),
    background = Color(0xFFFAF8F2), onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFAF8F2), onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFE9E5DA), onSurfaceVariant = Color(0xFF4C4A43),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F2EA),
    surfaceContainer = Color(0xFFF0EDE4), surfaceContainerHigh = Color(0xFFEAE7DD), surfaceContainerHighest = Color(0xFFE4E1D7),
    outline = Color(0xFF7D7A70), outlineVariant = Color(0xFFCFCBBF),
    error = Color(0xFFB3261E),
)

/** @param mode 0 sistem, 1 açık, 2 koyu */
@Composable
fun KaralaTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)),
        content = content,
    )
}

val Good = Color(0xFF3FBF7F)
val Bad = Color(0xFFE5534B)
