package pt.hexa.empresarial.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import pt.hexa.empresarial.LocaleOverride
import pt.hexa.empresarial.R
import pt.hexa.empresarial.domain.apps.AppOrdering
import pt.hexa.empresarial.domain.grid.GridResult
import pt.hexa.empresarial.domain.grid.HomeGrid
import pt.hexa.empresarial.domain.model.AppSort
import pt.hexa.empresarial.domain.model.Cell
import pt.hexa.empresarial.domain.model.ClockItem
import pt.hexa.empresarial.domain.model.ContrastLevel
import pt.hexa.empresarial.domain.model.DockSettings
import pt.hexa.empresarial.domain.model.DockStyle
import pt.hexa.empresarial.domain.model.DrawerView
import pt.hexa.empresarial.domain.model.GestureAction
import pt.hexa.empresarial.domain.model.GestureBinding
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.HomeItem
import pt.hexa.empresarial.domain.model.HomeLayout
import pt.hexa.empresarial.domain.model.IconBackdrop
import pt.hexa.empresarial.domain.model.IconShape
import pt.hexa.empresarial.domain.model.IconStyle
import pt.hexa.empresarial.domain.model.Ids
import pt.hexa.empresarial.domain.model.LabelAlignment
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.LogoPlacement
import pt.hexa.empresarial.domain.model.OrbitalBackground
import pt.hexa.empresarial.domain.model.OrbitalDistribution
import pt.hexa.empresarial.domain.model.OrbitalOpening
import pt.hexa.empresarial.domain.model.OrbitalSettings
import pt.hexa.empresarial.domain.model.SearchBarItem
import pt.hexa.empresarial.domain.model.ThemeMode
import pt.hexa.empresarial.domain.model.WallpaperMode
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.domain.orbital.OrbitalGeometry
import pt.hexa.empresarial.domain.orbital.OrbitalParams
import pt.hexa.empresarial.groups.CenterElementEditor
import pt.hexa.empresarial.groups.GroupEditorDialog
import pt.hexa.empresarial.groups.shapeLabel
import pt.hexa.empresarial.home.Dock
import pt.hexa.empresarial.home.DockActions
import pt.hexa.empresarial.system.SystemActionsService
import pt.hexa.empresarial.theme.HexaLogo
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.AppPickerDialog
import pt.hexa.empresarial.ui.Hint
import pt.hexa.empresarial.ui.SectionHeader
import pt.hexa.empresarial.ui.SettingChoice
import pt.hexa.empresarial.ui.SettingLink
import pt.hexa.empresarial.ui.SettingSlider
import pt.hexa.empresarial.ui.SettingSwitch

private fun pct(v: Float) = "${(v * 100).toInt()} %"

private fun times(v: Float) = String.format(LocaleOverride.PT_PT, "%.1f×", v)

/** Colour swatches with names (accent, lines, solid wallpaper). */
@Composable
fun ColorSwatches(title: String, options: List<Pair<Long, Int>>, selected: Long, onSelect: (Long) -> Unit) {
    val colors = HexaTheme.colors
    Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((value, label) in options) {
            val name = stringResource(label)
            val isSel = (value and 0xFFFFFF) == (selected and 0xFFFFFF)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) colors.onSurface else colors.outline, CircleShape)
                    .clickable { onSelect(value) }
                    .semantics { contentDescription = name },
            )
        }
    }
}

val ACCENTS = listOf(
    0xFFC9A227 to R.string.color_gold,
    0xFFD8C08A to R.string.color_champagne,
    0xFFB0793F to R.string.color_bronze,
    0xFFB8BCC2 to R.string.color_silver,
    0xFFE5E4E2 to R.string.color_platinum,
    0xFF4F6D9A to R.string.color_steel_blue,
)

// ---- Ecrã inicial ---------------------------------------------------------------------------------

@Composable
fun HomeSettings(env: SettingsEnv) {
    val s = env.state
    val home = s.home
    val items = home.pages.flatMap { it.items }
    val hasClock = items.any { it is ClockItem }
    val hasSearch = items.any { it is SearchBarItem }
    SectionHeader(stringResource(R.string.home_grid))
    SettingSlider(stringResource(R.string.home_columns), home.columns.toFloat(), HomeLayout.MIN_COLUMNS.toFloat()..HomeLayout.MAX_COLUMNS.toFloat(), "${home.columns}", { v ->
        env.update { it.copy(home = HomeGrid.resizeGrid(it.home, v.toInt(), it.home.rows)) }
    }, steps = HomeLayout.MAX_COLUMNS - HomeLayout.MIN_COLUMNS - 1)
    SettingSlider(stringResource(R.string.home_rows), home.rows.toFloat(), HomeLayout.MIN_ROWS.toFloat()..HomeLayout.MAX_ROWS.toFloat(), "${home.rows}", { v ->
        env.update { it.copy(home = HomeGrid.resizeGrid(it.home, it.home.columns, v.toInt())) }
    }, steps = HomeLayout.MAX_ROWS - HomeLayout.MIN_ROWS - 1)
    Hint(stringResource(R.string.home_grid_hint))
    SettingChoice(
        stringResource(R.string.home_default_page),
        home.pages.indices.map { it to stringResource(R.string.page_number, it + 1) },
        home.defaultPage,
        { p -> env.update { it.copy(home = it.home.copy(defaultPage = p)) } },
    )
    SectionHeader(stringResource(R.string.home_elements))
    SettingSwitch(stringResource(R.string.item_clock), hasClock, { on -> env.update { toggleElement(it, on, clock = true) } })
    SettingSwitch(stringResource(R.string.item_search_bar), hasSearch, { on -> env.update { toggleElement(it, on, clock = false) } })
    SectionHeader(stringResource(R.string.clock_section))
    val c = s.clock
    SettingSwitch(stringResource(R.string.clock_show_time), c.showTime, { v -> env.update { it.copy(clock = it.clock.copy(showTime = v)) } })
    SettingSwitch(stringResource(R.string.clock_show_weekday), c.showWeekday, { v -> env.update { it.copy(clock = it.clock.copy(showWeekday = v)) } })
    SettingSwitch(stringResource(R.string.clock_show_date), c.showDate, { v -> env.update { it.copy(clock = it.clock.copy(showDate = v)) } })
    SettingSwitch(stringResource(R.string.clock_show_card), c.showCard, { v -> env.update { it.copy(clock = it.clock.copy(showCard = v)) } })
    SettingChoice(
        stringResource(R.string.clock_format),
        listOf(0 to stringResource(R.string.clock_format_system), 24 to stringResource(R.string.clock_format_24), 12 to stringResource(R.string.clock_format_12)),
        when (c.use24h) { null -> 0; true -> 24; false -> 12 },
        { f -> env.update { it.copy(clock = it.clock.copy(use24h = when (f) { 24 -> true; 12 -> false; else -> null })) } },
    )
    Hint(stringResource(R.string.home_edit_hint))
}

private fun toggleElement(s: LauncherState, on: Boolean, clock: Boolean): LauncherState {
    val existing = s.home.pages.flatMap { it.items }.filter { if (clock) it is ClockItem else it is SearchBarItem }
    if (!on) return s.copy(home = existing.fold(s.home) { h, i -> HomeGrid.remove(h, i.id) })
    if (existing.isNotEmpty()) return s
    val item: HomeItem = if (clock) ClockItem(Ids.item(), Cell(0, 0, s.home.columns, 2)) else SearchBarItem(Ids.item(), Cell(0, 0, s.home.columns, 1))
    return when (val r = HomeGrid.add(s.home, s.home.defaultPage, item)) {
        is GridResult.Ok -> s.copy(home = r.layout)
        is GridResult.Rejected -> s
    }
}

// ---- Ícones ---------------------------------------------------------------------------------------

@Composable
fun IconSettingsScreen(env: SettingsEnv) {
    val context = LocalContext.current
    val i = env.state.icons
    val catalog by env.container.catalog.snapshot.collectAsState()
    val sample = remember(catalog) { AppOrdering.alphabetical(catalog.entries).take(4) }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium)).background(Color(0xFF151A2E)).padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        sample.forEach { AppIcon(it.key, i.sizeDp.dp, i) }
    }
    SettingSlider(stringResource(R.string.icons_size), i.sizeDp.toFloat(), 36f..72f, "${i.sizeDp} dp", { v -> env.update { it.copy(icons = it.icons.copy(sizeDp = v.toInt())) } })
    SettingSlider(stringResource(R.string.icons_spacing), i.spacingDp.toFloat(), 0f..20f, "${i.spacingDp} dp", { v -> env.update { it.copy(icons = it.icons.copy(spacingDp = v.toInt())) } })
    SettingChoice(
        stringResource(R.string.icons_shape),
        listOf(IconShape.SYSTEM to stringResource(R.string.shape_system), IconShape.CIRCLE to stringResource(R.string.shape_circle), IconShape.ROUNDED_SQUARE to stringResource(R.string.shape_rounded_square), IconShape.SQUIRCLE to stringResource(R.string.shape_squircle)),
        i.shape, { v -> env.update { it.copy(icons = it.icons.copy(shape = v)) } },
        description = stringResource(R.string.icons_shape_hint),
    )
    SettingChoice(
        stringResource(R.string.icons_style),
        listOf(IconStyle.ORIGINAL to stringResource(R.string.style_original), IconStyle.MONOCHROME to stringResource(R.string.style_monochrome), IconStyle.GOLD to stringResource(R.string.style_gold)),
        i.style, { v -> env.update { it.copy(icons = it.icons.copy(style = v)) } },
        description = stringResource(R.string.icons_style_hint),
    )
    val packs by produceState(emptyList<pt.hexa.empresarial.apps.IconPackInfo>()) { value = env.container.iconPacks.installed() }
    SettingChoice(
        stringResource(R.string.icons_pack),
        listOf<Pair<String?, String>>(null to stringResource(R.string.icons_pack_none)) + packs.map { it.packageName to it.label },
        i.iconPack,
        { v -> env.container.icons.clear(); env.update { it.copy(icons = it.icons.copy(iconPack = v)) } },
        description = if (packs.isEmpty()) stringResource(R.string.icons_pack_empty) else stringResource(R.string.icons_pack_hint),
    )
    SectionHeader(stringResource(R.string.icons_labels))
    SettingSwitch(stringResource(R.string.icons_show_labels), i.showLabels, { v -> env.update { it.copy(icons = it.icons.copy(showLabels = v)) } })
    SettingSlider(stringResource(R.string.icons_label_size), i.labelSizeSp.toFloat(), 9f..16f, "${i.labelSizeSp} sp", { v -> env.update { it.copy(icons = it.icons.copy(labelSizeSp = v.toInt())) } })
    SettingChoice(stringResource(R.string.icons_label_lines), listOf(1 to "1", 2 to "2"), i.labelLines, { v -> env.update { it.copy(icons = it.icons.copy(labelLines = v)) } })
    SettingChoice(
        stringResource(R.string.icons_alignment),
        listOf(LabelAlignment.CENTER to stringResource(R.string.align_center), LabelAlignment.START to stringResource(R.string.align_start)),
        i.alignment, { v -> env.update { it.copy(icons = it.icons.copy(alignment = v)) } },
    )
    Hint(context.getString(R.string.icons_rows_cols_hint))
}

// ---- Dock -----------------------------------------------------------------------------------------

@Composable
fun DockSettingsScreen(env: SettingsEnv) {
    val d = env.state.dock
    val catalog by env.container.catalog.snapshot.collectAsState()
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium)).background(Color(0xFF151A2E)).padding(vertical = 12.dp)) {
        Dock(d, env.state.icons, catalog, editMode = false, draggingSlot = null, actions = DockActions({ _, _, _ -> }, { _, _, _ -> }, { _, _, _, _ -> }, {}, {}, {}, {}))
    }
    SettingSwitch(stringResource(R.string.dock_enabled), d.enabled, { v -> env.update { it.copy(dock = it.dock.copy(enabled = v)) } })
    SettingSlider(stringResource(R.string.dock_positions), d.slots.size.toFloat(), DockSettings.MIN_SLOTS.toFloat()..DockSettings.MAX_SLOTS.toFloat(), "${d.slots.size}", { v ->
        env.update { s ->
            val n = v.toInt()
            val slots = if (n <= s.dock.slots.size) s.dock.slots.take(n) else s.dock.slots + List(n - s.dock.slots.size) { null }
            s.copy(dock = s.dock.copy(slots = slots))
        }
    }, steps = DockSettings.MAX_SLOTS - DockSettings.MIN_SLOTS - 1)
    SettingSlider(stringResource(R.string.dock_icon_size), d.iconSizeDp.toFloat(), 36f..72f, "${d.iconSizeDp} dp", { v -> env.update { it.copy(dock = it.dock.copy(iconSizeDp = v.toInt())) } })
    SettingSlider(stringResource(R.string.dock_spacing), d.spacingDp.toFloat(), 0f..32f, "${d.spacingDp} dp", { v -> env.update { it.copy(dock = it.dock.copy(spacingDp = v.toInt())) } })
    SettingSlider(stringResource(R.string.dock_margin), d.marginDp.toFloat(), 0f..48f, "${d.marginDp} dp", { v -> env.update { it.copy(dock = it.dock.copy(marginDp = v.toInt())) } })
    SettingSwitch(stringResource(R.string.dock_background), d.showBackground, { v -> env.update { it.copy(dock = it.dock.copy(showBackground = v)) } })
    if (d.showBackground) SettingSlider(stringResource(R.string.dock_background_opacity), d.backgroundOpacity, 0f..1f, pct(d.backgroundOpacity), { v -> env.update { it.copy(dock = it.dock.copy(backgroundOpacity = v)) } })
    SettingChoice(
        stringResource(R.string.dock_style),
        listOf(DockStyle.GOLD_MEDALLION to stringResource(R.string.dock_style_gold), DockStyle.SILVER_MEDALLION to stringResource(R.string.dock_style_silver), DockStyle.PLAIN to stringResource(R.string.dock_style_plain)),
        d.style, { v -> env.update { it.copy(dock = it.dock.copy(style = v)) } },
    )
    SettingSwitch(stringResource(R.string.dock_labels), d.showLabels, { v -> env.update { it.copy(dock = it.dock.copy(showLabels = v)) } })
    SettingSwitch(stringResource(R.string.dock_all_apps), d.showAllAppsButton, { v -> env.update { it.copy(dock = it.dock.copy(showAllAppsButton = v)) } }, stringResource(R.string.dock_all_apps_hint))
    Hint(stringResource(R.string.dock_hint))
}

// ---- Gaveta ---------------------------------------------------------------------------------------

@Composable
fun DrawerSettingsScreen(env: SettingsEnv) {
    val dr = env.state.drawer
    val catalog by env.container.catalog.snapshot.collectAsState()
    SettingChoice(
        stringResource(R.string.drawer_default_view),
        listOf(DrawerView.ORBITAL to stringResource(R.string.drawer_view_orbital), DrawerView.GRID to stringResource(R.string.drawer_view_grid)),
        dr.view, { v -> env.update { it.copy(drawer = it.drawer.copy(view = v)) } },
        description = stringResource(R.string.drawer_view_hint),
    )
    SettingChoice(
        stringResource(R.string.drawer_sort),
        listOf(AppSort.ALPHABETICAL to stringResource(R.string.sort_alpha), AppSort.MOST_USED to stringResource(R.string.sort_usage), AppSort.MANUAL to stringResource(R.string.sort_manual)),
        dr.sort, { v -> env.update { it.copy(drawer = it.drawer.copy(sort = v)) } },
    )
    SettingSlider(stringResource(R.string.drawer_grid_columns), dr.gridColumns.toFloat(), 3f..7f, "${dr.gridColumns}", { v -> env.update { it.copy(drawer = it.drawer.copy(gridColumns = v.toInt())) } }, steps = 3)
    SettingSwitch(stringResource(R.string.drawer_grid_labels), dr.gridShowLabels, { v -> env.update { it.copy(drawer = it.drawer.copy(gridShowLabels = v)) } })
    SettingSwitch(stringResource(R.string.drawer_auto_keyboard), dr.autoKeyboard, { v -> env.update { it.copy(drawer = it.drawer.copy(autoKeyboard = v)) } })
    SettingSlider(stringResource(R.string.drawer_opacity), dr.backgroundOpacity, 0.5f..1f, pct(dr.backgroundOpacity), { v -> env.update { it.copy(drawer = it.drawer.copy(backgroundOpacity = v)) } })

    SectionHeader(stringResource(R.string.drawer_hidden_apps))
    val hidden = env.state.apps.hidden
    if (hidden.isEmpty()) Hint(stringResource(R.string.drawer_hidden_none))
    hidden.forEach { key ->
        AppRow(key, catalog.label(key) ?: key, env) {
            TextButton(onClick = { env.update { it.copy(apps = it.apps.copy(hidden = it.apps.hidden - key)) } }) { Text(stringResource(R.string.action_show)) }
        }
    }
    if (dr.sort == AppSort.MANUAL) {
        SectionHeader(stringResource(R.string.drawer_manual_order))
        Hint(stringResource(R.string.drawer_manual_order_hint))
        val ordered = AppOrdering.order(catalog.entries, env.state.apps, AppSort.MANUAL)
        val keys = ordered.map { it.key }
        val up = stringResource(R.string.action_move_up)
        val down = stringResource(R.string.action_move_down)
        ordered.forEach { app ->
            AppRow(app.key, app.label, env) {
                TextButton(onClick = { env.update { it.copy(apps = AppOrdering.moveInManualOrder(it.apps, keys, app.key, -1)) } }, modifier = Modifier.semantics { contentDescription = up }) { Text("↑") }
                TextButton(onClick = { env.update { it.copy(apps = AppOrdering.moveInManualOrder(it.apps, keys, app.key, 1)) } }, modifier = Modifier.semantics { contentDescription = down }) { Text("↓") }
            }
        }
    }
}

@Composable
private fun AppRow(key: String, label: String, env: SettingsEnv, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(key, 32.dp, env.state.icons)
        Spacer(Modifier.width(12.dp))
        Text(label, color = HexaTheme.colors.onSurface, modifier = Modifier.weight(1f))
        actions()
    }
}

// ---- Interface orbital ----------------------------------------------------------------------------

@Composable
fun OrbitalSettingsScreen(env: SettingsEnv) {
    val o = env.state.orbital
    fun set(t: (OrbitalSettings) -> OrbitalSettings) = env.update { it.copy(orbital = t(it.orbital)) }
    OrbitalPreview(o)
    SectionHeader(stringResource(R.string.orbital_geometry))
    SettingSlider(stringResource(R.string.orbital_count), o.maxOrbits.toFloat(), 1f..7f, "${o.maxOrbits}", { v -> set { it.copy(maxOrbits = v.toInt()) } }, steps = 5)
    SettingSlider(stringResource(R.string.orbital_spacing), o.orbitSpacingDp.toFloat(), 36f..140f, "${o.orbitSpacingDp} dp", { v -> set { it.copy(orbitSpacingDp = v.toInt()) } }, description = stringResource(R.string.orbital_spacing_hint))
    SettingSlider(stringResource(R.string.orbital_max_radius), o.maxRadiusFraction, 0.5f..1f, pct(o.maxRadiusFraction), { v -> set { it.copy(maxRadiusFraction = v) } })
    SettingSlider(stringResource(R.string.orbital_icon_size), o.iconSizeDp.toFloat(), 32f..72f, "${o.iconSizeDp} dp", { v -> set { it.copy(iconSizeDp = v.toInt()) } })
    SettingSlider(stringResource(R.string.orbital_center_size), o.centerSizeDp.toFloat(), 36f..140f, "${o.centerSizeDp} dp", { v -> set { it.copy(centerSizeDp = v.toInt()) } })
    SettingSlider(stringResource(R.string.orbital_min_gap), o.minGapDp.toFloat(), 2f..40f, "${o.minGapDp} dp", { v -> set { it.copy(minGapDp = v.toInt()) } })

    SectionHeader(stringResource(R.string.orbital_appearance))
    SettingSwitch(stringResource(R.string.orbital_show_lines), o.showLines, { v -> set { it.copy(showLines = v) } })
    ColorSwatches(
        stringResource(R.string.orbital_line_color),
        listOf(0xFFB8BCC2 to R.string.color_silver, 0xFFC9A227 to R.string.color_gold, 0xFFFFFFFF to R.string.color_white, 0xFF8A6E6E to R.string.color_rose_grey, 0xFF5B5E66 to R.string.color_graphite),
        o.lineColor, { v -> set { it.copy(lineColor = v) } },
    )
    SettingSlider(stringResource(R.string.orbital_opacity), o.orbitOpacity, 0f..1f, pct(o.orbitOpacity), { v -> set { it.copy(orbitOpacity = v) } })
    SettingSlider(stringResource(R.string.orbital_decorative), o.decorativeRings.toFloat(), 0f..3f, "${o.decorativeRings}", { v -> set { it.copy(decorativeRings = v.toInt()) } }, steps = 2)
    SettingChoice(
        stringResource(R.string.orbital_background),
        listOf(
            OrbitalBackground.THEME to stringResource(R.string.bg_theme),
            OrbitalBackground.GRAPHITE to stringResource(R.string.bg_graphite),
            OrbitalBackground.DEEP_BLACK to stringResource(R.string.bg_black),
            OrbitalBackground.GRADIENT to stringResource(R.string.bg_gradient),
            OrbitalBackground.WALLPAPER_DIMMED to stringResource(R.string.bg_wallpaper),
        ),
        o.background, { v -> set { it.copy(background = v) } },
    )
    SettingChoice(
        stringResource(R.string.orbital_icon_backdrop),
        listOf(IconBackdrop.DARK_DISC to stringResource(R.string.backdrop_dark), IconBackdrop.LIGHT_DISC to stringResource(R.string.backdrop_light), IconBackdrop.GOLD_DISC to stringResource(R.string.backdrop_gold), IconBackdrop.NONE to stringResource(R.string.backdrop_none)),
        o.iconBackdrop, { v -> set { it.copy(iconBackdrop = v) } },
    )
    SettingSwitch(stringResource(R.string.orbital_shadows), o.shadows, { v -> set { it.copy(shadows = v) } })
    SettingChoice(
        stringResource(R.string.orbital_opening),
        listOf(OrbitalOpening.RADIAL to stringResource(R.string.opening_radial), OrbitalOpening.PERSPECTIVE to stringResource(R.string.opening_perspective), OrbitalOpening.FADE to stringResource(R.string.opening_fade)),
        o.opening, { v -> set { it.copy(opening = v) } },
        description = stringResource(R.string.orbital_opening_hint),
    )

    SectionHeader(stringResource(R.string.orbital_organization))
    SettingChoice(
        stringResource(R.string.orbital_distribution),
        listOf(OrbitalDistribution.AUTOMATIC to stringResource(R.string.distribution_auto), OrbitalDistribution.MANUAL to stringResource(R.string.distribution_manual)),
        o.distribution, { v -> set { it.copy(distribution = v) } },
        description = stringResource(R.string.orbital_distribution_hint),
    )
    var pickFav by remember { mutableStateOf(false) }
    var pickPriority by remember { mutableStateOf(false) }
    val catalog by env.container.catalog.snapshot.collectAsState()
    SettingLink(stringResource(R.string.orbital_favorites), LocalContext.current.resources.getQuantityString(R.plurals.app_count, env.state.apps.favorites.size, env.state.apps.favorites.size), { pickFav = true }, stringResource(R.string.orbital_favorites_hint))
    SettingLink(stringResource(R.string.orbital_priority), LocalContext.current.resources.getQuantityString(R.plurals.app_count, env.state.apps.priority.size, env.state.apps.priority.size), { pickPriority = true }, stringResource(R.string.orbital_priority_hint))
    if (pickFav) {
        AppPickerDialog(stringResource(R.string.orbital_favorites), env.state.icons, { pickFav = false }, { apps -> env.update { it.copy(apps = it.apps.copy(favorites = apps.map { a -> a.key })) }; pickFav = false }, multiple = true, initiallySelected = env.state.apps.favorites)
    }
    if (pickPriority) {
        AppPickerDialog(stringResource(R.string.orbital_priority), env.state.icons, { pickPriority = false }, { apps -> env.update { it.copy(apps = it.apps.copy(priority = apps.map { a -> a.key })) }; pickPriority = false }, multiple = true, initiallySelected = env.state.apps.priority)
    }
    if (catalog.loaded && env.state.apps.hidden.isNotEmpty()) Hint(stringResource(R.string.orbital_hidden_hint))

    SectionHeader(stringResource(R.string.orbital_center))
    CenterElementEditor(o.center, env.state.icons) { c -> set { it.copy(center = c) } }
}

/** Live miniature of the orbital composition with the current geometry and colours. */
@Composable
private fun OrbitalPreview(o: OrbitalSettings) {
    val colors = HexaTheme.colors
    val description = stringResource(R.string.orbital_preview)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium))
            .background(Color(0xFF1A1B1F))
            .semantics { contentDescription = description },
    ) {
        val scale = 0.55f
        val l = OrbitalGeometry.layout(
            14,
            OrbitalParams(
                size.width, size.height, o.iconSizeDp * density * scale, o.centerSizeDp * density * scale, o.minGapDp * density * scale,
                o.orbitSpacingDp * density * scale, o.maxOrbits, o.maxRadiusFraction, 4f * density,
            ),
        )
        val c = Offset(l.centerX, l.centerY)
        if (o.showLines) l.ringRadii.forEach { r -> drawCircle(Color(o.lineColor).copy(alpha = o.orbitOpacity), r, c, style = Stroke(1f * density)) }
        drawCircle(colors.accent.copy(alpha = 0.6f), l.centerSize / 2f, c, style = Stroke(1.5f * density))
        l.pages.firstOrNull()?.orbits?.forEach { orbit -> orbit.slots.forEach { s -> drawCircle(Color(0xFF3A3B42), l.iconSize / 2f, Offset(s.x, s.y)) } }
    }
}

// ---- Grupos ---------------------------------------------------------------------------------------

@Composable
fun GroupSettingsScreen(env: SettingsEnv) {
    var editing by remember { mutableStateOf<String?>(null) }
    val groups = env.state.groups
    Hint(stringResource(R.string.groups_intro))
    OutlinedButton(onClick = { editing = "" }, modifier = Modifier.padding(vertical = 8.dp)) { Text(stringResource(R.string.action_new_group)) }
    if (groups.isEmpty()) Hint(stringResource(R.string.groups_none))
    groups.forEach { g ->
        SettingLink(
            g.name,
            LocalContext.current.resources.getQuantityString(R.plurals.app_count, g.appKeys.size, g.appKeys.size),
            { editing = g.id },
            shapeLabel(g.shape),
        )
    }
    editing?.let { id ->
        GroupEditorDialog(
            existing = groups.firstOrNull { it.id == id },
            icons = env.state.icons,
            onSave = { g, isNew ->
                env.update { s ->
                    val list = if (s.groups.any { it.id == g.id }) s.groups.map { if (it.id == g.id) g else it } else s.groups + g
                    val home = if (!isNew) s.home else when (val r = HomeGrid.add(s.home, s.home.defaultPage, GroupItem(Ids.item(), Cell(0, 0), g.id))) {
                        is GridResult.Ok -> r.layout
                        is GridResult.Rejected -> s.home
                    }
                    s.copy(groups = list, home = home)
                }
                editing = null
            },
            onDelete = { gid ->
                env.update { s ->
                    s.copy(
                        groups = s.groups.filter { it.id != gid },
                        home = s.home.copy(pages = s.home.pages.map { p -> p.copy(items = p.items.filter { it !is GroupItem || it.groupId != gid }) }),
                    )
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ---- Widgets --------------------------------------------------------------------------------------

@Composable
fun WidgetSettingsScreen(env: SettingsEnv) {
    Hint(stringResource(R.string.widgets_intro))
    val placed = env.state.home.pages.flatMapIndexed { pi, p -> p.items.filterIsInstance<WidgetItem>().map { pi to it } }
    if (placed.isEmpty()) Hint(stringResource(R.string.widgets_none_placed))
    placed.forEach { (page, w) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.label.ifEmpty { stringResource(R.string.widget_generic) }, color = HexaTheme.colors.onSurface)
                Text(
                    stringResource(if (w.needsRestore) R.string.widget_info_restore else R.string.widget_info, page + 1, w.cell.w, w.cell.h),
                    style = MaterialTheme.typography.bodySmall, color = HexaTheme.colors.onSurfaceMuted,
                )
            }
            TextButton(onClick = {
                if (!w.needsRestore) env.container.widgets.delete(w.appWidgetId)
                env.update { it.copy(home = HomeGrid.remove(it.home, w.id)) }
            }) { Text(stringResource(R.string.action_remove), color = HexaTheme.colors.danger) }
        }
    }
    Hint(stringResource(R.string.widgets_limits))
}

// ---- Gestos ---------------------------------------------------------------------------------------

@Composable
fun gestureLabel(b: GestureBinding, appLabel: String?): String = when (b.action) {
    GestureAction.NONE -> stringResource(R.string.gesture_none)
    GestureAction.OPEN_DRAWER -> stringResource(R.string.gesture_open_drawer)
    GestureAction.OPEN_ORBITAL -> stringResource(R.string.gesture_open_orbital)
    GestureAction.OPEN_GRID -> stringResource(R.string.gesture_open_grid)
    GestureAction.OPEN_SEARCH -> stringResource(R.string.gesture_open_search)
    GestureAction.OPEN_SETTINGS -> stringResource(R.string.gesture_open_settings)
    GestureAction.EDIT_HOME -> stringResource(R.string.gesture_edit_home)
    GestureAction.OPEN_APP -> stringResource(R.string.gesture_open_app_named, appLabel ?: "—")
    GestureAction.DEFAULT_PAGE -> stringResource(R.string.gesture_default_page)
    GestureAction.NOTIFICATIONS -> stringResource(R.string.gesture_notifications)
    GestureAction.QUICK_SETTINGS -> stringResource(R.string.gesture_quick_settings)
    GestureAction.LOCK_SCREEN -> stringResource(R.string.gesture_lock_screen)
    GestureAction.RECENT_APPS -> stringResource(R.string.gesture_recent_apps)
}

private enum class GestureSlot { UP, DOWN, DOUBLE_TAP, HOME }

@Composable
fun GestureSettingsScreen(env: SettingsEnv) {
    val g = env.state.gestures
    val catalog by env.container.catalog.snapshot.collectAsState()
    var choosing by remember { mutableStateOf<GestureSlot?>(null) }
    var pickAppFor by remember { mutableStateOf<GestureSlot?>(null) }
    var serviceOn by remember { mutableStateOf(SystemActionsService.isEnabled(env.activity)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceOn = SystemActionsService.isEnabled(env.activity) }
    fun binding(slot: GestureSlot) = when (slot) {
        GestureSlot.UP -> g.swipeUp
        GestureSlot.DOWN -> g.swipeDown
        GestureSlot.DOUBLE_TAP -> g.doubleTap
        GestureSlot.HOME -> g.homeButton
    }
    fun set(slot: GestureSlot, b: GestureBinding) = env.update { s ->
        s.copy(
            gestures = when (slot) {
                GestureSlot.UP -> s.gestures.copy(swipeUp = b)
                GestureSlot.DOWN -> s.gestures.copy(swipeDown = b)
                GestureSlot.DOUBLE_TAP -> s.gestures.copy(doubleTap = b)
                GestureSlot.HOME -> s.gestures.copy(homeButton = b)
            },
        )
    }
    val titles = mapOf(
        GestureSlot.UP to R.string.gesture_swipe_up,
        GestureSlot.DOWN to R.string.gesture_swipe_down,
        GestureSlot.DOUBLE_TAP to R.string.gesture_double_tap,
        GestureSlot.HOME to R.string.gesture_home_button,
    )
    Hint(stringResource(R.string.gestures_intro))
    GestureSlot.entries.forEach { slot ->
        val b = binding(slot)
        SettingLink(stringResource(titles.getValue(slot)), gestureLabel(b, b.appKey?.let { catalog.label(it) }), { choosing = slot })
    }
    SettingLink(stringResource(R.string.gesture_long_press), stringResource(R.string.gesture_edit_home), {}, stringResource(R.string.gesture_long_press_hint))

    SectionHeader(stringResource(R.string.accessibility_section))
    Hint(stringResource(R.string.accessibility_needed_text))
    SettingLink(
        stringResource(R.string.accessibility_status),
        stringResource(if (serviceOn) R.string.status_on else R.string.status_off),
        { env.activity.startActivity(SystemActionsService.settingsIntent()) },
        stringResource(R.string.accessibility_open_hint),
    )

    choosing?.let { slot ->
        val actions = GestureAction.entries
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(titles.getValue(slot))) },
            text = {
                Column(Modifier.padding(top = 4.dp)) {
                    actions.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                choosing = null
                                if (a == GestureAction.OPEN_APP) pickAppFor = slot else set(slot, GestureBinding(a))
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = binding(slot).action == a, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = HexaTheme.colors.accent))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(if (a == GestureAction.OPEN_APP) stringResource(R.string.gesture_open_app) else gestureLabel(GestureBinding(a), null))
                                if (a.needsAccessibility) Text(stringResource(R.string.gesture_needs_accessibility), style = MaterialTheme.typography.bodySmall, color = HexaTheme.colors.onSurfaceMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    pickAppFor?.let { slot ->
        AppPickerDialog(stringResource(R.string.gesture_open_app), env.state.icons, { pickAppFor = null }, { apps ->
            apps.firstOrNull()?.let { set(slot, GestureBinding(GestureAction.OPEN_APP, it.key)) }
            pickAppFor = null
        })
    }
}

// ---- Aparência ------------------------------------------------------------------------------------

@Composable
fun AppearanceSettingsScreen(env: SettingsEnv) {
    val a = env.state.appearance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun set(t: (pt.hexa.empresarial.domain.model.AppearanceSettings) -> pt.hexa.empresarial.domain.model.AppearanceSettings) = env.update { it.copy(appearance = t(it.appearance)) }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = env.container.images.import(uri, 2048)
            if (name != null) set { it.copy(wallpaper = WallpaperMode.IMAGE, wallpaperImage = name) }
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium)).background(HexaTheme.colors.surface).padding(20.dp), contentAlignment = Alignment.Center) {
        HexaLogo()
    }
    SettingChoice(
        stringResource(R.string.appearance_theme),
        listOf(ThemeMode.SYSTEM to stringResource(R.string.theme_system), ThemeMode.LIGHT to stringResource(R.string.theme_light), ThemeMode.DARK to stringResource(R.string.theme_dark)),
        a.theme, { v -> set { it.copy(theme = v) } },
    )
    ColorSwatches(stringResource(R.string.appearance_accent), ACCENTS, a.accent) { v -> set { it.copy(accent = v) } }
    SettingChoice(
        stringResource(R.string.appearance_contrast),
        listOf(ContrastLevel.STANDARD to stringResource(R.string.contrast_standard), ContrastLevel.MEDIUM to stringResource(R.string.contrast_medium), ContrastLevel.HIGH to stringResource(R.string.contrast_high)),
        a.contrast, { v -> set { it.copy(contrast = v) } },
    )
    SettingSlider(stringResource(R.string.appearance_surface_opacity), a.surfaceOpacity, 0f..1f, pct(a.surfaceOpacity), { v -> set { it.copy(surfaceOpacity = v) } }, description = stringResource(R.string.appearance_surface_hint))
    SettingSlider(stringResource(R.string.appearance_corners), a.cornerScale, 0f..1.5f, pct(a.cornerScale), { v -> set { it.copy(cornerScale = v) } })
    SettingChoice(
        stringResource(R.string.appearance_logo),
        listOf(LogoPlacement.DRAWER_HEADER to stringResource(R.string.logo_drawer), LogoPlacement.HOME_TOP to stringResource(R.string.logo_home), LogoPlacement.HIDDEN to stringResource(R.string.logo_hidden)),
        a.logo, { v -> set { it.copy(logo = v) } },
    )
    SectionHeader(stringResource(R.string.appearance_wallpaper))
    SettingChoice(
        stringResource(R.string.appearance_wallpaper_mode),
        listOf(
            WallpaperMode.SYSTEM to stringResource(R.string.wallpaper_system),
            WallpaperMode.EXECUTIVE_GRADIENT to stringResource(R.string.wallpaper_gradient),
            WallpaperMode.SOLID to stringResource(R.string.wallpaper_solid),
            WallpaperMode.IMAGE to stringResource(R.string.wallpaper_image),
        ),
        a.wallpaper,
        { v -> if (v == WallpaperMode.IMAGE && a.wallpaperImage == null) pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else set { it.copy(wallpaper = v) } },
    )
    if (a.wallpaper == WallpaperMode.IMAGE) {
        SettingLink(stringResource(R.string.wallpaper_change_image), null, { pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    }
    if (a.wallpaper == WallpaperMode.SOLID) {
        ColorSwatches(
            stringResource(R.string.wallpaper_color),
            listOf(0xFF14161F to R.string.color_graphite, 0xFF0B0C10 to R.string.color_black, 0xFF151A2E to R.string.color_navy, 0xFF2B2A28 to R.string.color_warm_grey, 0xFFF4F2EC to R.string.color_soft_white),
            a.wallpaperColor, { v -> set { it.copy(wallpaperColor = v) } },
        )
    }
    SettingSlider(stringResource(R.string.appearance_scrim), a.scrim, 0f..0.7f, pct(a.scrim), { v -> set { it.copy(scrim = v) } }, description = stringResource(R.string.appearance_scrim_hint))
    Hint(context.getString(R.string.appearance_system_wallpaper_hint))
}

// ---- Animações ------------------------------------------------------------------------------------

@Composable
fun AnimationSettingsScreen(env: SettingsEnv) {
    val an = env.state.animations
    SettingSwitch(stringResource(R.string.animations_enabled), an.enabled, { v -> env.update { it.copy(animations = it.animations.copy(enabled = v)) } }, stringResource(R.string.animations_enabled_hint))
    SettingSlider(stringResource(R.string.animations_duration), an.speed, 0.5f..1.5f, times(an.speed), { v -> env.update { it.copy(animations = it.animations.copy(speed = v)) } }, description = stringResource(R.string.animations_duration_hint))
    Hint(stringResource(R.string.animations_groups_hint))
}

// ---- Avançadas ------------------------------------------------------------------------------------

@Composable
fun AdvancedSettingsScreen(env: SettingsEnv) {
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    SettingLink(stringResource(R.string.advanced_language), stringResource(R.string.advanced_language_value), {}, stringResource(R.string.advanced_language_hint))
    SettingLink(stringResource(R.string.advanced_clear_icons), null, { env.container.icons.clear(); android.widget.Toast.makeText(context, R.string.msg_done, android.widget.Toast.LENGTH_SHORT).show() }, stringResource(R.string.advanced_clear_icons_hint))
    SettingLink(stringResource(R.string.advanced_reset_usage), null, { env.container.usage.clear(); android.widget.Toast.makeText(context, R.string.msg_done, android.widget.Toast.LENGTH_SHORT).show() }, stringResource(R.string.advanced_reset_usage_hint))
    SettingLink(stringResource(R.string.advanced_reload_apps), null, { env.container.catalog.reloadAll(); android.widget.Toast.makeText(context, R.string.msg_done, android.widget.Toast.LENGTH_SHORT).show() }, stringResource(R.string.advanced_reload_apps_hint))
    if (env.container.state.recovered) Hint(stringResource(R.string.msg_state_recovered))
    SectionHeader(stringResource(R.string.advanced_reset_section))
    OutlinedButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.advanced_reset), color = HexaTheme.colors.danger) }
    Hint(stringResource(R.string.advanced_reset_hint))
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.advanced_reset_confirm_title)) },
            text = { Text(stringResource(R.string.advanced_reset_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    env.state.home.pages.flatMap { it.items }.filterIsInstance<WidgetItem>().filter { !it.needsRestore }.forEach { env.container.widgets.delete(it.appWidgetId) }
                    val fresh = pt.hexa.empresarial.apps.DefaultApps.firstRunState(context, LauncherState(), env.container.catalog.snapshot.value)
                    env.container.state.set(fresh)
                }) { Text(stringResource(R.string.action_reset), color = HexaTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
