package pt.hexa.empresarial.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import pt.hexa.empresarial.apps.CatalogSnapshot
import pt.hexa.empresarial.domain.apps.AppEntry
import pt.hexa.empresarial.domain.model.CenterKind
import pt.hexa.empresarial.domain.model.IconBackdrop
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.OrbitalOpening
import pt.hexa.empresarial.domain.model.OrbitalSettings
import pt.hexa.empresarial.domain.orbital.OrbitalGeometry
import pt.hexa.empresarial.domain.orbital.OrbitalLayout
import pt.hexa.empresarial.domain.orbital.OrbitalParams
import pt.hexa.empresarial.groups.CenterView
import pt.hexa.empresarial.home.HomeViewModel
import pt.hexa.empresarial.home.MenuSource
import pt.hexa.empresarial.home.itemGestures
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.AppIcon
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x)
}

/**
 * Orbital drawer: apps on concentric orbits around a central element. Positions come from
 * [OrbitalGeometry] (no hard-coded coordinates); when apps do not fit, extra pages are swiped
 * horizontally. The opening animation is driven by the drawer [progress]: orbits fade in, then
 * icons travel outwards to their places, inner orbits first.
 */
@Composable
fun OrbitalDrawer(vm: HomeViewModel, state: LauncherState, apps: List<AppEntry>, catalog: CatalogSnapshot, progress: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val o = state.orbital
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat() - with(density) { 24.dp.toPx() } // room for page dots
        val layout = remember(apps.size, w, h, o, density.density) {
            with(density) {
                OrbitalGeometry.layout(
                    apps.size,
                    OrbitalParams(
                        width = w, height = h,
                        iconSize = o.iconSizeDp.dp.toPx(),
                        centerSize = (if (o.center.kind == CenterKind.NONE) 16.dp else o.centerSizeDp.dp).toPx(),
                        minGap = o.minGapDp.dp.toPx(),
                        orbitSpacing = o.orbitSpacingDp.dp.toPx(),
                        maxOrbits = o.maxOrbits,
                        maxRadiusFraction = o.maxRadiusFraction,
                        edgeMargin = 8.dp.toPx(),
                        minIconSize = minOf(o.iconSizeDp, 36).dp.toPx(),
                    ),
                )
            }
        }
        if (apps.isEmpty()) {
            Text(stringResource(R.string.drawer_empty), color = LocalDrawerFg.current.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val pager = rememberPagerState { layout.pageCount }
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                OrbitalPage(vm, state, o, layout, page, apps, catalog, progress)
            }
            if (layout.pageCount > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(layout.pageCount) { i ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 8.dp else 6.dp)
                                .background(if (i == pager.currentPage) HexaTheme.colors.accent else Color.White.copy(alpha = 0.35f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitalPage(
    vm: HomeViewModel,
    state: LauncherState,
    o: OrbitalSettings,
    layout: OrbitalLayout,
    page: Int,
    apps: List<AppEntry>,
    catalog: CatalogSnapshot,
    progress: Float,
) {
    val density = LocalDensity.current
    val colors = HexaTheme.colors
    val pg = layout.pages[page]
    val lineColor = Color(o.lineColor)
    val ringCount = layout.ringRadii.size
    val tilt = o.opening == OrbitalOpening.PERSPECTIVE
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (tilt) {
                    // Starts as a tilted ring (seen in perspective) and settles flat.
                    rotationX = (1f - ease(progress)) * 62f
                    cameraDistance = 14f * density.density
                }
            },
    ) {
        if (o.showLines) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(layout.centerX, layout.centerY)
                val radii = layout.ringRadii
                for ((k, r) in radii.withIndex()) {
                    val a = ease((progress - k * 0.06f) / 0.6f) * o.orbitOpacity
                    drawCircle(lineColor.copy(alpha = a), r, c, style = Stroke(1.1f * density.density))
                    // Thin decorative rings between orbits, like the reference composition.
                    val next = radii.getOrNull(k + 1) ?: (r + (radii.getOrNull(1)?.minus(radii[0]) ?: layout.iconSize))
                    for (d in 1..o.decorativeRings) {
                        val rr = r + (next - r) * d / (o.decorativeRings + 1)
                        drawCircle(lineColor.copy(alpha = a * 0.45f), rr, c, style = Stroke(0.7f * density.density))
                    }
                }
                if (radii.isNotEmpty()) {
                    val inner = (layout.centerSize / 2f + (radii[0] - layout.centerSize / 2f) * 0.45f)
                    drawCircle(lineColor.copy(alpha = ease(progress) * o.orbitOpacity * 0.6f), inner, c, style = Stroke(0.7f * density.density))
                }
            }
        }

        // Central element.
        if (o.center.kind != CenterKind.NONE) {
            val cs = layout.centerSize
            val scale = 0.85f + 0.15f * ease(progress)
            Box(
                Modifier
                    .offset { IntOffset((layout.centerX - cs / 2f).roundToInt(), (layout.centerY - cs / 2f).roundToInt()) }
                    .size(with(density) { cs.toDp() })
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(Color(0xFF26272C))
                    .border(1.dp, colors.accent.copy(alpha = 0.35f + 0.35f * ease(progress)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { CenterView(o.center, with(density) { (cs * 0.66f).toDp() }) }
        }

        // Icons.
        val iconPx = layout.iconSize
        val iconDp = with(density) { iconPx.toDp() }
        val order = apps.map { it.key }
        pg.orbits.forEachIndexed { k, orbit ->
            val local = ease((progress - 0.08f - k * 0.07f) / 0.65f)
            for (slot in orbit.slots) {
                val app = apps.getOrNull(slot.index) ?: continue
                val r = when (o.opening) {
                    OrbitalOpening.RADIAL -> orbit.radius * (0.35f + 0.65f * local)
                    else -> orbit.radius
                }
                val x = layout.centerX + r * cos(slot.angle) - iconPx / 2f
                val y = layout.centerY + r * sin(slot.angle) - iconPx / 2f
                val missing = catalog.byKey[app.key]?.enabled == false
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(iconDp)
                        .graphicsLayer { alpha = local * (if (missing) 0.45f else 1f) }
                        .then(if (o.shadows && o.iconBackdrop != IconBackdrop.NONE) Modifier.shadow(HexaTheme.tokens.shadowSmall, CircleShape) else Modifier)
                        .then(backdrop(o.iconBackdrop, colors.metallic()))
                        .itemGestures(
                            key = app.key,
                            onTap = { vm.launch(app.key, it) },
                            onLongPress = { vm.showAppMenu(app.key, MenuSource.ORBITAL, it) },
                            onDragStart = { _, _ -> },
                            onDrag = {},
                            onDragEnd = {},
                        )
                        .semantics { contentDescription = app.label; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    val inner = if (o.iconBackdrop == IconBackdrop.NONE) iconDp else iconDp * 0.62f
                    AppIcon(app.key, inner, state.icons)
                }
            }
        }
        // Keep the current order available to the "closer / further" menu actions.
        SideEffect { vm.orbitalOrder = order }
    }
}

private fun backdrop(kind: IconBackdrop, gold: androidx.compose.ui.graphics.Brush): Modifier = when (kind) {
    IconBackdrop.NONE -> Modifier
    IconBackdrop.DARK_DISC -> Modifier.clip(CircleShape).background(Color(0xFF2C2D33)).border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
    IconBackdrop.LIGHT_DISC -> Modifier.clip(CircleShape).background(Color(0xFFE9E7E1))
    IconBackdrop.GOLD_DISC -> Modifier.clip(CircleShape).background(gold)
}
