package pt.hexa.empresarial.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.hexa.empresarial.R
import pt.hexa.empresarial.apps.AppInfo
import pt.hexa.empresarial.apps.CatalogSnapshot
import pt.hexa.empresarial.domain.apps.AppEntry
import pt.hexa.empresarial.domain.apps.AppOrdering
import pt.hexa.empresarial.domain.model.AppSort
import pt.hexa.empresarial.domain.model.DrawerView
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.LogoPlacement
import pt.hexa.empresarial.domain.model.OrbitalBackground
import pt.hexa.empresarial.domain.model.OrbitalDistribution
import pt.hexa.empresarial.home.HomeViewModel
import pt.hexa.empresarial.home.MenuSource
import pt.hexa.empresarial.home.windowRect
import pt.hexa.empresarial.theme.Brand
import pt.hexa.empresarial.theme.HexaLogo
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphButton
import pt.hexa.empresarial.ui.GlyphIcon

/** Foreground colour of the drawer content (white on dark backgrounds, ink on the light one). */
val LocalDrawerFg = staticCompositionLocalOf { Color.White }

fun drawerIsLight(bg: OrbitalBackground, darkTheme: Boolean) = bg == OrbitalBackground.THEME && !darkTheme

/** Background of both drawer views. */
fun drawerBrush(bg: OrbitalBackground, opacity: Float, darkTheme: Boolean): Brush = when (bg) {
    OrbitalBackground.THEME -> SolidColor((if (darkTheme) Color(0xFF1A1B1F) else Brand.SoftWhite).copy(alpha = opacity))
    OrbitalBackground.DEEP_BLACK -> SolidColor(Brand.DeepBlack.copy(alpha = opacity))
    OrbitalBackground.GRAPHITE -> SolidColor(Color(0xFF1A1B1F).copy(alpha = opacity))
    OrbitalBackground.GRADIENT -> Brush.verticalGradient(listOf(Color(0xFF1B2036).copy(alpha = opacity), Brand.DeepBlack.copy(alpha = opacity)))
    OrbitalBackground.WALLPAPER_DIMMED -> SolidColor(Color.Black.copy(alpha = opacity * 0.72f))
}

/**
 * The app drawer, opened by swiping up. [progress] (0..1) follows the finger, so the orbital
 * composition assembles itself while the drawer appears. Two views share it: orbital (round
 * button) and grid (square button). While searching, results are a plain list for legibility.
 */
@Composable
fun DrawerOverlay(
    vm: HomeViewModel,
    state: LauncherState,
    catalog: CatalogSnapshot,
    progress: Float,
    focusSearch: Boolean,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
) {
    val usage by vm.usage.collectAsState()
    val view = state.drawer.view
    val sort = if (view == DrawerView.ORBITAL && state.orbital.distribution == OrbitalDistribution.MANUAL) AppSort.MANUAL else state.drawer.sort
    val ordered = remember(catalog, state.apps, sort, usage) { AppOrdering.order(catalog.entries, state.apps, sort, usage.counts) }
    val density = LocalDensity.current
    val dark = HexaTheme.colors.isDark
    val fg = if (drawerIsLight(state.orbital.background, dark)) Color(0xFF15161B) else Color.White

    CompositionLocalProvider(LocalDrawerFg provides fg) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
            .background(drawerBrush(state.orbital.background, state.drawer.backgroundOpacity, dark)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .graphicsLayer { translationY = (1f - progress) * with(density) { 48.dp.toPx() } },
        ) {
            Header(vm, state, focusSearch, onDragDelta, onDragEnd, onClose, catalog)
            val closeDrag = Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, dy ->
                    change.consume()
                    onDragDelta(dy)
                }
            }
            when {
                vm.query.isNotBlank() -> SearchResults(vm, state, catalog)
                view == DrawerView.ORBITAL -> OrbitalDrawer(vm, state, ordered, catalog, progress, Modifier.weight(1f).then(closeDrag))
                else -> GridDrawer(vm, state, ordered, catalog, onDragDelta, onDragEnd, Modifier.weight(1f))
            }
        }
    }
    }
}

@Composable
private fun Header(
    vm: HomeViewModel,
    state: LauncherState,
    focusSearch: Boolean,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    catalog: CatalogSnapshot,
) {
    val colors = HexaTheme.colors
    val fg = LocalDrawerFg.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) {
        if (focusSearch || state.drawer.autoKeyboard) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, dy ->
                    change.consume()
                    onDragDelta(dy)
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grab handle: swipe down here (or anywhere on the orbits) to close. Purely visual; the
        // close button below is the accessible way to close.
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(fg.copy(alpha = 0.35f)),
        )
        if (state.appearance.logo == LogoPlacement.DRAWER_HEADER) {
            Spacer(Modifier.height(10.dp))
            HexaLogo(size = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(HexaTheme.tokens.radiusPill)
            val hint = stringResource(R.string.search_apps_hint)
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(fg.copy(alpha = 0.07f))
                    .border(1.dp, colors.metallic(), shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.SEARCH, fg.copy(alpha = 0.85f), size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (vm.query.isEmpty()) Text(hint, color = fg.copy(alpha = 0.5f), fontSize = 15.sp, maxLines = 1)
                    BasicTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = fg, fontSize = 16.sp),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            catalog.search.search(vm.query, 1).firstOrNull()?.let { vm.launch(it.key, null) }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
                    )
                }
                if (vm.query.isNotEmpty()) {
                    GlyphButton(Glyph.CLOSE, stringResource(R.string.action_clear_search), vm::clearQuery, tint = fg)
                }
            }
            Spacer(Modifier.width(8.dp))
            GlyphButton(
                Glyph.ORBIT, stringResource(R.string.drawer_view_orbital), { vm.clearQuery(); vm.setDrawerView(DrawerView.ORBITAL) },
                selected = state.drawer.view == DrawerView.ORBITAL, tint = fg,
            )
            Spacer(Modifier.width(6.dp))
            // The square button opens the conventional grid (not the system's recent apps).
            GlyphButton(
                Glyph.GRID, stringResource(R.string.drawer_view_grid), { vm.clearQuery(); vm.setDrawerView(DrawerView.GRID) },
                square = true, selected = state.drawer.view == DrawerView.GRID, tint = fg,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            GlyphButton(Glyph.SETTINGS, stringResource(R.string.settings_title), { vm.host?.openSettings() }, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(6.dp))
            GlyphButton(Glyph.CLOSE, stringResource(R.string.action_close_drawer), onClose, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
        }
    }
}

/** Search results: a legible list, best match first; hidden and unavailable apps are marked. */
@Composable
private fun SearchResults(vm: HomeViewModel, state: LauncherState, catalog: CatalogSnapshot) {
    val fg = LocalDrawerFg.current
    val results = remember(catalog, vm.query) { catalog.search.search(vm.query, 60) }
    val hidden = state.apps.hidden.toSet()
    if (results.isEmpty()) {
        Text(
            stringResource(R.string.search_no_results, vm.query),
            color = fg.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(results, key = { it.key }) { app ->
            val info = catalog.byKey[app.key]
            ResultRow(vm, state, app, info, app.key in hidden)
        }
    }
}

@Composable
private fun ResultRow(vm: HomeViewModel, state: LauncherState, app: AppEntry, info: AppInfo?, hidden: Boolean) {
    val fg = LocalDrawerFg.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val enabled = info?.enabled != false
    val suffix = when {
        !enabled -> stringResource(R.string.app_unavailable_suffix)
        hidden -> stringResource(R.string.app_hidden_suffix)
        app.isWorkProfile -> stringResource(R.string.app_work_suffix)
        else -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HexaTheme.tokens.radiusMedium))
            .onGloballyPositioned { coords = it }
            .combinedClickable(
                onClick = { vm.launch(app.key, coords?.windowRect()) },
                onLongClick = { coords?.let { vm.showAppMenu(app.key, MenuSource.DRAWER, it.windowRect()) } },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.key, 40.dp, state.icons)
        Spacer(Modifier.width(14.dp))
        Text(app.label + suffix, color = fg.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
