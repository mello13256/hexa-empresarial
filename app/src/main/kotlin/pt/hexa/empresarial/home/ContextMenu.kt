package pt.hexa.empresarial.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import pt.hexa.empresarial.R
import pt.hexa.empresarial.domain.model.AppItem
import pt.hexa.empresarial.domain.model.ClockItem
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.SearchBarItem
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphIcon

/** One entry of a context menu. Entries without a [glyph] are app shortcuts, listed first. */
data class MenuEntry(val label: String, val danger: Boolean = false, val glyph: Glyph? = null, val action: () -> Unit)

/**
 * Context menu shown next to the long-pressed element, like Android's app shortcuts popup. It is
 * placed below the anchor when there is room, otherwise above, and kept inside the screen.
 */
@Composable
fun ContextMenuOverlay(target: MenuTarget, entries: List<MenuEntry>, title: String?, onDismiss: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    val colors = HexaTheme.colors
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val margin = with(density) { 12.dp.roundToPx() }
        val a = target.anchor
        val x = (a.centerX() - menuSize.width / 2).coerceIn(margin, (w - menuSize.width - margin).coerceAtLeast(margin))
        val below = a.bottom + margin / 2
        val y = if (below + menuSize.height < h - margin) below else (a.top - menuSize.height - margin / 2).coerceAtLeast(margin)
        val shape = RoundedCornerShape(HexaTheme.tokens.radiusMedium)
        Column(
            Modifier
                .offset { IntOffset(x, y) }
                .width(240.dp)
                .onSizeChanged { menuSize = it }
                .background(colors.surface.copy(alpha = 0.98f), shape)
                .border(1.dp, colors.accent.copy(alpha = 0.35f), shape)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            if (title != null) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        icon()
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = colors.outline)
            }
            entries.forEachIndexed { i, e ->
                val previous = entries.getOrNull(i - 1)
                // Separators between the groups: shortcuts, actions, destructive actions.
                if (previous != null && ((previous.glyph == null && e.glyph != null) || (!previous.danger && e.danger))) {
                    HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 4.dp))
                }
                val tint = if (e.danger) colors.danger else colors.onSurface
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = e.action)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (e.glyph != null) GlyphIcon(e.glyph, if (e.danger) tint else colors.onSurfaceMuted, size = 20.dp)
                    else GlyphIcon(Glyph.ARROW_RIGHT, colors.accent, size = 20.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(e.label, style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Menu entries for a home item that is not an app (widget, group, clock, search bar). */
@Composable
fun itemMenuEntries(vm: HomeViewModel, target: MenuTarget.Item, density: Float): Pair<String?, List<MenuEntry>> {
    val item = vm.itemById(target.itemId) ?: return null to emptyList()
    val remove = MenuEntry(stringResource(R.string.action_remove), danger = true, glyph = Glyph.CLOSE) { vm.removeItem(item.id) }
    val edit = MenuEntry(stringResource(R.string.action_edit_home), glyph = Glyph.EDIT) { vm.enterEdit() }
    return when (item) {
        is WidgetItem -> item.label to buildList {
            if (item.needsRestore) add(MenuEntry(stringResource(R.string.action_restore_widget), glyph = Glyph.PLUS) { vm.closeMenu(); vm.restoreWidget(item, density) })
            else add(MenuEntry(stringResource(R.string.action_resize), glyph = Glyph.WIDGET) { vm.closeMenu(); vm.enterEdit(); vm.selectWidget(item.id) })
            add(remove)
        }
        is GroupItem -> (vm.state.value.group(item.groupId)?.name) to listOf(
            MenuEntry(stringResource(R.string.action_open_group), glyph = Glyph.GROUP) { vm.openGroup(item.groupId, target.anchor) },
            MenuEntry(stringResource(R.string.action_edit_group), glyph = Glyph.EDIT) { vm.editGroup(item.groupId) },
            remove,
        )
        is ClockItem -> stringResource(R.string.item_clock) to listOf(edit, remove)
        is SearchBarItem -> stringResource(R.string.item_search_bar) to listOf(edit, remove)
        is AppItem -> null to emptyList()
    }
}

/** A semi-transparent full-screen scrim used behind dialogs drawn over the home screen. */
@Composable
fun Scrim(alpha: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}
