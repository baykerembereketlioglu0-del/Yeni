package app.karala.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.karala.R

/** Plus Jakarta Sans (SIL OFL 1.1) — uygulamaya gömülü, Türkçe karakterleri tam. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Float = 0f) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.em)

private val KTypography = Typography(
    displayLarge = style(52, 58, FontWeight.ExtraBold, -0.03f),
    displayMedium = style(42, 48, FontWeight.ExtraBold, -0.03f),
    displaySmall = style(34, 40, FontWeight.ExtraBold, -0.02f),
    headlineLarge = style(30, 36, FontWeight.ExtraBold, -0.02f),
    headlineMedium = style(26, 32, FontWeight.ExtraBold, -0.02f),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.015f),
    titleLarge = style(20, 26, FontWeight.Bold, -0.01f),
    titleMedium = style(16, 22, FontWeight.Bold, -0.005f),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Medium),
    bodyMedium = style(14, 21, FontWeight.Medium),
    bodySmall = style(12, 17, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.02f),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFD24A), onPrimary = Color(0xFF1A1505),
    primaryContainer = Color(0xFF3D3310), onPrimaryContainer = Color(0xFFFFE9A6),
    secondary = Color(0xFF5EE6B0), onSecondary = Color(0xFF06261A),
    secondaryContainer = Color(0xFF12382B), onSecondaryContainer = Color(0xFFB5F3D6),
    tertiary = Color(0xFF7CB6FF), onTertiary = Color(0xFF0A2440),
    tertiaryContainer = Color(0xFF16345A), onTertiaryContainer = Color(0xFFCFE4FF),
    background = Color(0xFF0D0F14), onBackground = Color(0xFFEEF0F5),
    surface = Color(0xFF0D0F14), onSurface = Color(0xFFEEF0F5),
    surfaceVariant = Color(0xFF232733), onSurfaceVariant = Color(0xFFA3ABBA),
    surfaceContainerLowest = Color(0xFF090B0F), surfaceContainerLow = Color(0xFF13161D),
    surfaceContainer = Color(0xFF171A22), surfaceContainerHigh = Color(0xFF1F232D), surfaceContainerHighest = Color(0xFF2A2F3B),
    outline = Color(0xFF596273), outlineVariant = Color(0xFF2A2F3B),
    error = Color(0xFFFF6B6B), onError = Color(0xFF3A0A0A), errorContainer = Color(0xFF5A1B1B), onErrorContainer = Color(0xFFFFD4D4),
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5B00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A), onPrimaryContainer = Color(0xFF231A00),
    secondary = Color(0xFF0E7A52), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F3DC), onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF1D5FA8), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E6FF), onTertiaryContainer = Color(0xFF001C3A),
    background = Color(0xFFF6F4EE), onBackground = Color(0xFF17181C),
    surface = Color(0xFFF6F4EE), onSurface = Color(0xFF17181C),
    surfaceVariant = Color(0xFFE9E5DA), onSurfaceVariant = Color(0xFF5A5850),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF0EDE5), surfaceContainerHighest = Color(0xFFE6E2D8),
    outline = Color(0xFF8A877C), outlineVariant = Color(0xFFE2DED3),
    error = Color(0xFFB3261E),
)

/** Material şemasının dışında kalan marka renkleri. */
class KColors(
    val dark: Boolean,
    /** Kurşun kalem sarısından turuncuya: ana eylem ve vurgu kartı. */
    val brand: Brush,
    val onBrand: Color,
    /** Geri sayım kartı. */
    val dusk: Brush,
    val sun: Color, val mint: Color, val sky: Color, val violet: Color, val coral: Color,
)

private val KDark = KColors(
    dark = true,
    brand = Brush.linearGradient(listOf(Color(0xFFFFDB52), Color(0xFFFFA63D))),
    onBrand = Color(0xFF1A1505),
    dusk = Brush.linearGradient(listOf(Color(0xFF27407A), Color(0xFF5A3C9E))),
    sun = Color(0xFFFFD24A), mint = Color(0xFF5EE6B0), sky = Color(0xFF7CB6FF), violet = Color(0xFFB79CFF), coral = Color(0xFFFF8A7A),
)
private val KLight = KColors(
    dark = false,
    brand = Brush.linearGradient(listOf(Color(0xFFFFDB52), Color(0xFFFFA63D))),
    onBrand = Color(0xFF1A1505),
    dusk = Brush.linearGradient(listOf(Color(0xFF3B5FB5), Color(0xFF7B55D1))),
    sun = Color(0xFFB98300), mint = Color(0xFF0E8F60), sky = Color(0xFF2B6FC4), violet = Color(0xFF7550D6), coral = Color(0xFFD9503F),
)

val LocalK = staticCompositionLocalOf { KDark }
val K: KColors @Composable get() = LocalK.current

/** @param mode 0 sistem, 1 açık, 2 koyu */
@Composable
fun KaralaTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalK provides if (dark) KDark else KLight) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = KTypography,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)),
            content = content,
        )
    }
}

val Good = Color(0xFF3FBF7F)
val Bad = Color(0xFFE5534B)
