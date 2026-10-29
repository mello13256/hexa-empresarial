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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
        vm.messages.collect { m ->
            val text = if (m.arg != null) context.getString(m.text, m.arg) else context.getString(m.text)
            // A newer message replaces the one on screen instead of waiting behind it.
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val token = m.undoToken
                if (token == null) {
                    snackbar.showSnackbar(text)
                } else {
                    val result = snackbar.showSnackbar(text, actionLabel = context.getString(R.string.action_undo), duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) vm.undo(token) else vm.undoExpired(token)
                }
            }
        }
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
            // While dragging, the remove target takes the place of the page bar (kept for its height).
            if (vm.editMode) EditPageBar(vm, pager.currentPage, pages.size, state.home.defaultPage, Modifier.alpha(if (drag != null) 0f else 1f))
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
            if (vm.editMode) EditTools(vm) else PageIndicator(pages.size, pager.currentPage, state.home.defaultPage)
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
                    // In edit mode a tap on empty space first deselects a widget, then ends editing.
                    onTap = { if (vm.editMode) { if (vm.selectedWidget != null) vm.selectWidget(null) else vm.exitEdit() } },
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
        // The cell grid only appears while something is being dragged, to show where it can go.
        if (drag != null) {
            val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
            Canvas(Modifier.fillMaxSize()) {
                for (x in 0 until home.columns) for (y in 0 until home.rows) {
                    drawRoundRect(
                        colors.onWallpaper.copy(alpha = 0.14f),
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
                        // In edit mode a tap never opens anything by accident: it only edits groups
                        // and selects widgets for resizing.
                        if (vm.editMode) {
                            when (item) {
                                is GroupItem -> vm.editGroup(item.groupId)
                                is WidgetItem -> vm.selectWidget(item.id)
                                else -> Unit
                            }
                        } else {
                            when (item) {
                                is AppItem -> vm.launch(item.appKey, bounds)
                                is GroupItem -> vm.openGroup(item.groupId, bounds)
                                is ClockItem -> (context as? HomeActivity)?.startIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                is SearchBarItem -> vm.openDrawer(null, true)
                                is WidgetItem -> Unit
                            }
                        }
                    },
                    onLongPress = { bounds ->
                        if (item is AppItem) vm.showAppMenu(item.appKey, MenuSource.HOME, bounds, item.id) else vm.showItemMenu(item.id, bounds)
                    },
                    onDragStart = { root, grab -> onDragStart(item, (item as? AppItem)?.appKey, root, grab, c.w * cellW, c.h * cellH) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
                val badge: (@Composable () -> Unit)? = if (vm.editMode && !dragged) {
                    { RemoveBadge(itemLabel(item, state, catalog), { vm.removeItem(item.id) }) }
                } else {
                    null
                }
                when (item) {
                    is AppItem -> AppCell(
                        item.appKey, catalog.label(item.appKey) ?: item.label, state.icons,
                        missing = catalog.loaded && catalog.resolve(item.appKey) == null, modifier = gestures, badge = badge,
                    )
                    is GroupItem -> state.group(item.groupId)?.let { GroupCell(it, state.icons, gestures, badge) }
                    is ClockItem -> ClockCell(state.clock, state.appearance.surfaceOpacity, gestures)
                    is SearchBarItem -> SearchBarCell(state.appearance.surfaceOpacity, onClick = { if (!vm.editMode) vm.openDrawer(null, true) }, modifier = gestures)
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
                // Apps and groups carry the badge on their icon; larger elements on their corner.
                if (badge != null && item !is AppItem && item !is GroupItem) Box(Modifier.align(Alignment.TopStart)) { badge() }
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

/** Name of a home element, for accessibility labels. */
@Composable
private fun itemLabel(item: HomeItem, state: LauncherState, catalog: CatalogSnapshot): String = when (item) {
    is AppItem -> catalog.label(item.appKey) ?: item.label
    is GroupItem -> state.group(item.groupId)?.name ?: stringResource(R.string.group_default_name)
    is ClockItem -> stringResource(R.string.item_clock)
    is SearchBarItem -> stringResource(R.string.item_search_bar)
    is WidgetItem -> item.label.ifEmpty { stringResource(R.string.widget_generic) }
}

/** Context menu for an app (home, dock, drawers, groups): only what makes sense where it was opened. */
@Composable
private fun AppMenu(vm: HomeViewModel, target: MenuTarget.App, state: LauncherState, catalog: CatalogSnapshot) {
    val key = target.appKey
    val label = catalog.label(key) ?: ""
    val source = target.source
    val inDrawer = source == MenuSource.DRAWER || source == MenuSource.ORBITAL
    var groupPicker by remember { mutableStateOf(false) }
    val entries = buildList {
        target.shortcuts.take(MAX_MENU_SHORTCUTS).forEach { s -> add(MenuEntry(s.label) { vm.launchShortcut(s, target.anchor) }) }
        if (source != MenuSource.HOME) add(MenuEntry(stringResource(R.string.action_add_to_home), glyph = Glyph.HOME) { vm.addAppToHome(key, label) })
        if (source != MenuSource.DOCK) add(MenuEntry(stringResource(R.string.action_add_to_dock), glyph = Glyph.DOCK) { vm.addToDock(key) })
        if (state.groups.isNotEmpty() && source != MenuSource.GROUP) add(MenuEntry(stringResource(R.string.action_add_to_group), glyph = Glyph.GROUP) { groupPicker = true })
        if (inDrawer) {
            val fav = key in state.apps.favorites
            add(MenuEntry(stringResource(if (fav) R.string.action_unfavorite else R.string.action_favorite), glyph = Glyph.STAR) { vm.toggleFavorite(key) })
        }
        if (source == MenuSource.ORBITAL) {
            add(MenuEntry(stringResource(R.string.action_orbit_closer), glyph = Glyph.ORBIT) { vm.nudgeOrbital(key, -1, vm.orbitalOrder) })
            add(MenuEntry(stringResource(R.string.action_orbit_further), glyph = Glyph.ORBIT) { vm.nudgeOrbital(key, 1, vm.orbitalOrder) })
        }
        if (inDrawer) add(MenuEntry(stringResource(R.string.action_hide_app), glyph = Glyph.HIDE) { vm.hideApp(key) })
        add(MenuEntry(stringResource(R.string.action_app_info), glyph = Glyph.INFO) { vm.appInfo(key) })
        if (source == MenuSource.HOME && target.itemId != null) add(MenuEntry(stringResource(R.string.action_remove), danger = true, glyph = Glyph.CLOSE) { vm.removeItem(target.itemId) })
        if (source == MenuSource.DOCK && target.dockSlot != null) add(MenuEntry(stringResource(R.string.action_remove_from_dock), danger = true, glyph = Glyph.CLOSE) { vm.closeMenu(); vm.setDockSlot(target.dockSlot, null) })
        add(MenuEntry(stringResource(R.string.action_uninstall), danger = true, glyph = Glyph.TRASH) { vm.uninstall(key) })
    }
    if (!groupPicker) {
        ContextMenuOverlay(target, entries, label, vm::closeMenu, icon = { AppIcon(key, 32.dp, state.icons) })
    } else {
        ContextMenuOverlay(
            target,
            state.groups.map { g -> MenuEntry(g.name, glyph = Glyph.GROUP) { vm.addAppToGroup(g.id, key) } },
            stringResource(R.string.action_add_to_group),
            { groupPicker = false; vm.closeMenu() },
        )
    }
}

/** Android shows at most four shortcuts in a launcher menu; more only make it longer. */
private const val MAX_MENU_SHORTCUTS = 4
