package pt.hexa.empresarial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import pt.hexa.empresarial.apps.IconSpec
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.theme.HexaTheme
import kotlin.math.roundToInt

/** Real app icon, rendered with the user's icon settings. Shows a faint disc while loading. */
@Composable
fun AppIcon(appKey: String, size: Dp, settings: IconSettings, modifier: Modifier = Modifier, tintOverride: Int? = null) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.toPx() }.roundToInt()
    val colors = HexaTheme.colors
    val spec = IconSpec.from(settings, px, tintOverride ?: colors.onWallpaper.toArgb(), colors.accent.toArgb())
    val provider = context.container.icons
    val bitmap by produceState<ImageBitmap?>(provider.peek(appKey, spec)?.asImageBitmap(), appKey, spec) {
        if (value == null) value = provider.load(appKey, spec)?.asImageBitmap()
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = modifier.size(size))
    } else {
        Canvas(modifier.size(size)) { drawCircle(colors.onSurfaceMuted.copy(alpha = 0.15f)) }
    }
}
