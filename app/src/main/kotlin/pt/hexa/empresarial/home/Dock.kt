package pt.hexa.empresarial.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.hexa.empresarial.R
import pt.hexa.empresarial.apps.CatalogSnapshot
import pt.hexa.empresarial.domain.model.DockSettings
import pt.hexa.empresarial.domain.model.DockStyle
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.domain.model.IconStyle
import pt.hexa.empresarial.theme.Brand
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.theme.HexaType
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphIcon

/** Callbacks of the dock, wired to the view model and the drag controller by the home screen. */
class DockActions(
    val onTap: (slot: Int, key: String, bounds: android.graphics.Rect) -> Unit,
    val onLongPress: (slot: Int, key: String, bounds: android.graphics.Rect) -> Unit,
    val onDragStart: (slot: Int, key: String, root: Offset, grab: Offset) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onEmptySlot: (slot: Int) -> Unit,
    val onAllApps: () -> Unit,
)

/**
 * Bottom dock with favourite apps. Medallion styles show monochrome glyphs on metallic discs, like
 * the reference; the plain style shows the original icons.
 */
@Composable
fun Dock(
    dock: DockSettings,
    icons: IconSettings,
    catalog: CatalogSnapshot,
    editMode: Boolean,
    draggingSlot: Int?,
    actions: DockActions,
    modifier: Modifier = Modifier,
) {
    val colors = HexaTheme.colors
    val shape = RoundedCornerShape(HexaTheme.tokens.radiusPill)
    val size = dock.iconSizeDp.dp
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dock.marginDp.dp, vertical = 8.dp)
            .then(if (dock.showBackground) Modifier.clip(shape).background(Color.Black.copy(alpha = dock.backgroundOpacity)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(dock.spacingDp.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dock.slots.forEachIndexed { slot, key ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (key == null) {
                    EmptySlot(size, editMode) { actions.onEmptySlot(slot) }
                } else {
                    val label = catalog.label(key)
                    val missing = catalog.resolve(key) == null
                    Column(
                        Modifier
                            .alpha(if (draggingSlot == slot) 0.3f else 1f)
                            .itemGestures(
                                key = "dock$slot$key",
                                onTap = { actions.onTap(slot, key, it) },
                                onLongPress = { actions.onLongPress(slot, key, it) },
                                onDragStart = { root, grab -> actions.onDragStart(slot, key, root, grab) },
                                onDrag = actions.onDrag,
                                onDragEnd = actions.onDragEnd,
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = label ?: ""
                                role = Role.Button
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DockIcon(key, dock.style, size, icons, missing)
                        if (dock.showLabels && label != null) {
                            Text(label, color = Color.White, style = HexaType.label.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (dock.showAllAppsButton) {
            val desc = stringResource(R.string.action_all_apps_grid)
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(HexaTheme.tokens.radiusSmall))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, colors.metallic(), RoundedCornerShape(HexaTheme.tokens.radiusSmall))
                    .clickable(onClick = actions.onAllApps)
                    .semantics { contentDescription = desc; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.GRID, Color.White, size = size * 0.42f) }
        }
    }
}

@Composable
private fun DockIcon(key: String, style: DockStyle, size: androidx.compose.ui.unit.Dp, icons: IconSettings, missing: Boolean) {
    val colors = HexaTheme.colors
    when (style) {
        DockStyle.PLAIN -> AppIcon(key, size, icons, Modifier.alpha(if (missing) 0.4f else 1f))
        DockStyle.GOLD_MEDALLION, DockStyle.SILVER_MEDALLION -> {
            val brush = if (style == DockStyle.GOLD_MEDALLION) colors.metallic() else colors.silver()
            Box(
                Modifier
                    .size(size)
                    .shadow(HexaTheme.tokens.shadowSmall, CircleShape)
                    .clip(CircleShape)
                    .background(brush)
                    .alpha(if (missing) 0.4f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(key, size * 0.6f, icons.copy(style = IconStyle.MONOCHROME, shape = icons.shape), tintOverride = Brand.SoftWhite.toArgb())
            }
        }
    }
}

@Composable
private fun EmptySlot(size: androidx.compose.ui.unit.Dp, editMode: Boolean, onClick: () -> Unit) {
    val desc = stringResource(R.string.a11y_dock_empty)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = if (editMode) 0.6f else 0.18f), CircleShape)
            .clickable(enabled = editMode, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { if (editMode) GlyphIcon(Glyph.PLUS, Color.White.copy(alpha = 0.8f), size = size * 0.35f) }
}
