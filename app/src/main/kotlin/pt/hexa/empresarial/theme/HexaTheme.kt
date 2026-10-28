package pt.hexa.empresarial.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pt.hexa.empresarial.domain.model.AnimationSettings
import pt.hexa.empresarial.domain.model.AppearanceSettings
import pt.hexa.empresarial.domain.model.ThemeMode

/** Typography: serif for the brand and the clock, clean sans-serif for everything else. */
object HexaType {
    val brand = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.18.em)
    val edition = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, letterSpacing = 0.34.em)
    val clock = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraLight, fontSize = 64.sp, letterSpacing = 0.02.em)
    val date = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = 0.14.em)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp)

    val material = Typography(
        headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 0.02.em),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.02.em),
    )
}

private val LocalColors = staticCompositionLocalOf { HexaColors.resolve(true, Brand.Gold, pt.hexa.empresarial.domain.model.ContrastLevel.STANDARD) }
private val LocalTokens = staticCompositionLocalOf { HexaTokens() }

object HexaTheme {
    val colors: HexaColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val tokens: HexaTokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
}

@Composable
fun HexaTheme(appearance: AppearanceSettings, animations: AnimationSettings, content: @Composable () -> Unit) {
    val dark = when (appearance.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = remember(dark, appearance.accent, appearance.contrast) {
        HexaColors.resolve(dark, Color(appearance.accent), appearance.contrast)
    }
    val tokens = remember(appearance.cornerScale, animations) {
        HexaTokens.create(appearance.cornerScale, animations.enabled, animations.speed)
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = Brand.DeepBlack, secondary = Brand.Silver,
            background = colors.background, surface = colors.surface, surfaceVariant = colors.surfaceHigh,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceHigh, surfaceContainerLow = colors.background,
            onSurface = colors.onSurface, onBackground = colors.onSurface, onSurfaceVariant = colors.onSurfaceMuted,
            outline = colors.outline, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = Color.White, secondary = Brand.SilverDark,
            background = colors.background, surface = colors.surface, surfaceVariant = colors.surfaceHigh,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceHigh, surfaceContainerLow = colors.background,
            onSurface = colors.onSurface, onBackground = colors.onSurface, onSurfaceVariant = colors.onSurfaceMuted,
            outline = colors.outline, error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = HexaType.material, content = content)
    }
}
