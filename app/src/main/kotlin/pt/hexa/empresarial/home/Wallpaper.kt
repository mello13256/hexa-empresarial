package pt.hexa.empresarial.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.model.AppearanceSettings
import pt.hexa.empresarial.domain.model.WallpaperMode
import pt.hexa.empresarial.theme.Brand

/**
 * Home background. SYSTEM shows the device wallpaper (the window is transparent); the other modes
 * draw over it. A light scrim keeps labels readable on any image.
 */
@Composable
fun Wallpaper(a: AppearanceSettings) {
    when (a.wallpaper) {
        WallpaperMode.SYSTEM -> Unit
        WallpaperMode.SOLID -> Box(Modifier.fillMaxSize().background(Color(a.wallpaperColor)))
        WallpaperMode.EXECUTIVE_GRADIENT -> ExecutiveGradient()
        WallpaperMode.IMAGE -> {
            val context = LocalContext.current
            val bmp by produceState<ImageBitmap?>(null, a.wallpaperImage) {
                value = a.wallpaperImage?.let { context.container.images.load(it)?.asImageBitmap() }
            }
            val b = bmp
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else ExecutiveGradient()
        }
    }
    if (a.scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = a.scrim)))
}

/** Deep navy gradient with two large, very soft discs: calm and corporate. */
@Composable
fun ExecutiveGradient() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B2036), Brand.Navy, Color(0xFF0E1020))))
        drawCircle(
            Brush.radialGradient(listOf(Color(0x22FFFFFF), Color.Transparent), center = Offset(size.width * 1.05f, size.height * 0.18f), radius = size.width * 0.75f),
            radius = size.width * 0.75f, center = Offset(size.width * 1.05f, size.height * 0.18f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0x1AC9A227), Color.Transparent), center = Offset(-size.width * 0.1f, size.height * 0.72f), radius = size.width * 0.8f),
            radius = size.width * 0.8f, center = Offset(-size.width * 0.1f, size.height * 0.72f),
        )
    }
}
