package pt.hexa.empresarial.groups

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.model.CenterElement
import pt.hexa.empresarial.domain.model.CenterKind
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.drawCenterSymbol

/** The centre of the orbital drawer or of a group: symbol, app icon, user image or nothing. */
@Composable
fun CenterView(center: CenterElement, size: Dp, modifier: Modifier = Modifier) {
    val colors = HexaTheme.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (center.kind) {
            CenterKind.NONE -> Unit
            CenterKind.SYMBOL -> Canvas(Modifier.size(size)) {
                drawCenterSymbol(center.symbol, this.center, this.size.minDimension / 2f * 0.9f, colors.silver(), colors.accent)
            }
            CenterKind.APP_ICON -> center.appKey?.let { AppIcon(it, size, IconSettings()) }
            CenterKind.IMAGE -> {
                val context = LocalContext.current
                val name = center.imagePath
                val bmp by produceState<ImageBitmap?>(null, name) {
                    value = name?.let { context.container.images.load(it)?.asImageBitmap() }
                }
                bmp?.let { Image(it, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop) }
            }
        }
    }
}
