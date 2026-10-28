package pt.hexa.empresarial.home

import android.content.Intent
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.hexa.empresarial.R
import pt.hexa.empresarial.apps.CatalogSnapshot
import pt.hexa.empresarial.domain.model.AppItem
import pt.hexa.empresarial.domain.model.ClockItem
import pt.hexa.empresarial.domain.model.DrawerView
import pt.hexa.empresarial.domain.model.GestureAction
import pt.hexa.empresarial.domain.model.GroupItem
import pt.hexa.empresarial.domain.model.HomeItem
import pt.hexa.empresarial.domain.model.HomePage
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.model.LogoPlacement
import pt.hexa.empresarial.domain.model.SearchBarItem
import pt.hexa.empresarial.domain.model.WidgetItem
import pt.hexa.empresarial.drawer.DrawerOverlay
import pt.hexa.empresarial.groups.GroupEditorDialog
import pt.hexa.empresarial.groups.GroupOverlay
import pt.hexa.empresarial.theme.HexaLogo
import pt.hexa.empresarial.theme.HexaTheme
import pt.hexa.empresarial.ui.AppIcon
import pt.hexa.empresarial.ui.AppPickerDialog
import pt.hexa.empresarial.ui.Glyph
import pt.hexa.empresarial.ui.GlyphButton
import pt.hexa.empresarial.widgets.WidgetPickerDialog
import kotlin.math.roundToInt

/** What is being dragged and where (root coordinates). */
private data class DragInfo(
    val item: HomeItem?,
    val dockSlot: Int?,
    val appKey: String?,
    val position: Offset,
    val grab: Offset,
    val widthPx: Float,
    val heightPx: Float,
)

/** Geometry of the visible page grid, used to turn a drop position into a cell. */
private class GridGeometry {
    var origin = Offset.Zero
    var cellW = 1f
    var cellH = 1f
    var bounds = Rect.Zero
}

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsState()
    HexaTheme(state.appearance, state.animations) {
        if (!ready || !state.initialized) {
            // First run: the home screen is being filled with the device's usual apps.
            Box(Modifier.fillMaxSize().background(HexaTheme.colors.background), contentAlignment = Alignment.Center) { HexaLogo() }
        } else {
            HomeContent(vm, state)
        }
    }
}

@Composable
private fun HomeContent(vm: HomeViewModel, state: LauncherState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val tokens = HexaTheme.tokens
    val pages = state.home.pages
    val pager = rememberPagerState(initialPage = state.home.defaultPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val drawer = remember { Animatable(0f) }
    var drawerFocusSearch by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    val grid = remember { GridGeometry() }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var removeBounds by remember { mutableStateOf(Rect.Zero) }
    var dockPickerSlot by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val stateRef by rememberUpdatedState(state)

    LaunchedEffect(pager.currentPage) { vm.currentPage = pager.currentPage }
    LaunchedEffect(vm.pageRequest) {
        vm.pageRequest?.let { (page, _) -> if (page in 0 until pager.pageCount) pager.animateScrollToPage(page) }
    }
    LaunchedEffect(vm.drawerRequest) {
        val r = vm.drawerRequest ?: return@LaunchedEffect
        drawerFocusSearch = r.focusSearch
        drawer.animateTo(1f, tween(tokens.durationMedium))
    }
    LaunchedEffect(vm.drawerCloseRequest) {
        if (vm.drawerCloseRequest != 0L) {
            drawer.animateTo(0f, tween(tokens.durationShort))
            vm.clearQuery()
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { m -> snackbar.showSnackbar(if (m.arg != null) context.getString(m.text, m.arg) else context.getString(m.text)) }
    }
    SideEffect { (context as? HomeActivity)?.drawerVisible = drawer.value > 0f }

    fun closeDrawer() {
        scope.launch {
            drawer.animateTo(0f, tween(tokens.durationShort))
            vm.clearQuery()
        }
    }

    BackHandler {
        when {
            vm.menu != null -> vm.closeMenu()
            vm.groupEditor != null -> vm.closeGroupEditor()
            vm.openGroup != null -> vm.closeGroup()
            drawer.value > 0f -> closeDrawer()
            vm.editMode -> vm.exitEdit()
            else -> Unit // a launcher never closes on Back
        }
    }

    // ---- drag & drop ----------------------------------------------------------------------------

    val edgeSide by remember {
        derivedStateOf {
            val d = drag ?: return@derivedStateOf 0
            val edge = with(density) { 28.dp.toPx() }
            when {
                d.position.x < grid.bounds.left + edge -> -1
                d.position.x > grid.bounds.right - edge -> 1
                else -> 0
            }
        }
    }
    LaunchedEffect(edgeSide) {
        while (edgeSide != 0) {
            delay(550)
            val target = pager.currentPage + edgeSide
            if (target in 0 until pager.pageCount) pager.animateScrollToPage(target) else break
        }
    }

    fun drop() {
        val d = drag ?: return
        drag = null
        val p = d.position
        when {
            removeBounds.contains(p) -> {
                if (d.item != null) vm.removeItem(d.item.id) else if (d.dockSlot != null) vm.setDockSlot(d.dockSlot, null)
            }
            dockBounds.contains(p) && d.appKey != null && state.dock.enabled -> {
                val count = state.dock.slots.size + if (state.dock.showAllAppsButton) 1 else 0
                val slot = ((p.x - dockBounds.left) / (dockBounds.width / count)).toInt().coerceIn(0, state.dock.slots.lastIndex)
                vm.dropOnDock(slot, d.appKey, d.item?.id, d.dockSlot)
            }
            grid.bounds.contains(p) -> {
                val tl = p - d.grab - grid.origin
                val x = (tl.x / grid.cellW).roundToInt()
                val y = (tl.y / grid.cellH).roundToInt()
                if (d.item != null) vm.moveItem(d.item.id, pager.currentPage, x, y) else if (d.dockSlot != null) vm.dropDockOnHome(d.dockSlot, pager.currentPage, x, y)
            }
        }
    }

    // ---- swipe up / down ------------------------------------------------------------------------

    var swipeTotal by remember { mutableStateOf(0f) }
    var swipeDrivesDrawer by remember { mutableStateOf(false) }
    val drawerActions = setOf(GestureAction.OPEN_DRAWER, GestureAction.OPEN_ORBITAL, GestureAction.OPEN_GRID)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        Wallpaper(state.appearance)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInputVertical(
                    enabled = drag == null && !vm.editMode,
                    onStart = {
                        swipeTotal = 0f
                        val up = stateRef.gestures.swipeUp
                        swipeDrivesDrawer = up.action in drawerActions
                        if (swipeDrivesDrawer) {
                            if (up.action == GestureAction.OPEN_ORBITAL) vm.setDrawerView(DrawerView.ORBITAL)
                            if (up.action == GestureAction.OPEN_GRID) vm.setDrawerView(DrawerView.GRID)
                            drawerFocusSearch = false
                        }
                    },
                    onDelta = { dy ->
                        swipeTotal += dy
                        if (swipeDrivesDrawer && swipeTotal < 0f) scope.launch { drawer.snapTo((-swipeTotal / (heightPx * 0.45f)).coerceIn(0f, 1f)) }
                    },
                    onEnd = {
                        val threshold = with(density) { 64.dp.toPx() }
                        when {
                            swipeTotal < 0f && swipeDrivesDrawer -> scope.launch {
                                if (drawer.value > 0.2f) drawer.animateTo(1f, tween(tokens.durationShort)) else drawer.animateTo(0f, tween(tokens.durationShort))
                            }
                            swipeTotal < -threshold -> vm.perform(stateRef.gestures.swipeUp)
                            swipeTotal > threshold -> vm.perform(stateRef.gestures.swipeDown)
                        }
                    },
                ),
        ) {
            if (state.appearance.logo == LogoPlacement.HOME_TOP) HexaLogo(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), size = 22.sp)
            if (vm.editMode) EditBar(vm, pager.currentPage, pages.size, state.home.defaultPage)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize(), key = { pages.getOrNull(it)?.id ?: it }) { index ->
                    val page = pages.getOrNull(index) ?: return@HorizontalPager
                    PageContent(
                        vm = vm,
                        state = state,
                        page = page,
                        pageIndex = index,
                        isCurrent = index == pager.currentPage,
                        catalog = catalog,
                        grid = grid,
                        drag = drag,
                        onDragStart = { item, key, root, grab, w, h ->
                            vm.closeMenu()
                            drag = DragInfo(item, null, key, root, grab, w, h)
                        },
                        onDrag = { root -> drag = drag?.copy(position = root) },
                        onDragEnd = { drop() },
                    )
                }
            }
            PageIndicator(pages.size, pager.currentPage, state.home.defaultPage)
            if (state.dock.enabled) {
                Dock(
                    dock = state.dock,
                    icons = state.icons,
                    catalog = catalog,
                    editMode = vm.editMode,
                    draggingSlot = drag?.dockSlot,
                    modifier = Modifier.onGloballyPositioned { dockBounds = it.boundsInRoot() },
                    actions = DockActions(
                        onTap = { _, key, bounds -> vm.launch(key, bounds) },
                        onLongPress = { slot, key, bounds -> vm.showAppMenu(key, MenuSource.DOCK, bounds, dockSlot = slot) },
                        onDragStart = { slot, key, root, grab ->
                            vm.closeMenu()
                            val s = with(density) { state.dock.iconSizeDp.dp.toPx() }
                            drag = DragInfo(null, slot, key, root, grab, s, s)
                        },
                        onDrag = { root -> drag = drag?.copy(position = root) },
                        onDragEnd = { drop() },
                        onEmptySlot = { dockPickerSlot = it },
                        onAllApps = { vm.openDrawer(DrawerView.GRID, false) },
                    ),
                )
            }
        }

        // Dragged element and the remove target.
        drag?.let { d ->
            RemoveZone(
                active = removeBounds.contains(d.position),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .onGloballyPositioned { removeBounds = it.boundsInRoot() },
            )
            val key = d.appKey
            Box(
                Modifier
                    .offset { IntOffset((d.position.x - d.grab.x).roundToInt(), (d.position.y - d.grab.y).roundToInt()) }
                    .size(with(density) { d.widthPx.toDp() }, with(density) { d.heightPx.toDp() })
                    .scale(1.08f)
                    .alpha(0.92f),
                contentAlignment = Alignment.Center,
            ) {
                if (key != null) AppIcon(key, state.icons.sizeDp.dp, state.icons) else Box(Modifier.fillMaxSize().border(2.dp, HexaTheme.colors.accent, RoundedCornerShape(tokens.radiusMedium)))
            }
        }

        if (drawer.value > 0f) {
            DrawerOverlay(
                vm = vm,
                state = state,
                catalog = catalog,
                progress = drawer.value,
                focusSearch = drawerFocusSearch,
                onDragDelta = { dy -> scope.launch { drawer.snapTo((drawer.value - dy / (heightPx * 0.6f)).coerceIn(0f, 1f)) } },
                onDragEnd = { scope.launch { if (drawer.value < 0.75f) drawer.animateTo(0f, tween(tokens.durationShort)).also { vm.clearQuery() } else drawer.animateTo(1f, tween(tokens.durationShort)) } },
                onClose = { closeDrawer() },
            )
        }

        vm.openGroup?.let { og ->
            state.group(og.groupId)?.let { g -> GroupOverlay(vm, g, og.origin, state.icons) }
        }

        vm.menu?.let { target ->
            when (target) {
                is MenuTarget.App -> AppMenu(vm, target, state, catalog)
                is MenuTarget.Item -> {
                    val (title, entries) = itemMenuEntries(vm, target, density.density)
                    ContextMenuOverlay(target, entries, title, vm::closeMenu)
                }
            }
        }

        vm.groupEditor?.let { id ->
            GroupEditorDialog(
                existing = if (id == HomeViewModel.NEW_GROUP) null else state.group(id),
                icons = state.icons,
                onSave = vm::saveGroup,
                onDelete = vm::deleteGroup,
                onDismiss = vm::closeGroupEditor,
            )
        }
        vm.widgetPickerPage?.let { page -> WidgetPickerDialog(vm, page) }
        dockPickerSlot?.let { slot ->
            AppPickerDialog(
                title = stringResource(R.string.dock_choose_app),
                icons = state.icons,
                onDismiss = { dockPickerSlot = null },
                onPick = { apps -> apps.firstOrNull()?.let { vm.setDockSlot(slot, it.key) }; dockPickerSlot = null },
            )
        }
        if (vm.accessibilityPrompt) {
            AlertDialog(
                onDismissRequest = vm::dismissAccessibilityPrompt,
                title = { Text(stringResource(R.string.accessibility_needed_title)) },
                text = { Text(stringResource(R.string.accessibility_needed_text)) },
                confirmButton = { TextButton(onClick = vm::openAccessibilitySettings) { Text(stringResource(R.string.action_open_settings)) } },
                dismissButton = { TextButton(onClick = vm::dismissAccessibilityPrompt) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }
}

/** Vertical swipe detection that leaves horizontal swipes to the pager. */
private fun Modifier.pointerInputVertical(enabled: Boolean, onStart: () -> Unit, onDelta: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        this.then(
            Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onStart() },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        onDelta(dy)
                    },
                    onDragEnd = onEnd,
                    onDragCancel = onEnd,
                )
            },
        )
    }

@Composable
private fun PageContent(
    vm: HomeViewModel,
    state: LauncherState,
    page: HomePage,
    pageIndex: Int,
    isCurrent: Boolean,
    catalog: CatalogSnapshot,
    grid: GridGeometry,
    drag: DragInfo?,
    onDragStart: (HomeItem, String?, Offset, Offset, Float, Float) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = HexaTheme.colors
    val home = state.home
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .onGloballyPositioned { c ->
                if (isCurrent) {
                    grid.origin = c.positionInRoot()
                    grid.bounds = c.boundsInRoot()
                }
            }
            .pointerInput(vm.editMode) {
                detectTapGestures(
                    onDoubleTap = { if (!vm.editMode) vm.perform(state.gestures.doubleTap) },
                    onLongPress = { vm.enterEdit() },
                    onTap = { if (vm.editMode) vm.selectWidget(null) },
                )
            },
    ) {
        val cellW = constraints.maxWidth.toFloat() / home.columns
        val cellH = constraints.maxHeight.toFloat() / home.rows
        if (isCurrent) {
            grid.cellW = cellW
            grid.cellH = cellH
            vm.cellSizeDp = with(density) { cellW.toDp().value to cellH.toDp().value }
        }
        if (vm.editMode) {
            val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
            Canvas(Modifier.fillMaxSize()) {
                for (x in 0 until home.columns) for (y in 0 until home.rows) {
                    drawRoundRect(
                        colors.onWallpaper.copy(alpha = 0.18f),
                        topLeft = Offset(x * cellW + 4f, y * cellH + 4f),
                        size = androidx.compose.ui.geometry.Size(cellW - 8f, cellH - 8f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f),
                        style = Stroke(1.5f, pathEffect = dash),
                    )
                }
            }
        }
        for (item in page.items) {
            val c = item.cell
            val dragged = drag?.item?.id == item.id
            Box(
                Modifier
                    .offset { IntOffset((c.x * cellW).roundToInt(), (c.y * cellH).roundToInt()) }
                    .size(with(density) { (c.w * cellW).toDp() }, with(density) { (c.h * cellH).toDp() })
                    .alpha(if (dragged) 0.3f else 1f),
            ) {
                val gestures = Modifier.itemGestures(
                    key = item.id,
                    onTap = { bounds ->
                        when (item) {
                            is AppItem -> if (vm.editMode) vm.showAppMenu(item.appKey, MenuSource.HOME, bounds, item.id) else vm.launch(item.appKey, bounds)
                            is GroupItem -> vm.openGroup(item.groupId, bounds)
                            is ClockItem -> (context as? HomeActivity)?.startIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            is SearchBarItem -> vm.openDrawer(null, true)
                            is WidgetItem -> vm.selectWidget(item.id)
                        }
                    },
                    onLongPress = { bounds ->
                        if (item is AppItem) vm.showAppMenu(item.appKey, MenuSource.HOME, bounds, item.id) else vm.showItemMenu(item.id, bounds)
                    },
                    onDragStart = { root, grab -> onDragStart(item, (item as? AppItem)?.appKey, root, grab, c.w * cellW, c.h * cellH) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
                when (item) {
                    is AppItem -> AppCell(item.appKey, catalog.label(item.appKey) ?: item.label, state.icons, missing = catalog.loaded && catalog.resolve(item.appKey) == null, modifier = gestures)
                    is GroupItem -> state.group(item.groupId)?.let { GroupCell(it, state.icons, gestures) }
                    is ClockItem -> ClockCell(state.clock, state.appearance.surfaceOpacity, gestures)
                    is SearchBarItem -> SearchBarCell(state.appearance.surfaceOpacity, onClick = { vm.openDrawer(null, true) }, modifier = gestures)
                    is WidgetItem -> {
                        val wDp = with(density) { (c.w * cellW).toDp().value }
                        val hDp = with(density) { (c.h * cellH).toDp().value }
                        WidgetCell(item, wDp, hDp, onRestore = { vm.restoreWidget(item, density.density) })
                        // In edit mode a transparent layer takes the touches (move, select, resize).
                        if (vm.editMode) Box(Modifier.fillMaxSize().then(gestures))
                        if (vm.editMode && vm.selectedWidget == item.id) {
                            val info = vm.c.widgets.providerInfo(item.appWidgetId)
                            val (minW, minH) = info?.let { vm.c.widgets.minSpan(it, vm.cellSizeDp.first, vm.cellSizeDp.second, density.density) } ?: (1 to 1)
                            ResizeFrame(
                                onResize = { dw, dh ->
                                    vm.resizeWidget(item.id, (c.w + dw).coerceAtLeast(minW.coerceAtMost(home.columns)), (c.h + dh).coerceAtLeast(minH.coerceAtMost(home.rows)))
                                },
                                cellWidthPx = cellW,
                                cellHeightPx = cellH,
                                canW = info == null || vm.c.widgets.canResize(info, true),
                                canH = info == null || vm.c.widgets.canResize(info, false),
                            )
                        }
                    }
                }
            }
        }
        // Drop preview on the current page.
        if (isCurrent && drag != null && grid.bounds.contains(drag.position)) {
            val tl = drag.position - drag.grab - grid.origin
            val w = (drag.item?.cell?.w ?: 1)
            val h = (drag.item?.cell?.h ?: 1)
            val x = (tl.x / cellW).roundToInt().coerceIn(0, (home.columns - w).coerceAtLeast(0))
            val y = (tl.y / cellH).roundToInt().coerceIn(0, (home.rows - h).coerceAtLeast(0))
            Box(
                Modifier
                    .offset { IntOffset((x * cellW).roundToInt(), (y * cellH).roundToInt()) }
                    .size(with(density) { (w * cellW).toDp() }, with(density) { (h * cellH).toDp() })
                    .padding(4.dp)
                    .border(2.dp, colors.accent.copy(alpha = 0.8f), RoundedCornerShape(HexaTheme.tokens.radiusMedium)),
            )
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, defaultPage: Int) {
    if (count <= 1) return
    val colors = HexaTheme.colors
    val description = stringResource(R.string.a11y_page_indicator, current + 1, count)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .background(
                        if (i == current) colors.accent else Color.White.copy(alpha = if (i == defaultPage) 0.7f else 0.4f),
                        CircleShape,
                    ),
            )
        }
    }
}

/** Edit mode bar: what can be added and how the current page is managed. */
@Composable
private fun EditBar(vm: HomeViewModel, page: Int, pageCount: Int, defaultPage: Int) {
    val colors = HexaTheme.colors
    val shape = RoundedCornerShape(HexaTheme.tokens.radiusLarge)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Color.Black.copy(alpha = 0.72f), shape)
            .border(1.dp, colors.accent.copy(alpha = 0.6f), shape)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.edit_mode_title), color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = vm::exitEdit) { Text(stringResource(R.string.action_done), color = colors.accent) }
        }
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            GlyphButton(Glyph.WIDGET, stringResource(R.string.action_add_widget), vm::openWidgetPicker, tint = Color.White)
            GlyphButton(Glyph.GROUP, stringResource(R.string.action_new_group), { vm.editGroup(null) }, tint = Color.White)
            GlyphButton(Glyph.PAGE, stringResource(R.string.action_add_page), vm::addPage, tint = Color.White)
            GlyphButton(Glyph.SETTINGS, stringResource(R.string.settings_title), { vm.host?.openSettings() }, tint = Color.White)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.movePage(page, page - 1) }, enabled = page > 0) { Text(stringResource(R.string.page_move_left), color = Color.White) }
            TextButton(onClick = { vm.setDefaultPage(page) }, enabled = page != defaultPage) {
                Text(stringResource(if (page == defaultPage) R.string.page_is_default else R.string.page_make_default), color = Color.White)
            }
            TextButton(onClick = { vm.removePage(page) }, enabled = pageCount > 1) { Text(stringResource(R.string.page_delete), color = colors.danger) }
            TextButton(onClick = { vm.movePage(page, page + 1) }, enabled = page < pageCount - 1) { Text(stringResource(R.string.page_move_right), color = Color.White) }
        }
    }
}

/** Context menu for an app (home, dock, drawers, groups). */
@Composable
private fun AppMenu(vm: HomeViewModel, target: MenuTarget.App, state: LauncherState, catalog: CatalogSnapshot) {
    val key = target.appKey
    val label = catalog.label(key) ?: ""
    var groupPicker by remember { mutableStateOf(false) }
    val entries = buildList {
        target.shortcuts.forEach { s -> add(MenuEntry(s.label) { vm.launchShortcut(s, target.anchor) }) }
        add(MenuEntry(stringResource(R.string.action_app_info)) { vm.appInfo(key) })
        if (target.source != MenuSource.HOME) add(MenuEntry(stringResource(R.string.action_add_to_home)) { vm.addAppToHome(key, label) })
        if (target.source != MenuSource.DOCK) add(MenuEntry(stringResource(R.string.action_add_to_dock)) { vm.addToDock(key) })
        if (state.groups.isNotEmpty()) add(MenuEntry(stringResource(R.string.action_add_to_group)) { groupPicker = true })
        val fav = key in state.apps.favorites
        add(MenuEntry(stringResource(if (fav) R.string.action_unfavorite else R.string.action_favorite)) { vm.toggleFavorite(key) })
        if (target.source == MenuSource.ORBITAL) {
            add(MenuEntry(stringResource(R.string.action_orbit_closer)) { vm.nudgeOrbital(key, -1, vm.orbitalOrder) })
            add(MenuEntry(stringResource(R.string.action_orbit_further)) { vm.nudgeOrbital(key, 1, vm.orbitalOrder) })
        }
        if (target.source == MenuSource.DRAWER || target.source == MenuSource.ORBITAL) add(MenuEntry(stringResource(R.string.action_hide_app)) { vm.hideApp(key) })
        if (target.source == MenuSource.HOME && target.itemId != null) add(MenuEntry(stringResource(R.string.action_remove), danger = true) { vm.removeItem(target.itemId) })
        if (target.source == MenuSource.DOCK && target.dockSlot != null) add(MenuEntry(stringResource(R.string.action_remove_from_dock), danger = true) { vm.closeMenu(); vm.setDockSlot(target.dockSlot, null) })
        add(MenuEntry(stringResource(R.string.action_uninstall), danger = true) { vm.uninstall(key) })
    }
    if (!groupPicker) {
        ContextMenuOverlay(target, entries, label, vm::closeMenu)
    } else {
        ContextMenuOverlay(
            target,
            state.groups.map { g -> MenuEntry(g.name) { vm.addAppToGroup(g.id, key) } },
            androidx.compose.ui.res.stringResource(R.string.action_add_to_group),
        ) { groupPicker = false; vm.closeMenu() }
    }
}
