package pt.hexa.empresarial.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design system values in one place. Components read them through [HexaTheme.tokens]; corner
 * radii follow the user's corner preference and durations the animation preference.
 */
@Immutable
data class HexaTokens(
    // Spacing scale (4 dp grid).
    val space1: Dp = 4.dp,
    val space2: Dp = 8.dp,
    val space3: Dp = 12.dp,
    val space4: Dp = 16.dp,
    val space5: Dp = 24.dp,
    val space6: Dp = 32.dp,
    // Screen margins.
    val screenMargin: Dp = 16.dp,
    // Corner radii (scaled).
    val radiusSmall: Dp = 10.dp,
    val radiusMedium: Dp = 18.dp,
    val radiusLarge: Dp = 28.dp,
    val radiusPill: Dp = 50.dp,
    // Elevation used for soft shadows.
    val shadowSmall: Dp = 4.dp,
    val shadowMedium: Dp = 10.dp,
    // Icon sizes.
    val iconSmall: Dp = 20.dp,
    val iconMedium: Dp = 40.dp,
    val iconListItem: Dp = 44.dp,
    // Interaction.
    val minTouchTarget: Dp = 48.dp,
    val pressedScale: Float = 0.94f,
    val disabledAlpha: Float = 0.38f,
    // Durations in ms (scaled; 0 when animations are off).
    val durationShort: Int = 140,
    val durationMedium: Int = 260,
    val durationLong: Int = 420,
    val animationsEnabled: Boolean = true,
) {
    companion object {
        fun create(cornerScale: Float, animationsEnabled: Boolean, speed: Float): HexaTokens {
            val base = HexaTokens()
            fun d(ms: Int) = if (!animationsEnabled) 0 else (ms * speed).toInt().coerceAtLeast(1)
            return base.copy(
                radiusSmall = base.radiusSmall * cornerScale,
                radiusMedium = base.radiusMedium * cornerScale,
                radiusLarge = base.radiusLarge * cornerScale,
                radiusPill = if (cornerScale < 0.2f) base.radiusSmall * cornerScale else base.radiusPill,
                durationShort = d(base.durationShort),
                durationMedium = d(base.durationMedium),
                durationLong = d(base.durationLong),
                animationsEnabled = animationsEnabled,
            )
        }
    }
}
