package pt.hexa.empresarial.groups

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import pt.hexa.empresarial.R
import pt.hexa.empresarial.domain.groups.GroupGeometry
import pt.hexa.empresarial.domain.model.AppGroup
import pt.hexa.empresarial.domain.model.CenterKind
import pt.hexa.empresarial.domain.model.GroupOpenMode
import pt.hexa.empresarial.domain.model.GroupSize
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.home.HomeViewModel
import pt.hexa.empresarial.home.MenuSource
import pt.hexa.empresarial.home.Scrim
import pt.hexa.empresarial.home.itemGestures
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.theme.HexaType
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphButton
import kotlin.math.min
import kotlin.math.roundToInt

fun iconSizeFor(size: GroupSize): Int = when (size) {
    GroupSize.SMALL -> 44
    GroupSize.MEDIUM -> 52
    GroupSize.LARGE -> 60
}

/**
 * An open group: its apps unfold from the centre into the group's shape (circle, radial,
 * hexagon, grid or spiral), either from where the group sits or in the middle of the screen.
 */
@Composable
fun GroupOverlay(vm: HomeViewModel, group: AppGroup, origin: android.graphics.Rect?, icons: IconSettings) {
    val density = LocalDensity.current
    val colors = HexaTheme.colors
    val tokens = HexaTheme.tokens
    val open = remember(group.id) { Animatable(0f) }
    val duration = if (tokens.animationsEnabled) (tokens.durationMedium / group.animationSpeed.coerceIn(0.5f, 2f)).toInt() else 0
    LaunchedEffect(group.id) { open.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) }

    Scrim(0.55f * open.value) { vm.closeGroup() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val iconPx = with(density) { iconSizeFor(group.size).dp.toPx() }
        val gapPx = with(density) { 14.dp.toPx() }
        val centerPx = if (group.center.kind == CenterKind.NONE) 0f else iconPx * 1.2f
        val arrangement = remember(group.shape, group.appKeys.size, iconPx, centerPx) {
            GroupGeometry.arrange(group.shape, group.appKeys.size, iconPx, gapPx, centerPx)
        }
        val margin = with(density) { 24.dp.toPx() }
        val extent = maxOf(arrangement.extent(iconPx), centerPx)
        val scale = min(1f, (min(w, h) - 2 * margin) / extent)
        val half = extent * scale / 2f
        val (cx, cy) = if (group.openMode == GroupOpenMode.EXPAND_IN_PLACE && origin != null) {
            origin.exactCenterX().coerceIn(half + margin, w - half - margin) to origin.exactCenterY().coerceIn(half + margin * 3, h - half - margin * 3)
        } else {
            w / 2f to h / 2f
        }
        val p = open.value
        val iconDp = with(density) { (iconPx * scale).toDp() }

        // Name above the composition, edit button below.
        Text(
            group.name,
            style = HexaType.date.copy(brush = colors.metallic()),
            modifier = Modifier
                .offset { IntOffset(0, (cy - half - with(density) { 44.dp.toPx() }).roundToInt().coerceAtLeast(0)) }
                .wrapContentSize()
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = p },
        )
        if (group.center.kind != CenterKind.NONE) {
            val cs = centerPx * scale
            Box(
                Modifier
                    .offset { IntOffset((cx - cs / 2f).roundToInt(), (cy - cs / 2f).roundToInt()) }
                    .size(with(density) { cs.toDp() })
                    .graphicsLayer { scaleX = 0.7f + 0.3f * p; scaleY = 0.7f + 0.3f * p }
                    .clip(CircleShape)
                    .background(Color(0xFF202126))
                    .border(1.dp, colors.metallic(), CircleShape),
                contentAlignment = Alignment.Center,
            ) { CenterView(group.center, with(density) { (cs * 0.7f).toDp() }) }
        }
        if (group.appKeys.isEmpty()) {
            Text(stringResource(R.string.group_empty), color = Color.White, modifier = Modifier.align(Alignment.Center).padding(top = 120.dp))
        }
        group.appKeys.forEachIndexed { i, key ->
            val spot = arrangement.spots.getOrNull(i) ?: return@forEachIndexed
            val x = cx + spot.dx * scale * p - iconPx * scale / 2f
            val y = cy + spot.dy * scale * p - iconPx * scale / 2f
            val label = vm.catalog.value.label(key) ?: ""
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(iconDp)
                    .graphicsLayer { alpha = p }
                    .itemGestures(
                        key = key,
                        onTap = { vm.launch(key, it); vm.closeGroup() },
                        onLongPress = { vm.showAppMenu(key, MenuSource.GROUP, it) },
                        onDragStart = { _, _ -> },
                        onDrag = {},
                        onDragEnd = {},
                    )
                    .semantics { contentDescription = label; role = Role.Button },
            ) { AppIcon(key, iconDp, icons) }
        }
        GlyphButton(
            Glyph.EDIT, stringResource(R.string.action_edit_group), { vm.editGroup(group.id) },
            tint = Color.White,
            modifier = Modifier
                .offset { IntOffset((cx - with(density) { 24.dp.toPx() }).roundToInt(), (cy + half + with(density) { 12.dp.toPx() }).roundToInt().coerceAtMost((h - with(density) { 72.dp.toPx() }).roundToInt())) }
                .graphicsLayer { alpha = p },
        )
    }
}
