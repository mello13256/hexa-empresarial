package pt.hexa.empresarial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.hexa.empresarial.theme.HexaTheme

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

/** Round or square icon button with ripple feedback and a content description. */
@Composable
fun GlyphButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    square: Boolean = false,
    selected: Boolean = false,
    tint: Color = HexaTheme.colors.onSurface,
) {
    val colors = HexaTheme.colors
    val shape = if (square) RoundedCornerShape(HexaTheme.tokens.radiusSmall) else CircleShape
    Box(
        modifier
            .size(HexaTheme.tokens.minTouchTarget)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) colors.accent else colors.outline, shape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (selected) colors.accent else tint) }
}

@Composable
fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true) {
    val colors = HexaTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.background),
        )
    }
}

@Composable
fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit, steps: Int = 0, description: String? = null) {
    val colors = HexaTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = colors.accent)
        }
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
        Slider(
            value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
        )
    }
}

@Composable
fun <T> SettingChoice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, description: String? = null) {
    val colors = HexaTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in options) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent.copy(alpha = 0.2f), selectedLabelColor = colors.onSurface),
                )
            }
        }
    }
}

@Composable
fun SettingLink(title: String, value: String? = null, onClick: () -> Unit, description: String? = null) {
    val colors = HexaTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceMuted)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.accent, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(pt.hexa.empresarial.LocaleOverride.PT_PT),
        style = MaterialTheme.typography.labelLarge,
        color = HexaTheme.colors.accent,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = HexaTheme.colors.onSurfaceMuted, modifier = modifier.padding(vertical = 4.dp))
}
