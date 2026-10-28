package pt.hexa.empresarial.groups

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import pt.hexa.empresarial.R
import pt.hexa.empresarial.container
import pt.hexa.empresarial.domain.groups.GroupGeometry
import pt.hexa.empresarial.domain.model.AppGroup
import pt.hexa.empresarial.domain.model.CenterElement
import pt.hexa.empresarial.domain.model.CenterKind
import pt.hexa.empresarial.domain.model.CenterSymbol
import pt.hexa.empresarial.domain.model.GroupOpenMode
import pt.hexa.empresarial.domain.model.GroupShape
import pt.hexa.empresarial.domain.model.GroupSize
import pt.hexa.empresarial.domain.model.IconSettings
import pt.hexa.empresarial.domain.model.Ids
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.AppPickerDialog
import pt.hexa.empresarial.ui.Hint
import pt.hexa.empresarial.ui.SectionHeader
import pt.hexa.empresarial.ui.SettingChoice
import pt.hexa.empresarial.ui.SettingLink
import pt.hexa.empresarial.ui.SettingSlider
import kotlin.math.min

@Composable
fun shapeLabel(s: GroupShape): String = stringResource(
    when (s) {
        GroupShape.CIRCLE -> R.string.shape_circle
        GroupShape.RADIAL -> R.string.shape_radial
        GroupShape.HEXAGON -> R.string.shape_hexagon
        GroupShape.GRID -> R.string.shape_grid
        GroupShape.SPIRAL -> R.string.shape_spiral
    },
)

@Composable
fun symbolLabel(s: CenterSymbol): String = stringResource(
    when (s) {
        CenterSymbol.NETWORK -> R.string.symbol_network
        CenterSymbol.HEXAGON -> R.string.symbol_hexagon
        CenterSymbol.DIAMOND -> R.string.symbol_diamond
        CenterSymbol.ORBIT -> R.string.symbol_orbit
        CenterSymbol.MONOGRAM -> R.string.symbol_monogram
    },
)

/** Shared editor for a centre element (groups and the orbital drawer). */
@Composable
fun CenterElementEditor(center: CenterElement, icons: IconSettings, onChange: (CenterElement) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickApp by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = context.container.images.import(uri, 512)
                if (name != null) onChange(center.copy(kind = CenterKind.IMAGE, imagePath = name))
            }
        }
    }
    SettingChoice(
        stringResource(R.string.center_kind),
        listOf(
            CenterKind.SYMBOL to stringResource(R.string.center_symbol),
            CenterKind.APP_ICON to stringResource(R.string.center_app_icon),
            CenterKind.IMAGE to stringResource(R.string.center_image),
            CenterKind.NONE to stringResource(R.string.center_none),
        ),
        center.kind,
        { kind ->
            when (kind) {
                CenterKind.APP_ICON -> pickApp = true
                CenterKind.IMAGE -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else -> onChange(center.copy(kind = kind))
            }
        },
    )
    if (center.kind == CenterKind.SYMBOL) {
        SettingChoice(stringResource(R.string.center_symbol), CenterSymbol.entries.map { it to symbolLabel(it) }, center.symbol, { onChange(center.copy(symbol = it)) })
    }
    if (center.kind == CenterKind.APP_ICON) {
        SettingLink(stringResource(R.string.center_app_icon), center.appKey?.let { context.container.catalog.snapshot.value.label(it) } ?: "", { pickApp = true })
    }
    if (center.kind == CenterKind.IMAGE) {
        SettingLink(stringResource(R.string.center_change_image), null, { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    }
    if (pickApp) {
        AppPickerDialog(
            title = stringResource(R.string.center_app_icon),
            icons = icons,
            onDismiss = { pickApp = false },
            onPick = { apps -> apps.firstOrNull()?.let { onChange(center.copy(kind = CenterKind.APP_ICON, appKey = it.key)) }; pickApp = false },
        )
    }
}

/** Create or edit a group: name, apps, shape, centre, size, opening behaviour and speed. */
@Composable
fun GroupEditorDialog(
    existing: AppGroup?,
    icons: IconSettings,
    onSave: (AppGroup, isNew: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HexaTheme.colors
    val defaultName = stringResource(R.string.group_default_name)
    var g by remember(existing?.id) { mutableStateOf(existing ?: AppGroup(Ids.group(), defaultName)) }
    var pickApps by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(
                    stringResource(if (existing == null) R.string.group_new_title else R.string.group_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = g.name,
                    onValueChange = { g = g.copy(name = it.take(40)) },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                GroupPreview(g)
                SectionHeader(stringResource(R.string.group_apps))
                SettingLink(
                    stringResource(R.string.group_choose_apps),
                    LocalContext.current.resources.getQuantityString(R.plurals.app_count, g.appKeys.size, g.appKeys.size),
                    { pickApps = true },
                )
                SectionHeader(stringResource(R.string.group_shape))
                SettingChoice(stringResource(R.string.group_shape), GroupShape.entries.map { it to shapeLabel(it) }, g.shape, { g = g.copy(shape = it) })
                SettingChoice(
                    stringResource(R.string.group_size),
                    listOf(GroupSize.SMALL to stringResource(R.string.size_small), GroupSize.MEDIUM to stringResource(R.string.size_medium), GroupSize.LARGE to stringResource(R.string.size_large)),
                    g.size, { g = g.copy(size = it) },
                )
                SettingChoice(
                    stringResource(R.string.group_open_mode),
                    listOf(GroupOpenMode.EXPAND_IN_PLACE to stringResource(R.string.group_open_in_place), GroupOpenMode.FULL_SCREEN to stringResource(R.string.group_open_center)),
                    g.openMode, { g = g.copy(openMode = it) },
                )
                SettingSlider(stringResource(R.string.group_animation_speed), g.animationSpeed, 0.5f..2f, "%.1f×".format(pt.hexa.empresarial.LocaleOverride.PT_PT, g.animationSpeed), { g = g.copy(animationSpeed = it) })
                SectionHeader(stringResource(R.string.group_center))
                CenterElementEditor(g.center, icons) { g = g.copy(center = it) }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (existing != null) {
                        OutlinedButton(onClick = { onDelete(existing.id) }) { Text(stringResource(R.string.action_delete), color = colors.danger) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { onSave(g.copy(name = g.name.trim().ifEmpty { defaultName }), existing == null) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    ) { Text(stringResource(R.string.action_save)) }
                }
                if (g.appKeys.isEmpty()) Hint(stringResource(R.string.group_empty_hint))
            }
        }
    }
    if (pickApps) {
        AppPickerDialog(
            title = stringResource(R.string.group_choose_apps),
            icons = icons,
            multiple = true,
            initiallySelected = g.appKeys,
            maxSelection = AppGroup.MAX_APPS,
            onDismiss = { pickApps = false },
            onPick = { apps -> g = g.copy(appKeys = apps.map { it.key }); pickApps = false },
        )
    }
}

/** Live preview of the group's shape with neutral discs. */
@Composable
private fun GroupPreview(g: AppGroup) {
    val colors = HexaTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val n = g.appKeys.size.coerceAtLeast(6)
        Canvas(Modifier.fillMaxSize()) {
            val icon = 20f * density
            val center = if (g.center.kind == CenterKind.NONE) 0f else icon * 1.2f
            val a = GroupGeometry.arrange(g.shape, n, icon, 4f * density, center)
            val s = min(1f, (min(size.width, size.height) - 8f) / a.extent(icon))
            val c = Offset(size.width / 2f, size.height / 2f)
            if (center > 0f) drawCircle(colors.accent.copy(alpha = 0.7f), center / 2f * s, c)
            a.spots.forEachIndexed { i, p ->
                drawCircle(if (i < g.appKeys.size) colors.onSurface.copy(alpha = 0.75f) else colors.onSurfaceMuted.copy(alpha = 0.3f), icon / 2f * s, Offset(c.x + p.dx * s, c.y + p.dy * s))
            }
        }
    }
}
