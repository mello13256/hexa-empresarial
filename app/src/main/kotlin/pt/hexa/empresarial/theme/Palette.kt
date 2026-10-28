package pt.hexa.empresarial.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import pt.hexa.empresarial.domain.model.ContrastLevel

/** Brand colours: deep black, graphite, soft white, silver grey and metallic gold. */
object Brand {
    val DeepBlack = Color(0xFF0B0C10)
    val Graphite = Color(0xFF1C1E24)
    val GraphiteLight = Color(0xFF2A2D35)
    val SoftWhite = Color(0xFFF4F2EC)
    val Silver = Color(0xFFB8BCC2)
    val SilverLight = Color(0xFFE6E8EB)
    val SilverDark = Color(0xFF8E9196)
    val Gold = Color(0xFFC9A227)
    val GoldLight = Color(0xFFF1DB8F)
    val GoldDark = Color(0xFF8C6A2F)
    val Navy = Color(0xFF151A2E)
    val Danger = Color(0xFFE5484D)
}

/**
 * Colours resolved for the current theme, contrast and accent. Everything that draws reads from
 * here (via [HexaTheme.colors]) so light/dark/contrast changes apply everywhere at once.
 */
@Immutable
data class HexaColors(
    val isDark: Boolean,
    val accent: Color,
    val accentLight: Color,
    val accentDark: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val outline: Color,
    val onWallpaper: Color,
    val onWallpaperShadow: Color,
    val danger: Color,
) {
    /** Metallic gradient of the accent (gold by default). */
    fun metallic(start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
        Brush.linearGradient(listOf(accentDark, accentLight, accent, accentLight.copy(alpha = 0.9f), accentDark), start, end)

    fun silver(start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
        Brush.linearGradient(listOf(Brand.SilverDark, Brand.SilverLight, Brand.Silver, Brand.SilverLight, Brand.SilverDark), start, end)

    companion object {
        fun resolve(dark: Boolean, accent: Color, contrast: ContrastLevel): HexaColors {
            val boost = when (contrast) {
                ContrastLevel.STANDARD -> 0f
                ContrastLevel.MEDIUM -> 0.5f
                ContrastLevel.HIGH -> 1f
            }
            return if (dark) {
                HexaColors(
                    isDark = true,
                    accent = accent,
                    accentLight = lerp(accent, Color.White, 0.45f),
                    accentDark = lerp(accent, Color.Black, 0.35f),
                    background = lerp(Brand.DeepBlack, Color.Black, boost),
                    surface = lerp(Brand.Graphite, Color.Black, boost * 0.6f),
                    surfaceHigh = Brand.GraphiteLight,
                    onSurface = lerp(Brand.SoftWhite, Color.White, boost),
                    onSurfaceMuted = lerp(Brand.Silver, Color.White, boost * 0.7f),
                    outline = lerp(Color(0x33FFFFFF), Color(0x99FFFFFF), boost),
                    onWallpaper = Color.White,
                    onWallpaperShadow = Color.Black.copy(alpha = 0.55f + 0.35f * boost),
                    danger = Brand.Danger,
                )
            } else {
                HexaColors(
                    isDark = false,
                    accent = lerp(accent, Color.Black, 0.12f + 0.25f * boost),
                    accentLight = lerp(accent, Color.White, 0.35f),
                    accentDark = lerp(accent, Color.Black, 0.4f),
                    background = Brand.SoftWhite,
                    surface = lerp(Color(0xFFFFFFFF), Color.White, boost),
                    surfaceHigh = Color(0xFFE9E6DD),
                    onSurface = lerp(Color(0xFF15161B), Color.Black, boost),
                    onSurfaceMuted = lerp(Color(0xFF5B5E66), Color.Black, boost * 0.6f),
                    outline = lerp(Color(0x22000000), Color(0x88000000), boost),
                    onWallpaper = Color.White,
                    onWallpaperShadow = Color.Black.copy(alpha = 0.55f + 0.35f * boost),
                    danger = Color(0xFFC62828),
                )
            }
        }
    }
}
