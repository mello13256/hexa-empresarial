package pt.hexa.empresarial.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.hexa.empresarial.R
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphIcon

/** Dark translucent round button, readable over any wallpaper. */
@Composable
private fun EditButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    tint: Color = Color.White,
) {
    val colors = HexaTheme.colors
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(if (highlighted) colors.accent else Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (highlighted) Color.Black else tint, size = size * 0.5f) }
}

/**
 * Top of the edit mode: the current page and how to manage it, in a single row of icons
 * (move, main page, delete), plus a one-line hint.
 */
@Composable
fun EditPageBar(vm: HomeViewModel, page: Int, pageCount: Int, defaultPage: Int, modifier: Modifier = Modifier) {
    val colors = HexaTheme.colors
    val isDefault = page == defaultPage
    Column(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditButton(Glyph.ARROW_LEFT, stringResource(R.string.page_move_left), { vm.movePage(page, page - 1) }, enabled = page > 0)
            EditButton(
                Glyph.HOME,
                stringResource(if (isDefault) R.string.page_is_default else R.string.page_make_default),
                { vm.setDefaultPage(page) },
                highlighted = isDefault,
            )
            Text(
                stringResource(R.string.a11y_page_indicator, page + 1, pageCount),
                color = Color.White,
                style = TextStyle(fontSize = 15.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 96.dp),
            )
            EditButton(Glyph.TRASH, stringResource(R.string.page_delete), { vm.removePage(page) }, enabled = pageCount > 1, tint = colors.danger)
            EditButton(Glyph.ARROW_RIGHT, stringResource(R.string.page_move_right), { vm.movePage(page, page + 1) }, enabled = page < pageCount - 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.edit_hint), color = Color.White.copy(alpha = 0.7f), style = TextStyle(fontSize = 12.sp))
    }
}

/** Bottom of the edit mode: what can be added, settings, and done — each with a short label. */
@Composable
fun EditTools(vm: HomeViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        EditTool(Glyph.WIDGET, stringResource(R.string.edit_tool_widget), stringResource(R.string.action_add_widget), vm::openWidgetPicker)
        EditTool(Glyph.GROUP, stringResource(R.string.edit_tool_group), stringResource(R.string.action_new_group), { vm.editGroup(null) })
        EditTool(Glyph.PAGE, stringResource(R.string.edit_tool_page), stringResource(R.string.action_add_page), vm::addPage)
        EditTool(Glyph.SETTINGS, stringResource(R.string.edit_tool_settings), stringResource(R.string.settings_title), { vm.host?.openSettings() })
        EditTool(Glyph.CHECK, stringResource(R.string.action_done), stringResource(R.string.action_done), vm::exitEdit, highlighted = true)
    }
}

@Composable
private fun EditTool(glyph: Glyph, label: String, description: String, onClick: () -> Unit, highlighted: Boolean = false) {
    val colors = HexaTheme.colors
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(HexaTheme.tokens.radiusSmall))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (highlighted) colors.accent else Color.Black.copy(alpha = 0.5f))
                .border(1.dp, if (highlighted) colors.accent else Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, if (highlighted) Color.Black else Color.White, size = 22.dp) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = TextStyle(fontSize = 11.sp), maxLines = 1)
    }
}

/** Small "×" in the corner of each element in edit mode; removes it from the home screen. */
@Composable
fun RemoveBadge(label: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.a11y_remove_item, label)
    // A 32 dp touch area around a 20 dp visible disc.
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onRemove)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onRemove(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(Color(0xFF2A2D35), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.CLOSE, Color.White, size = 12.dp) }
    }
}
